package io.github.shadowlight.tagdisplay.discord;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DiscordLinkStore {
    public enum LinkResult {
        LINKED,
        ALREADY_LINKED_TO_ANOTHER_PLAYER
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, String> discordByPlayer = new HashMap<>();
    private final Map<String, UUID> playerByDiscord = new HashMap<>();

    public DiscordLinkStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discord-links.yml");
    }

    public synchronized void load() throws IOException, InvalidConfigurationException {
        discordByPlayer.clear();
        playerByDiscord.clear();
        if (!file.exists()) {
            YamlConfiguration empty = new YamlConfiguration();
            empty.set("players", Map.of());
            empty.save(file);
            return;
        }

        YamlConfiguration data = new YamlConfiguration();
        data.load(file);
        if (data.getConfigurationSection("players") == null) {
            return;
        }
        for (String rawUuid : data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawUuid);
                String discordId = data.getString("players." + rawUuid, "").trim();
                if (discordId.matches("[0-9]{17,20}") && !playerByDiscord.containsKey(discordId)) {
                    discordByPlayer.put(playerId, discordId);
                    playerByDiscord.put(discordId, playerId);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring an invalid Minecraft UUID in discord-links.yml.");
            }
        }
    }

    public synchronized String discordId(UUID playerId) {
        return discordByPlayer.get(playerId);
    }

    public synchronized UUID playerId(String discordId) {
        return playerByDiscord.get(discordId);
    }

    public synchronized LinkResult link(UUID playerId, String discordId) {
        UUID existingPlayer = playerByDiscord.get(discordId);
        if (existingPlayer != null && !existingPlayer.equals(playerId)) {
            return LinkResult.ALREADY_LINKED_TO_ANOTHER_PLAYER;
        }
        String previousDiscordId = discordByPlayer.put(playerId, discordId);
        if (previousDiscordId != null) {
            playerByDiscord.remove(previousDiscordId);
        }
        playerByDiscord.put(discordId, playerId);
        save();
        return LinkResult.LINKED;
    }

    public synchronized String unlink(UUID playerId) {
        String discordId = discordByPlayer.remove(playerId);
        if (discordId != null) {
            playerByDiscord.remove(discordId);
            save();
        }
        return discordId;
    }

    public synchronized Map<UUID, String> allLinks() {
        return Map.copyOf(discordByPlayer);
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : discordByPlayer.entrySet()) {
            data.set("players." + entry.getKey(), entry.getValue());
        }
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save built-in Discord account links: " + exception.getMessage());
        }
    }
}
