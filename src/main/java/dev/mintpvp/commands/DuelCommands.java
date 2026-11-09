package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.ArenaManager.Arena;
import dev.mintpvp.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /duel and /arena */
public final class DuelCommands implements CommandExecutor, TabCompleter {

    private final MintPvP plugin;

    public DuelCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(t.error("players only."));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("arena")) arena(p, args);
        else duel(p, args);
        return true;
    }

    private void duel(Player p, String[] args) {
        Theme t = plugin.theme();
        if (!p.hasPermission("mintpvp.duel")) { p.sendMessage(t.error("no permission.")); return; }
        if (args.length == 0) {
            p.sendMessage(t.info("usage:"));
            p.sendMessage(t.line("/duel <player> [arena]", "challenge a player"));
            p.sendMessage(t.line("/duel accept|deny [player]", "answer a request"));
            p.sendMessage(t.line("/duel forfeit", "leave your duel (you lose)"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.duels().accept(p, args.length > 1 ? args[1] : null);
            case "deny" -> plugin.duels().deny(p, args.length > 1 ? args[1] : null);
            case "forfeit" -> {
                if (!plugin.duels().inDuel(p.getUniqueId())) p.sendMessage(t.error("you're not in a duel."));
                else plugin.duels().forfeit(p);
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { p.sendMessage(t.error("player not found.")); return; }
                plugin.duels().request(p, target, args.length > 1 ? args[1] : null);
            }
        }
    }

    private void arena(Player p, String[] args) {
        Theme t = plugin.theme();
        if (!p.hasPermission("mintpvp.admin")) { p.sendMessage(t.error("no permission.")); return; }
        if (args.length == 0) {
            p.sendMessage(t.line("/arena create|delete|setspawn1|setspawn2|setcenter|tp <name>", "manage arenas"));
            p.sendMessage(t.line("/arena setheight <name> <blocks>", "floor/ceiling distance from the centre"));
            p.sendMessage(t.line("/arena setradius <name> <blocks>", "border half-width (square)"));
            p.sendMessage(t.line("/arena list", "list arenas"));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            p.sendMessage(t.info("arenas (" + plugin.arenas().all().size() + ")"));
            for (Arena a : plugin.arenas().all()) {
                String status = !a.spawnsSet() ? "spawns not set"
                        : !a.ready() ? "spawns outside arena (raise radius/height)"
                        : a.inUse ? "in use" : "ready";
                status += " - radius " + a.radius() + ", height \u00b1" + a.height();
                p.sendMessage(t.line(a.name, status));
            }
            return;
        }
        if (args.length < 2) { p.sendMessage(t.error("specify an arena name.")); return; }
        String name = args[1];
        switch (sub) {
            case "create" -> {
                if (!name.matches("[A-Za-z0-9_-]{1,24}")) { p.sendMessage(t.error("use letters, numbers, - or _ (max 24).")); return; }
                p.sendMessage(plugin.arenas().create(name) != null
                        ? t.info("arena created. now stand at each spawn and run /arena setspawn1|setspawn2 " + name)
                        : t.error("that arena already exists."));
            }
            case "delete" -> p.sendMessage(plugin.arenas().delete(name)
                    ? t.info("arena deleted.") : t.error("no such arena (or it's in use)."));
            case "setspawn1", "setspawn2" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null) { p.sendMessage(t.error("no such arena.")); return; }
                if (sub.equals("setspawn1")) a.spawn1 = p.getLocation(); else a.spawn2 = p.getLocation();
                plugin.arenas().save();
                p.sendMessage(t.info(sub + " set for " + a.name + (a.ready() ? " - arena ready!"
                        : a.spawnsSet() ? " - spawn is outside the border; use /arena setcenter or setradius." : ".")));
            }
            case "setcenter" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null) { p.sendMessage(t.error("no such arena.")); return; }
                a.center = p.getLocation();
                plugin.arenas().save();
                p.sendMessage(t.info("centre set for " + a.name + " (radius " + a.radius() + ")."));
            }
            case "setradius" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null) { p.sendMessage(t.error("no such arena.")); return; }
                int r;
                try { r = Integer.parseInt(args.length > 2 ? args[2] : ""); }
                catch (NumberFormatException ex) { p.sendMessage(t.error("usage: /arena setradius <name> <blocks>")); return; }
                if (r < 5 || r > 500) { p.sendMessage(t.error("radius must be 5-500.")); return; }
                a.radius = r;
                plugin.arenas().save();
                p.sendMessage(t.info("radius set to " + r + " (" + (r * 2) + "x" + (r * 2) + " area)."
                        + (a.ready() ? "" : " spawns are still outside the border!")));
            }
            case "setheight" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null) { p.sendMessage(t.error("no such arena.")); return; }
                int h;
                try { h = Integer.parseInt(args.length > 2 ? args[2] : ""); }
                catch (NumberFormatException ex) { p.sendMessage(t.error("usage: /arena setheight <name> <blocks>")); return; }
                if (h < 3 || h > 200) { p.sendMessage(t.error("height must be 3-200.")); return; }
                a.height = h;
                plugin.arenas().save();
                p.sendMessage(t.info("height limit set to \u00b1" + h + " blocks around the centre."
                        + (a.ready() ? "" : " spawns are still outside the arena!")));
            }
            case "tp" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null || a.spawn1 == null) { p.sendMessage(t.error("no such arena or spawn not set.")); return; }
                p.teleport(a.spawn1);
            }
            default -> p.sendMessage(t.error("unknown subcommand."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        boolean arena = cmd.getName().equalsIgnoreCase("arena");
        if (args.length == 1) {
            if (arena) out.addAll(List.of("create", "delete", "setspawn1", "setspawn2", "setcenter", "setradius", "setheight", "list", "tp"));
            else {
                out.addAll(List.of("accept", "deny", "forfeit"));
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            }
        } else if (args.length == 2) {
            if (arena) plugin.arenas().all().forEach(a -> out.add(a.name));
            else if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            } else plugin.arenas().all().forEach(a -> out.add(a.name));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
