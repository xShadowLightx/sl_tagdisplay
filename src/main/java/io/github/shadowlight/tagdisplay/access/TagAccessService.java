package io.github.shadowlight.tagdisplay.access;

import io.github.shadowlight.tagdisplay.model.TagDefinition;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class TagAccessService {
    private final JavaPlugin plugin;
    private final DiscordRoleProvider discordSrv;
    private final DiscordRoleProvider builtInDiscord;
    private AccessMode mode;

    public TagAccessService(JavaPlugin plugin, DiscordRoleProvider discordSrv, DiscordRoleProvider builtInDiscord) {
        this.plugin = plugin;
        this.discordSrv = discordSrv;
        this.builtInDiscord = builtInDiscord;
        reloadMode();
    }

    public void reloadMode() {
        reloadMode(false);
    }

    public void reloadMode(boolean restartSelectedProvider) {
        String configured = plugin.getConfig().getString("access.mode", "permissions");
        AccessMode previousMode = mode;
        AccessMode nextMode = AccessMode.parse(configured);
        if (configured != null && !nextMode.configValue().equalsIgnoreCase(configured.trim())) {
            plugin.getLogger().warning("Unknown access.mode '" + configured + "'; using 'permissions'.");
        }
        if (previousMode == AccessMode.BUILTIN_DISCORD
                && (restartSelectedProvider || nextMode != AccessMode.BUILTIN_DISCORD)) {
            builtInDiscord.stop();
        }
        mode = nextMode;
        if (mode == AccessMode.BUILTIN_DISCORD
                && (restartSelectedProvider || previousMode != AccessMode.BUILTIN_DISCORD)) {
            builtInDiscord.start();
        }
    }

    public AccessMode mode() {
        return mode;
    }

    public AccessResult check(Player player, TagDefinition tag) {
        // Explicit server permissions always grant access, including when a
        // Discord access mode is selected. Discord roles can grant access too.
        if (hasPermission(player, tag)) {
            return AccessResult.ALLOWED;
        }
        if (mode == AccessMode.PERMISSIONS) {
            return AccessResult.DENIED;
        }

        DiscordRoleProvider provider = mode == AccessMode.DISCORDSRV ? discordSrv : builtInDiscord;
        if (provider == null || !provider.isAvailable()) {
            if (plugin.getConfig().getBoolean("access.fallback-to-permissions", false)) {
                return hasPermission(player, tag) ? AccessResult.ALLOWED : AccessResult.DENIED;
            }
            return AccessResult.DENIED;
        }

        AccessResult roleResult = provider.hasAnyRole(player.getUniqueId(), tag.discordRoleIds());
        if (roleResult == AccessResult.PENDING
                && plugin.getConfig().getBoolean("access.fallback-to-permissions", false)) {
            return hasPermission(player, tag) ? AccessResult.ALLOWED : AccessResult.PENDING;
        }
        return roleResult;
    }

    public void setRefreshCallback(java.util.function.Consumer<UUID> callback) {
        Objects.requireNonNull(callback, "callback");
        if (discordSrv != null) {
            discordSrv.setRefreshCallback(callback);
        }
        if (builtInDiscord != null) {
            builtInDiscord.setRefreshCallback(callback);
        }
    }

    public void start() {
        if (mode == AccessMode.BUILTIN_DISCORD && builtInDiscord != null) {
            builtInDiscord.start();
        }
    }

    public void stop() {
        if (discordSrv != null) {
            discordSrv.stop();
        }
        if (builtInDiscord != null) {
            builtInDiscord.stop();
        }
    }

    private boolean hasPermission(Player player, TagDefinition tag) {
        return tag.permission() != null && !tag.permission().isBlank() && player.hasPermission(tag.permission());
    }
}
