package io.github.shadowlight.tagdisplay.discord;

import io.github.shadowlight.tagdisplay.access.AccessResult;
import io.github.shadowlight.tagdisplay.access.DiscordRoleProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BuiltInDiscordBot extends ListenerAdapter implements DiscordRoleProvider {
    private static final long MEMBER_RETRY_MILLIS = 30_000L;

    private final JavaPlugin plugin;
    private final DiscordLinkStore links;
    private final LinkCodeManager linkCodes;
    private final Map<String, Set<String>> roleCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastMemberRequest = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Consumer<UUID> refreshCallback = ignored -> { };
    private volatile JDA jda;
    private volatile boolean ready;

    public BuiltInDiscordBot(JavaPlugin plugin, DiscordLinkStore links, LinkCodeManager linkCodes) {
        this.plugin = plugin;
        this.links = links;
        this.linkCodes = linkCodes;
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        String guildId = guildId();
        String tokenEnvironment = plugin.getConfig().getString("access.discord.token-environment-variable", "SLTAGDISPLAY_DISCORD_TOKEN").trim();
        String token = System.getenv(tokenEnvironment);
        if (!guildId.matches("[0-9]{17,20}") || token == null || token.isBlank()) {
            plugin.getLogger().warning("Built-in Discord access is enabled but not configured. Set access.discord.guild-id and the "
                    + tokenEnvironment + " environment variable. Tag access will be denied unless permission fallback is enabled.");
            started.set(false);
            return;
        }

        try {
            jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(this)
                    .build();
            plugin.getLogger().info("Connecting the built-in Discord role integration.");
        } catch (RuntimeException exception) {
            started.set(false);
            plugin.getLogger().severe("Could not start the built-in Discord integration: " + exception.getMessage());
        }
    }

    @Override
    public void stop() {
        ready = false;
        started.set(false);
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdownNow();
        }
        roleCache.clear();
        lastMemberRequest.clear();
    }

    @Override
    public boolean isAvailable() {
        return ready && jda != null && jda.getGuildById(guildId()) != null;
    }

    @Override
    public AccessResult hasAnyRole(UUID playerId, Set<String> roleIds) {
        if (roleIds.isEmpty() || !isAvailable()) {
            return AccessResult.DENIED;
        }
        String discordId = links.discordId(playerId);
        if (discordId == null) {
            return AccessResult.DENIED;
        }
        Set<String> memberRoles = roleCache.get(discordId);
        if (memberRoles == null) {
            requestMember(discordId);
            return AccessResult.PENDING;
        }
        return memberRoles.stream().anyMatch(roleIds::contains) ? AccessResult.ALLOWED : AccessResult.DENIED;
    }

    @Override
    public void setRefreshCallback(Consumer<UUID> callback) {
        refreshCallback = callback;
    }

    @Override
    public void onReady(ReadyEvent event) {
        Guild guild = event.getJDA().getGuildById(guildId());
        if (guild == null) {
            plugin.getLogger().severe("The built-in Discord bot is not in the configured guild. Role access will remain unavailable.");
            return;
        }
        ready = true;
        guild.updateCommands().addCommands(
                Commands.slash("sltagdisplay", "Link your Minecraft account to choose Discord role tags")
                        .addSubcommands(new SubcommandData("link", "Link your Minecraft account using a one-time code")
                                .addOption(OptionType.STRING, "code", "The one-time code shown in Minecraft", true))
        ).queue(
                ignored -> plugin.getLogger().info("Registered the /sltagdisplay link command in the configured Discord server."),
                error -> plugin.getLogger().warning("Could not register the built-in Discord link command: " + error.getMessage())
        );

        for (String discordId : new HashSet<>(links.allLinks().values())) {
            requestMember(discordId);
        }
        plugin.getLogger().info("Built-in Discord role access is ready.");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("sltagdisplay") || !"link".equals(event.getSubcommandName())) {
            return;
        }
        if (event.getGuild() == null || !event.getGuild().getId().equals(guildId())) {
            event.reply("Use this command in the configured Minecraft server's Discord guild.").setEphemeral(true).queue();
            return;
        }
        String code = event.getOption("code") == null ? "" : event.getOption("code").getAsString();
        UUID playerId = linkCodes.consume(code);
        if (playerId == null) {
            event.reply("That link code is invalid or expired. Request a fresh code in Minecraft with `/tagdisplay link`.")
                    .setEphemeral(true).queue();
            return;
        }

        DiscordLinkStore.LinkResult linkResult = links.link(playerId, event.getUser().getId());
        if (linkResult == DiscordLinkStore.LinkResult.ALREADY_LINKED_TO_ANOTHER_PLAYER) {
            event.reply("This Discord account is already linked to another Minecraft account. Unlink it there before linking this account.")
                    .setEphemeral(true).queue();
            return;
        }

        updateRoles(event.getMember());
        event.reply("Your Discord account is now linked to Minecraft. You can close this message.")
                .setEphemeral(true).queue();
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        if (event.getGuild().getId().equals(guildId())) {
            updateRoles(event.getMember());
        }
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (event.getGuild().getId().equals(guildId())) {
            updateRoles(event.getMember());
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getGuild().getId().equals(guildId())) {
            updateRoles(event.getMember());
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        if (!event.getGuild().getId().equals(guildId())) {
            return;
        }
        String discordId = event.getUser().getId();
        roleCache.remove(discordId);
        UUID playerId = links.playerId(discordId);
        if (playerId != null) {
            scheduleRefresh(playerId);
        }
    }

    private void requestMember(String discordId) {
        JDA current = jda;
        if (!isAvailable() || current == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previousRequest = lastMemberRequest.putIfAbsent(discordId, now);
        if (previousRequest != null && now - previousRequest < MEMBER_RETRY_MILLIS) {
            return;
        }
        if (previousRequest != null) {
            lastMemberRequest.put(discordId, now);
        }

        Guild guild = current.getGuildById(guildId());
        if (guild == null) {
            return;
        }
        guild.retrieveMemberById(discordId).queue(
                this::updateRoles,
                error -> plugin.getLogger().fine("Could not retrieve a linked Discord member: " + error.getMessage())
        );
    }

    private void updateRoles(Member member) {
        if (member == null || !member.getGuild().getId().equals(guildId())) {
            return;
        }
        Set<String> roleIds = new HashSet<>();
        for (Role role : member.getRoles()) {
            roleIds.add(role.getId());
        }
        roleCache.put(member.getId(), Set.copyOf(roleIds));
        UUID playerId = links.playerId(member.getId());
        if (playerId != null) {
            scheduleRefresh(playerId);
        }
    }

    private void scheduleRefresh(UUID playerId) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshCallback.accept(playerId));
    }

    private String guildId() {
        return plugin.getConfig().getString("access.discord.guild-id", "").trim();
    }
}
