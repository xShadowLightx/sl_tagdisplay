package io.github.shadowlight.tagdisplay.model;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.Set;

public record TagDefinition(String id, Component display, String permission, Set<String> discordRoleIds, Material icon) {
    public TagDefinition {
        discordRoleIds = Set.copyOf(discordRoleIds);
    }
}
