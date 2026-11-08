package io.github.shadowlight.tagdisplay.storage;

import io.github.shadowlight.tagdisplay.model.TagType;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class PlayerSelectionStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public PlayerSelectionStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-selections.yml");
    }

    public void load() throws IOException, InvalidConfigurationException {
        if (!file.exists()) {
            plugin.saveResource("player-selections.yml", false);
        }
        data = new YamlConfiguration();
        data.load(file);
    }

    public String get(UUID playerId, TagType type) {
        return data.getString(path(playerId, type));
    }

    public void set(UUID playerId, TagType type, String tagId) {
        data.set(path(playerId, type), tagId == null || tagId.isBlank() ? null : tagId);
        save();
    }

    private String path(UUID playerId, TagType type) {
        return "players." + playerId + "." + type.selectionKey();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save player tag selections: " + exception.getMessage());
        }
    }
}
