package io.github.shadowlight.tagdisplay;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class TagDisplayCommand implements CommandExecutor {
    private final SLTagDisplayPlugin plugin;

    public TagDisplayCommand(SLTagDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Component.text("/headtag - choose your headtag"));
            sender.sendMessage(Component.text("/gangtag - choose your gangtag"));
            sender.sendMessage(Component.text("/tagdisplay reload - reload tag definitions"));
            sender.sendMessage(Component.text("/tagdisplay link|unlink - manage built-in Discord linking"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("sltagdisplay.admin")) {
                sender.sendMessage(Component.text("You do not have permission to reload SL Tag Display."));
                return true;
            }
            if (plugin.reloadPluginConfiguration()) {
                sender.sendMessage(Component.text("SL Tag Display configuration reloaded."));
            } else {
                sender.sendMessage(Component.text("SL Tag Display could not reload its configuration. Check the server log."));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("link")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("This command can only be used in game.");
                return true;
            }
            plugin.beginDiscordLink(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("unlink")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("This command can only be used in game.");
                return true;
            }
            plugin.unlinkDiscordAccount(player);
            return true;
        }

        sender.sendMessage(Component.text("Unknown subcommand. Use /tagdisplay help."));
        return true;
    }
}
