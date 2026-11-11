package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.Guis;
import dev.mintpvp.gui.HubGui;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.managers.QueueManager;
import dev.mintpvp.util.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /menu (the hub) and /queue [kit|leave]. */
public final class QueueCommands implements CommandExecutor, TabCompleter {

    private final MintPvP plugin;

    public QueueCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(t.error("players only."));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("menu")) {
            new HubGui(plugin, p).open(p);
            return true;
        }
        if (args.length == 0) {
            Guis.queueKits(plugin, p, QueueManager.Type.UNRANKED, null);
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            p.sendMessage(plugin.queue().leave(p.getUniqueId())
                    ? t.info("you left the queue.") : t.error("you're not in a queue."));
            return true;
        }
        boolean ranked = args[0].equalsIgnoreCase("ranked");
        if (ranked || args[0].equalsIgnoreCase("unranked")) {
            QueueManager.Type type = ranked ? QueueManager.Type.RANKED : QueueManager.Type.UNRANKED;
            if (args.length < 2) {
                Guis.queueKits(plugin, p, type, null);
                return true;
            }
            Kit k = plugin.kits().get(args[1]);
            if (k == null) { p.sendMessage(t.error("that kit doesn't exist.")); return true; }
            plugin.queue().join(p, k, type);
            return true;
        }
        Kit kit = plugin.kits().get(args[0]);
        if (kit == null) {
            p.sendMessage(t.error("that kit doesn't exist."));
            return true;
        }
        plugin.queue().join(p, kit, QueueManager.Type.UNRANKED);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("queue") && args.length == 1) {
            out.addAll(List.of("leave", "ranked", "unranked"));
            plugin.kits().all().forEach(k -> out.add(k.id));
        } else if (cmd.getName().equalsIgnoreCase("queue") && args.length == 2
                && (args[0].equalsIgnoreCase("ranked") || args[0].equalsIgnoreCase("unranked"))) {
            plugin.kits().all().forEach(k -> out.add(k.id));
        }
        String pre = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.startsWith(pre)).toList();
    }
}
