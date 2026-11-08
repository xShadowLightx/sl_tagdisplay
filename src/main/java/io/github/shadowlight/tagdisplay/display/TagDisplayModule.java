package io.github.shadowlight.tagdisplay.display;

import io.github.shadowlight.tagdisplay.access.AccessResult;
import io.github.shadowlight.tagdisplay.access.TagAccessService;
import io.github.shadowlight.tagdisplay.config.TagCatalog;
import io.github.shadowlight.tagdisplay.model.TagDefinition;
import io.github.shadowlight.tagdisplay.model.TagType;
import io.github.shadowlight.tagdisplay.storage.PlayerSelectionStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class TagDisplayModule {
    private final JavaPlugin plugin;
    private final TagType type;
    private final TagCatalog catalog;
    private final PlayerSelectionStore selections;
    private final TagAccessService access;
    private final NamespacedKey ownedKey;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private BukkitTask followTask;

    public TagDisplayModule(JavaPlugin plugin, TagType type, TagCatalog catalog, PlayerSelectionStore selections,
                            TagAccessService access) {
        this.plugin = plugin;
        this.type = type;
        this.catalog = catalog;
        this.selections = selections;
        this.access = access;
        this.ownedKey = new NamespacedKey(plugin, "tag_display_" + type.selectionKey());
    }

    public void start() {
        removeOrphanDisplays();
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L);
        refreshAll();
    }

    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        for (TextDisplay display : displays.values()) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        removeOrphanDisplays();
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        String selectedId = selections.get(player.getUniqueId(), type);
        if (selectedId == null) {
            remove(player.getUniqueId());
            return;
        }

        TagDefinition definition = catalog.find(selectedId).orElse(null);
        if (definition == null) {
            if (plugin.getConfig().getBoolean("settings.clear-selection-on-access-loss", true)) {
                selections.set(player.getUniqueId(), type, null);
            }
            remove(player.getUniqueId());
            return;
        }

        AccessResult accessResult = access.check(player, definition);
        if (accessResult == AccessResult.DENIED) {
            if (plugin.getConfig().getBoolean("settings.clear-selection-on-access-loss", true)) {
                selections.set(player.getUniqueId(), type, null);
            }
            remove(player.getUniqueId());
            return;
        }
        if (accessResult == AccessResult.PENDING) {
            remove(player.getUniqueId());
            return;
        }

        TextDisplay display = displays.get(player.getUniqueId());
        if (display == null || !display.isValid() || display.getWorld() != player.getWorld()) {
            remove(player.getUniqueId());
            display = spawn(player, definition.display());
            displays.put(player.getUniqueId(), display);
        } else if (!display.text().equals(definition.display())) {
            display.text(definition.display());
        }
        display.teleport(displayLocation(player));
    }

    public void remove(UUID playerId) {
        TextDisplay display = displays.remove(playerId);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }

        Iterator<Map.Entry<UUID, TextDisplay>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TextDisplay> entry = iterator.next();
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                if (entry.getValue().isValid()) {
                    entry.getValue().remove();
                }
                iterator.remove();
            }
        }
    }

    private TextDisplay spawn(Player player, Component text) {
        Location location = displayLocation(player);
        return player.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setLineWidth(200);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.getPersistentDataContainer().set(ownedKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private Location displayLocation(Player player) {
        Location location = player.getLocation().clone();
        location.setY(location.getY() + type.heightAboveFeet());
        return location;
    }

    private void removeOrphanDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay
                        && entity.getPersistentDataContainer().has(ownedKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }
}
