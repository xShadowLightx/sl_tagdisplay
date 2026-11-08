package io.github.shadowlight.tagdisplay.access;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Optional DiscordSRV integration, isolated behind reflection so DiscordSRV remains optional. */
public final class DiscordSrvRoleProvider implements DiscordRoleProvider {
    private final JavaPlugin plugin;
    private volatile Consumer<UUID> refreshCallback = ignored -> { };

    public DiscordSrvRoleProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin discordSrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
            if (discordSrv == null || !discordSrv.isEnabled()) {
                return false;
            }
            Object jda = invoke(discordSrv, "getJda");
            return jda != null && !guildId().isBlank()
                    && invoke(jda, "getGuildById", String.class, guildId()) != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public AccessResult hasAnyRole(UUID playerId, Set<String> roleIds) {
        if (roleIds.isEmpty() || !isAvailable()) {
            return AccessResult.DENIED;
        }
        try {
            Plugin discordSrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
            Object accountLinkManager = invoke(discordSrv, "getAccountLinkManager");
            Class<?> managerApi = Class.forName("github.scarsz.discordsrv.objects.managers.AccountLinkManager",
                    true, discordSrv.getClass().getClassLoader());
            String discordId = (String) managerApi.getMethod("getDiscordId", UUID.class).invoke(accountLinkManager, playerId);
            if (discordId == null || discordId.isBlank()) {
                return AccessResult.DENIED;
            }

            Object jda = invoke(discordSrv, "getJda");
            Object guild = invoke(jda, "getGuildById", String.class, guildId());
            Object member = invoke(guild, "getMemberById", String.class, discordId);
            if (member == null) {
                return AccessResult.PENDING;
            }

            Object roles = invoke(member, "getRoles");
            if (!(roles instanceof Collection<?> collection)) {
                return AccessResult.PENDING;
            }
            for (Object role : collection) {
                String roleId = (String) invoke(role, "getId");
                if (roleIds.contains(roleId)) {
                    return AccessResult.ALLOWED;
                }
            }
            return AccessResult.DENIED;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().fine("DiscordSRV role lookup is temporarily unavailable: " + exception.getMessage());
            return AccessResult.PENDING;
        }
    }

    @Override
    public void setRefreshCallback(Consumer<UUID> callback) {
        refreshCallback = callback;
    }

    private String guildId() {
        return plugin.getConfig().getString("access.discord.guild-id", "").trim();
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String methodName, Class<?> parameterType, Object value)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterType);
        return method.invoke(target, value);
    }
}
