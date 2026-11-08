package io.github.shadowlight.tagdisplay;

import io.github.shadowlight.tagdisplay.access.AccessMode;
import io.github.shadowlight.tagdisplay.access.DiscordSrvRoleProvider;
import io.github.shadowlight.tagdisplay.access.TagAccessService;
import io.github.shadowlight.tagdisplay.config.TagCatalog;
import io.github.shadowlight.tagdisplay.discord.BuiltInDiscordBot;
import io.github.shadowlight.tagdisplay.discord.DiscordLinkStore;
import io.github.shadowlight.tagdisplay.discord.LinkCodeManager;
import io.github.shadowlight.tagdisplay.display.PlayerTagListener;
import io.github.shadowlight.tagdisplay.display.TagDisplayModule;
import io.github.shadowlight.tagdisplay.menu.TagMenu;
import io.github.shadowlight.tagdisplay.model.TagType;
import io.github.shadowlight.tagdisplay.storage.PlayerSelectionStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SLTagDisplayPlugin extends JavaPlugin implements Listener {
    private final Map<TagType, TagCatalog> catalogs = new EnumMap<>(TagType.class);
    private final Map<TagType, TagDisplayModule> displayModules = new EnumMap<>(TagType.class);

    private PlayerSelectionStore selections;
    private DiscordLinkStore discordLinks;
    private LinkCodeManager linkCodes;
    private BuiltInDiscordBot builtInDiscord;
    private TagAccessService access;
    private TagMenu tagMenu;
    private boolean placeholdersRegistered;
    private GitHubUpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("player-selections.yml", false);
        saveResource("discord-links.yml", false);
        getServer().getPluginManager().registerEvents(this, this);

        selections = new PlayerSelectionStore(this);
        discordLinks = new DiscordLinkStore(this);
        try {
            selections.load();
            discordLinks.load();
        } catch (Exception exception) {
            getLogger().severe("Could not load player data: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long codeLifetime = getConfig().getLong("access.discord.link-code-lifetime-seconds", 300L);
        linkCodes = new LinkCodeManager(codeLifetime);
        DiscordSrvRoleProvider discordSrv = new DiscordSrvRoleProvider(this);
        builtInDiscord = new BuiltInDiscordBot(this, discordLinks, linkCodes);
        access = new TagAccessService(this, discordSrv, builtInDiscord);
        access.setRefreshCallback(this::schedulePlayerRefresh);

        for (TagType type : TagType.values()) {
            TagCatalog catalog = new TagCatalog(this, type);
            try {
                catalog.reload(getConfig());
            } catch (RuntimeException exception) {
                getLogger().severe("Could not load " + type.displayName().toLowerCase() + " definitions: " + exception.getMessage());
            }
            catalogs.put(type, catalog);
            displayModules.put(type, new TagDisplayModule(this, type, catalog, selections, access));
        }

        tagMenu = new TagMenu(this, catalogs, displayModules, selections, access);
        getServer().getPluginManager().registerEvents(tagMenu, this);
        getServer().getPluginManager().registerEvents(new PlayerTagListener(List.copyOf(displayModules.values())), this);
        registerCommands();
        if (!isEnabled()) {
            return;
        }

        access.start();
        for (TagDisplayModule module : displayModules.values()) {
            try {
                module.start();
            } catch (RuntimeException exception) {
                getLogger().severe("Could not start a tag display module: " + exception.getMessage());
            }
        }

        registerPlaceholderExpansionIfAvailable();
        logStartupBanner();
        updateChecker = new GitHubUpdateChecker(this);
        updateChecker.checkAsync();
    }

    @Override
    public void onDisable() {
        for (TagDisplayModule module : displayModules.values()) {
            try {
                module.stop();
            } catch (RuntimeException exception) {
                getLogger().warning("A tag display module did not stop cleanly: " + exception.getMessage());
            }
        }
        if (access != null) {
            access.stop();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
    }

    public boolean reloadPluginConfiguration() {
        try {
            reloadConfig();
        } catch (RuntimeException exception) {
            getLogger().severe("Configuration reload failed: " + exception.getMessage());
            return false;
        }

        if (updateChecker != null) {
            updateChecker.checkAsync();
        }

        long codeLifetime = getConfig().getLong("access.discord.link-code-lifetime-seconds", 300L);
        if (linkCodes != null) {
            linkCodes.setLifetimeSeconds(codeLifetime);
        }
        access.reloadMode(true);
        boolean loadedAny = false;
        for (Map.Entry<TagType, TagCatalog> entry : catalogs.entrySet()) {
            try {
                entry.getValue().reload(getConfig());
                loadedAny = true;
            } catch (RuntimeException exception) {
                getLogger().severe("Could not reload " + entry.getKey().displayName().toLowerCase()
                        + " definitions: " + exception.getMessage());
            }
        }
        for (TagDisplayModule module : displayModules.values()) {
            module.refreshAll();
        }
        return loadedAny;
    }

    public void openMenu(Player player, TagType type) {
        tagMenu.open(player, type, 0);
    }

    public void openMenu(Player player, TagType type, int page) {
        tagMenu.open(player, type, page);
    }

    public void beginDiscordLink(Player player) {
        if (access.mode() == AccessMode.DISCORDSRV) {
            player.sendMessage(Component.text("Link your accounts through DiscordSRV with /discord link."));
            return;
        }
        if (access.mode() != AccessMode.BUILTIN_DISCORD) {
            player.sendMessage(Component.text("Built-in Discord linking is disabled. Ask an admin to change access.mode."));
            return;
        }
        if (builtInDiscord == null || !builtInDiscord.isAvailable()) {
            player.sendMessage(Component.text("The built-in Discord bot is not connected right now."));
            return;
        }
        String code = linkCodes.issue(player.getUniqueId());
        if (code == null) {
            player.sendMessage(Component.text("Please wait 30 seconds before requesting another link code."));
            return;
        }
        player.sendMessage(Component.text("Your one-time Discord link code is: " + code));
        player.sendMessage(Component.text("In the configured Discord server, run /sltagdisplay link and enter this code. It expires in "
                + getConfig().getLong("access.discord.link-code-lifetime-seconds", 300L) + " seconds. Keep it private."));
    }

    public void unlinkDiscordAccount(Player player) {
        if (access.mode() == AccessMode.DISCORDSRV) {
            player.sendMessage(Component.text("Unlink this account through DiscordSRV with /discord unlink."));
            return;
        }
        if (access.mode() != AccessMode.BUILTIN_DISCORD) {
            player.sendMessage(Component.text("Built-in Discord linking is disabled."));
            return;
        }
        linkCodes.remove(player.getUniqueId());
        String oldDiscordId = discordLinks.unlink(player.getUniqueId());
        if (oldDiscordId == null) {
            player.sendMessage(Component.text("Your Minecraft account is not linked to Discord."));
        } else {
            player.sendMessage(Component.text("Your built-in Discord link was removed."));
            for (TagDisplayModule module : displayModules.values()) {
                module.refresh(player);
            }
        }
    }

    public void refreshPlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        for (TagDisplayModule module : displayModules.values()) {
            module.refresh(player);
        }
    }

    private void schedulePlayerRefresh(UUID playerId) {
        if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, () -> refreshPlayer(playerId));
        }
    }

    private void registerCommands() {
        PluginCommand headtag = getCommand("headtag");
        PluginCommand gangtag = getCommand("gangtag");
        PluginCommand tagdisplay = getCommand("tagdisplay");
        if (headtag == null || gangtag == null || tagdisplay == null) {
            getLogger().severe("A required command is missing from plugin.yml; disabling SL Tag Display.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        headtag.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used in game.");
                return true;
            }
            openMenu(player, TagType.HEADTAG);
            return true;
        });
        gangtag.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used in game.");
                return true;
            }
            openMenu(player, TagType.GANGTAG);
            return true;
        });
        tagdisplay.setExecutor(new TagDisplayCommand(this));
    }

    private void registerPlaceholderExpansionIfAvailable() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            Class<?> expansionClass = Class.forName(
                    "io.github.shadowlight.tagdisplay.placeholder.HeadtagPlaceholderExpansion", true, getClassLoader());
            Object expansion = expansionClass.getConstructor(SLTagDisplayPlugin.class).newInstance(this);
            placeholdersRegistered = (boolean) expansionClass.getMethod("register").invoke(expansion);
            if (placeholdersRegistered) {
                getLogger().info("Registered PlaceholderAPI placeholder %sltagdisplay_headtag%.");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().warning("PlaceholderAPI is installed but the headtag placeholder could not be registered: "
                    + exception.getMessage());
        }
    }

    public TagCatalog catalog(TagType type) {
        return catalogs.get(type);
    }

    public PlayerSelectionStore selections() {
        return selections;
    }

    public TagAccessService access() {
        return access;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateChecker != null) {
            updateChecker.notifyIfAvailable(event.getPlayer());
        }
    }

    private void logStartupBanner() {
        var console = getServer().getConsoleSender();
        try (var banner = getResource("startup-banner.txt")) {
            if (banner != null) {
                new String(banner.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                        .map(line -> line.equals("----------------------------------------") ? "§8" + line : line)
                        .forEach(console::sendMessage);
            }
        } catch (java.io.IOException exception) {
            getLogger().warning("Could not read startup banner: " + exception.getMessage());
        }
        console.sendMessage(" §b◆ SL Tag Display");
        console.sendMessage(" §7Player-selected headtags and gangtags");
        console.sendMessage(" §7Version: " + getDescription().getVersion());
        console.sendMessage(" §7Access: " + access.mode().configValue());
        console.sendMessage(" §7Integrations: " + integrationSummary());
        console.sendMessage("§8----------------------------------------");
        console.sendMessage("§b  ✔ SL Tag Display is ready");
        console.sendMessage("§7  Headtags: " + catalogs.get(TagType.HEADTAG).all().size()
                + " | Gangtags: " + catalogs.get(TagType.GANGTAG).all().size());
        console.sendMessage("§7  Commands: /headtag, /gangtag, /tagdisplay");
        console.sendMessage("§8----------------------------------------");
    }

    private String integrationSummary() {
        java.util.ArrayList<String> integrations = new java.util.ArrayList<>();
        for (String name : List.of("LuckPerms", "PlaceholderAPI", "DiscordSRV")) {
            var installed = getServer().getPluginManager().getPlugin(name);
            if (installed != null && installed.isEnabled()) integrations.add(name);
        }
        if (access.mode() == AccessMode.BUILTIN_DISCORD) integrations.add("Built-in Discord");
        return integrations.isEmpty() ? "None" : String.join(", ", integrations);
    }
}
