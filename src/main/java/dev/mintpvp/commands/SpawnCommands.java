package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /spawn and /setspawn */
public final class SpawnCommands implements CommandExecutor {

    private final MintPvP plugin;

    public SpawnCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(t.error("players only."));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("setspawn")) {
            if (!p.hasPermission("mintpvp.admin")) { p.sendMessage(t.error("no permission.")); return true; }
            plugin.spawn().set(p.getLocation());
            p.sendMessage(t.info("spawn set to your location (this is now the world spawn too)."));
            return true;
        }
        if (plugin.duels().inDuel(p.getUniqueId())) { p.sendMessage(t.error("you can't do that during a duel.")); return true; }
        if (plugin.duels().isSpectating(p.getUniqueId())) { p.sendMessage(t.error("leave spectating first (/spectate leave).")); return true; }
        if (plugin.combat().isTagged(p.getUniqueId())) { p.sendMessage(t.error("you can't teleport while in combat.")); return true; }
        p.teleport(plugin.spawn().get());
        p.sendMessage(t.info("teleported to spawn."));
        return true;
    }
}
