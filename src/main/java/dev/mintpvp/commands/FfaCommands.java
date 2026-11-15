package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.Guis;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ffa [kit|leave]: join a free-for-all lobby. */
public final class FfaCommands implements CommandExecutor, TabCompleter {

    private final MintPvP plugin;

    public FfaCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(t.error("players only."));
            return true;
        }
        if (args.length == 0) {
            Guis.ffaKits(plugin, p, null);
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            p.sendMessage(plugin.ffa().leave(p.getUniqueId()) ? t.info("you left the free-for-all lobby.") : t.error("you're not in a free-for-all lobby."));
            return true;
        }
        Kit kit = plugin.kits().get(args[0]);
        if (kit == null) {
            p.sendMessage(t.error("that kit doesn't exist."));
            return true;
        }
        plugin.ffa().join(p, kit);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("leave");
            plugin.kits().all().forEach(k -> out.add(k.id));
        }
        String pre = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.startsWith(pre)).toList();
    }
}
