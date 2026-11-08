package io.github.shadowlight.tagdisplay;

import org.bukkit.plugin.java.JavaPlugin;

public final class SLTagDisplayPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("SL Tag Display is starting; headtag and gangtag modules will initialize independently.");
    }
}
