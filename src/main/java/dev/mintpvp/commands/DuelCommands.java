package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.Guis;
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

/** duel commands */
public final class DuelCommands implements CommandExecutor, TabCompleter {

    private final MintPvP plugin;

    public DuelCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            // console-safe
            if (cmd.getName().equalsIgnoreCase("arena") && args.length > 0 && args[0].equalsIgnoreCase("list")) {
                listArenas(sender);
            } else if (cmd.getName().equalsIgnoreCase("arena") && args.length > 1 && args[0].equalsIgnoreCase("reset")) {
                resetArena(sender, args[1]);
            } else if (cmd.getName().equalsIgnoreCase("arena") && args.length > 0 && args[0].equalsIgnoreCase("themes")) {
                themes(sender);
            } else if (cmd.getName().equalsIgnoreCase("arena") && args.length > 1 && args[0].equalsIgnoreCase("check")) {
                checkArena(sender, args[1]);
            } else {
                sender.sendMessage(t.error("players only (console can use /arena list and /arena check <name>)."));
            }
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "arena" -> arena(p, args);
            case "spectate" -> spectate(p, args);
            case "challenge" -> challenge(p, args);
            default -> duel(p, args);
        }
        return true;
    }

    private void duel(Player p, String[] args) {
        Theme t = plugin.theme();
        if (!p.hasPermission("mintpvp.duel")) { p.sendMessage(t.error("no permission.")); return; }
        if (args.length == 0) {
            Guis.challenge(plugin, p, null);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.duels().accept(p, args.length > 1 ? args[1] : null);
            case "deny" -> plugin.duels().deny(p, args.length > 1 ? args[1] : null);
            case "help" -> {
                p.sendMessage(t.usage("/duel", "open the challenge menu"));
                p.sendMessage(t.usage("/duel <player> [kit] [rounds]", "challenge a player; pick the kit and \"first to\" rounds in a menu"));
                p.sendMessage(t.usage("/challenge <player> [kit]", "challenge a player to a ranked duel"));
                p.sendMessage(t.usage("/party", "team up and duel other parties"));
                p.sendMessage(t.usage("/party ffa [kit]", "party free-for-all (leader)"));
                p.sendMessage(t.usage("/duel accept|deny [player]", "answer a request"));
                p.sendMessage(t.usage("/duel forfeit", "leave your duel (you lose)"));
                p.sendMessage(t.usage("/spectate [player|arena|leave]", "watch a duel"));
            }
            case "rematch" -> plugin.duels().rematch(p);
            case "forfeit" -> {
                if (!plugin.duels().inDuel(p.getUniqueId())) p.sendMessage(t.error("you're not in a duel."));
                else plugin.duels().forfeit(p);
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { p.sendMessage(t.error("player not found.")); return; }
                String kit = args.length > 1 ? args[1] : null;
                if (kit != null && plugin.kits().get(kit) == null) { p.sendMessage(t.error("that kit doesn't exist.")); return; }
                String arena = null;
                Integer rounds = null;
                if (args.length > 2) {
                    try {
                        rounds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        arena = args[2];
                    }
                }
                if (rounds != null) {
                    plugin.duels().request(p, target, kit, arena, false, rounds);
                } else if (kit == null) {
                    Guis.kitAndRounds(plugin, p, target, false, null);
                } else {
                    Guis.roundsOnly(plugin, p, target, kit, arena, false);
                }
            }
        }
    }

    /** ranked challenge */
    private void challenge(Player p, String[] args) {
        Theme t = plugin.theme();
        if (!p.hasPermission("mintpvp.duel")) { p.sendMessage(t.error("no permission.")); return; }
        if (!plugin.queue().rankedEnabled()) { p.sendMessage(t.error("ranked is disabled right now.")); return; }
        if (args.length == 0) {
            Guis.challenge(plugin, p, null, true);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(t.error("player not found.")); return; }
        String kit = args.length > 1 ? args[1] : null;
        if (kit != null && plugin.kits().get(kit) == null) { p.sendMessage(t.error("that kit doesn't exist.")); return; }
        plugin.duels().request(p, target, kit, null, true);
    }

    private void spectate(Player p, String[] args) {
        Theme t = plugin.theme();
        if (!p.hasPermission("mintpvp.spectate")) { p.sendMessage(t.error("no permission.")); return; }
        if (args.length == 0) {
            if (plugin.duels().isSpectating(p.getUniqueId())) {
                plugin.duels().stopSpectating(p);
                p.sendMessage(t.info("stopped spectating."));
                return;
            }
            var duels = plugin.duels().activeDuels();
            p.sendMessage(t.info("running duels (" + duels.size() + ")"));
            for (var d : duels) {
                p.sendMessage(t.raw(d.title(), t.secondary())
                        .append(t.text(": " + d.kit.display + " on " + d.arena.name + " - "
                                + d.spectators.size() + " watching", t.accent())));
            }
            p.sendMessage(t.usage("/spectate <player|arena>", "watch a duel"));
            return;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            p.sendMessage(plugin.duels().stopSpectating(p)
                    ? t.info("stopped spectating.") : t.error("you're not spectating."));
            return;
        }
        plugin.duels().spectate(p, args[0]);
    }

    private void listArenas(CommandSender s) {
        Theme t = plugin.theme();
        s.sendMessage(t.info("arenas (" + plugin.arenas().all().size() + ")"));
        for (Arena a : plugin.arenas().all()) {
            String status = a.problem() != null ? a.problem() : a.inUse ? "in use" : "ready";
            if (a.theme != null) status = a.theme + " · " + status;
            s.sendMessage(t.line(a.name, status + (a.ready() ? " - " + (a.radius() * 2) + "x" + (a.radius() * 2)
                    + (a.heightLimited() ? ", height \u00b1" + a.height() : ", no height limit") : "")));
        }
    }

    private void resetArena(CommandSender s, String name) {
        Theme t = plugin.theme();
        Arena a = plugin.arenas().get(name);
        if (a == null || !plugin.generator().resets(a)) { s.sendMessage(t.error("only generated arenas can be reset.")); return; }
        if (a.inUse) { s.sendMessage(t.error("that arena is in use.")); return; }
        a.inUse = true;
        s.sendMessage(t.info("resetting " + a.name + "..."));
        plugin.generator().reset(a, () -> {
            a.inUse = false;
            s.sendMessage(t.info(a.name + " has been reset."));
        });
    }

    private void themes(CommandSender s) {
        Theme t = plugin.theme();
        s.sendMessage(t.info("arena themes - generator: " + plugin.generator().status()));
        for (dev.mintpvp.arenagen.ArenaTheme th : dev.mintpvp.arenagen.ArenaTheme.values()) {
            long small = plugin.arenas().all().stream().filter(a -> th.id.equals(a.theme) && !a.large).count();
            long big = plugin.arenas().all().stream().filter(a -> th.id.equals(a.theme) && a.large).count();
            s.sendMessage(t.usage(th.id, th.display + " - " + small + " unranked, " + big + " ranked"));
        }
        s.sendMessage(t.usage("/arena generate <theme|random> [count] [large]", "make more"));
    }

    private void checkArena(CommandSender s, String name) {
        Theme t = plugin.theme();
        Arena a = plugin.arenas().get(name);
        if (a == null) { s.sendMessage(t.error("no such arena.")); return; }
        s.sendMessage(t.info("arena " + a.name));
        if (a.theme != null) s.sendMessage(t.line("generated", a.theme + " theme, " + (a.large ? "large (ranked)" : "small") + ", slot " + a.slot + ", round"));
        s.sendMessage(t.line("spawn 1", a.describeSpawn1()));
        s.sendMessage(t.line("spawn 2", a.describeSpawn2()));
        if (a.ready()) {
            var c = a.center();
            s.sendMessage(t.line("centre", Math.round(c.getX()) + " " + Math.round(c.getY()) + " " + Math.round(c.getZ())));
            s.sendMessage(t.line("border", "radius " + a.radius() + (a.circular() ? " (round)" : " (square)")
                    + (a.heightLimited() ? ", height \u00b1" + a.height() : ", no height limit")));
            s.sendMessage(t.line("status", a.inUse ? "in use" : "ready"));
        } else {
            s.sendMessage(t.error("not usable: " + a.problem()));
        }
    }

    private void arena(Player p, String[] args) {
        Theme t = plugin.theme();
        if (!p.hasPermission("mintpvp.admin")) { p.sendMessage(t.error("no permission.")); return; }
        if (args.length == 0) {
            p.sendMessage(t.usage("/arena create|delete|setspawn1|setspawn2|setcenter|check|tp <name>", "manage arenas"));
            p.sendMessage(t.usage("/arena setheight <name> <blocks>", "floor/ceiling distance from the centre"));
            p.sendMessage(t.usage("/arena setradius <name> <blocks>", "border half-width (square)"));
            p.sendMessage(t.usage("/arena list", "list arenas and their status"));
            p.sendMessage(t.usage("/arena themes", "pre-made arena themes"));
            p.sendMessage(t.usage("/arena lobby", "make the generated lobby the server spawn"));
            p.sendMessage(t.usage("/arena reset <name>", "reset a generated arena to its original state"));
            p.sendMessage(t.usage("/arena generate <theme|random> [count] [large]", "make pre-made arenas (large = ranked size)"));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            listArenas(p);
            return;
        }
        if (sub.equals("lobby")) {
            var lobby = plugin.generator().lobbySpawn();
            if (lobby == null) {
                p.sendMessage(t.error("the arena generator is off (arena-generator.enabled in config.yml)."));
                return;
            }
            plugin.spawn().set(lobby);
            p.sendMessage(t.info("the spawn is now the lobby."));
            return;
        }
        if (sub.equals("themes")) {
            themes(p);
            return;
        }
        if (sub.equals("generate")) {
            if (!plugin.generator().enabled() || plugin.generator().world() == null) {
                p.sendMessage(t.error("the arena generator is off (arena-generator.enabled in config.yml)."));
                return;
            }
            String which = args.length > 1 ? args[1] : "random";
            dev.mintpvp.arenagen.ArenaTheme theme = dev.mintpvp.arenagen.ArenaTheme.byId(which);
            if (theme == null && !which.equalsIgnoreCase("random")) {
                p.sendMessage(t.error("unknown theme. use /arena themes"));
                return;
            }
            int amount = 1;
            boolean large = false;
            for (int i = 2; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("large") || args[i].equalsIgnoreCase("ranked")) {
                    large = true;
                    continue;
                }
                try { amount = Math.max(1, Math.min(30, Integer.parseInt(args[i]))); }
                catch (NumberFormatException e) { p.sendMessage(t.error("usage: /arena generate <theme|random> [count] [large]")); return; }
            }
            int queued = plugin.generator().generate(theme, amount, large);
            p.sendMessage(queued > 0
                    ? t.info("making " + queued + (large ? " large ranked" : "") + " arena" + (queued == 1 ? "" : "s") + " (" + plugin.generator().status() + ").")
                    : t.error("the arena limit is reached (arena-generator.max-total)."));
            return;
        }
        if (args.length < 2) { p.sendMessage(t.error("specify an arena name.")); return; }
        String name = args[1];
        switch (sub) {
            case "check" -> checkArena(p, name);
            case "reset" -> resetArena(p, name);
            case "create" -> {
                if (!name.matches("[A-Za-z0-9_-]{1,24}")) { p.sendMessage(t.error("use letters, numbers, - or _ (max 24).")); return; }
                p.sendMessage(plugin.arenas().create(name) != null
                        ? t.info("arena created. now stand at each spawn and run /arena setspawn1 " + name + " and /arena setspawn2 " + name)
                        : t.error("that arena already exists."));
            }
            case "delete" -> p.sendMessage(plugin.arenas().delete(name)
                    ? t.info("arena deleted.") : t.error("no such arena (or it's in use)."));
            case "setspawn1", "setspawn2" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null) { p.sendMessage(t.error("no such arena.")); return; }
                if (sub.equals("setspawn1")) a.setSpawn1(p.getLocation()); else a.setSpawn2(p.getLocation());
                plugin.arenas().save();
                p.sendMessage(a.ready()
                        ? t.info(sub + " set - arena " + a.name + " is ready (border grows to fit both spawns: "
                                + (a.radius() * 2) + "x" + (a.radius() * 2) + ").")
                        : t.info(sub + " set for " + a.name + ". still needed: " + a.problem() + "."));
            }
            case "setcenter" -> {
                Arena a = plugin.arenas().get(name);
                if (a == null) { p.sendMessage(t.error("no such arena.")); return; }
                a.setCenter(p.getLocation());
                plugin.arenas().save();
                p.sendMessage(t.info("centre set for " + a.name + (a.ready() ? " (radius " + a.radius() + ")." : ".")));
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
                p.sendMessage(t.info("radius set to " + r + " (the border still grows if a spawn is further out)."));
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
                p.sendMessage(t.info("height limit set to \u00b1" + h + " blocks around the centre."));
            }
            case "tp" -> {
                Arena a = plugin.arenas().get(name);
                var spawn = a == null ? null : a.spawn1();
                if (spawn == null) { p.sendMessage(t.error("no such arena, or spawn 1 isn't set / its world isn't loaded.")); return; }
                p.teleport(spawn);
            }
            default -> p.sendMessage(t.error("unknown subcommand."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        boolean arena = cmd.getName().equalsIgnoreCase("arena");
        if (cmd.getName().equalsIgnoreCase("challenge")) {
            if (args.length == 1) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            else if (args.length == 2) plugin.kits().all().forEach(k -> out.add(k.id));
            String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
            return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
        }
        if (cmd.getName().equalsIgnoreCase("spectate")) {
            if (args.length == 1) {
                out.add("leave");
                plugin.duels().activeDuels().forEach(d -> {
                    d.players.forEach(pl -> out.add(pl.getName()));
                    out.add(d.arena.name);
                });
            }
            String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
            return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
        }
        if (args.length == 1) {
            if (arena) out.addAll(List.of("create", "delete", "setspawn1", "setspawn2", "setcenter", "setradius", "setheight", "check", "list", "tp", "themes", "generate", "lobby", "reset"));
            else {
                out.addAll(List.of("accept", "deny", "forfeit"));
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            }
        } else if (args.length == 2) {
            if (arena && args.length == 3 && args[0].equalsIgnoreCase("generate")) {
                out.add("large");
            } else if (arena && args[0].equalsIgnoreCase("generate")) {
                out.add("random");
                for (var th : dev.mintpvp.arenagen.ArenaTheme.values()) out.add(th.id);
            } else if (arena) plugin.arenas().all().forEach(a -> out.add(a.name));
            else if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            } else plugin.kits().all().forEach(k -> out.add(k.id));
        } else if (args.length == 3 && !arena) {
            plugin.arenas().all().forEach(a -> out.add(a.name));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
