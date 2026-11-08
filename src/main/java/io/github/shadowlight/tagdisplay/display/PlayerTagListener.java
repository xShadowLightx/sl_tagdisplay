package io.github.shadowlight.tagdisplay.display;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;

public final class PlayerTagListener implements Listener {
    private final List<TagDisplayModule> modules;

    public PlayerTagListener(List<TagDisplayModule> modules) {
        this.modules = List.copyOf(modules);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (TagDisplayModule module : modules) {
            module.remove(event.getPlayer().getUniqueId());
        }
    }

    private void refresh(Player player) {
        for (TagDisplayModule module : modules) {
            module.refresh(player);
        }
    }
}
