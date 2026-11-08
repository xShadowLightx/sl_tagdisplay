package io.github.shadowlight.tagdisplay.placeholder;

import io.github.shadowlight.tagdisplay.SLTagDisplayPlugin;
import io.github.shadowlight.tagdisplay.access.AccessResult;
import io.github.shadowlight.tagdisplay.model.TagDefinition;
import io.github.shadowlight.tagdisplay.model.TagType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class HeadtagPlaceholderExpansion extends PlaceholderExpansion {
    private final SLTagDisplayPlugin plugin;

    public HeadtagPlaceholderExpansion(SLTagDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sltagdisplay";
    }

    @Override
    public String getAuthor() {
        return "shadowlight";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (!"headtag".equalsIgnoreCase(params) || offlinePlayer == null) {
            return null;
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return "";
        }
        String selectedId = plugin.selections().get(player.getUniqueId(), TagType.HEADTAG);
        if (selectedId == null) {
            return "";
        }
        TagDefinition definition = plugin.catalog(TagType.HEADTAG).find(selectedId).orElse(null);
        if (definition == null || plugin.access().check(player, definition) != AccessResult.ALLOWED) {
            return "";
        }
        return LegacyComponentSerializer.legacySection().serialize(definition.display());
    }
}
