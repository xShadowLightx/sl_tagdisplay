package io.github.shadowlight.tagdisplay.config;

import io.github.shadowlight.tagdisplay.model.TagDefinition;
import io.github.shadowlight.tagdisplay.model.TagType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public final class TagCatalog {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final TagType type;
    private Map<String, TagDefinition> definitions = Map.of();

    public TagCatalog(JavaPlugin plugin, TagType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public void reload(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection(type.configKey());
        String accessMode = root.getString("access.mode", "permissions").trim().toLowerCase(Locale.ROOT);
        Map<String, TagDefinition> replacement = new LinkedHashMap<>();
        if (section != null) {
            for (String configuredId : section.getKeys(false)) {
                String id = configuredId.toLowerCase(Locale.ROOT);
                ConfigurationSection tag = section.getConfigurationSection(configuredId);
                if (tag == null || !VALID_ID.matcher(id).matches()) {
                    plugin.getLogger().warning("Ignoring invalid " + type.displayName().toLowerCase(Locale.ROOT)
                            + " definition '" + configuredId + "'. IDs may contain lowercase letters, numbers, '_' and '-'.");
                    continue;
                }

                String rawDisplay = tag.getString("display", "");
                String permission = tag.getString("permission", "").trim();
                List<String> roleIds = tag.getStringList("discord-roles").stream()
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
                boolean hasAccessRule = switch (accessMode) {
                    case "discordsrv", "builtin-discord" -> !roleIds.isEmpty();
                    default -> !permission.isBlank();
                };
                if (rawDisplay.isBlank() || !hasAccessRule) {
                    plugin.getLogger().warning("Ignoring " + type.displayName().toLowerCase(Locale.ROOT) + " '" + id
                            + "': display text and an access rule matching access.mode are required.");
                    continue;
                }

                if (accessMode.equals("discordsrv") || accessMode.equals("builtin-discord")) {
                    roleIds.removeIf(roleId -> {
                        boolean invalid = !roleId.matches("[0-9]{17,20}");
                        if (invalid) {
                            plugin.getLogger().warning("Ignoring invalid Discord role ID '" + roleId + "' on tag '" + id + "'.");
                        }
                        return invalid;
                    });
                }

                Material icon = Material.matchMaterial(tag.getString("icon", "NAME_TAG"));
                if (icon == null || !icon.isItem()) {
                    plugin.getLogger().warning("Invalid menu icon for " + type.displayName().toLowerCase(Locale.ROOT)
                            + " '" + id + "'; using NAME_TAG.");
                    icon = Material.NAME_TAG;
                }

                try {
                    Component display = MINI_MESSAGE.deserialize(rawDisplay);
                    if (display.equals(Component.empty())) {
                        plugin.getLogger().warning("Ignoring empty " + type.displayName().toLowerCase(Locale.ROOT) + " '" + id + "'.");
                        continue;
                    }
                    TagDefinition previous = replacement.putIfAbsent(id, new TagDefinition(id, display, permission, Set.copyOf(roleIds), icon));
                    if (previous != null) {
                        plugin.getLogger().warning("Ignoring duplicate " + type.displayName().toLowerCase(Locale.ROOT) + " ID '" + id + "'.");
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Ignoring invalid display text for " + type.displayName().toLowerCase(Locale.ROOT)
                            + " '" + id + "': " + exception.getMessage());
                }
            }
        }
        definitions = Map.copyOf(replacement);
        plugin.getLogger().info("Loaded " + definitions.size() + " " + type.displayName().toLowerCase(Locale.ROOT) + " definition(s).");
    }

    public Optional<TagDefinition> find(String id) {
        return Optional.ofNullable(id == null ? null : definitions.get(id));
    }

    public Collection<TagDefinition> all() {
        List<TagDefinition> ordered = new ArrayList<>(definitions.values());
        ordered.sort(Comparator.comparing(TagDefinition::id));
        return List.copyOf(ordered);
    }
}
