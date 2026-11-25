package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.StatsManager;
import dev.mintpvp.managers.TabHook;
import dev.mintpvp.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/** stats commands */
public final class MintCommands implements CommandExecutor, TabCompleter {

    private final MintPvP plugin;

    public MintCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "mintpvp" -> main(sender, args);
            case "stats" -> stats(sender, args);
            case "leaderboard" -> leaderboard(sender, args);
            case "region" -> {
                if (sender instanceof Player p) new dev.mintpvp.gui.RegionGui(plugin, p, null).open(p);
                else sender.sendMessage(plugin.theme().error("players only."));
            }
            case "eu", "na" -> {
                if (sender instanceof Player p) plugin.lobby().transfer(p, cmd.getName().toLowerCase(Locale.ROOT));
                else sender.sendMessage(plugin.theme().error("players only."));
            }
            case "tiers" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.theme().error("players only.")); return true; }
                if (args.length > 0) {
                    var found = Bukkit.getPlayerExact(args[0]) != null ? java.util.Optional.of(Bukkit.getPlayerExact(args[0]).getUniqueId()) : plugin.stats().findByName(args[0]);
                    if (found.isEmpty()) { p.sendMessage(plugin.theme().error("no player with that name has played here.")); return true; }
                    dev.mintpvp.gui.TierGui.openFor(plugin, p, found.get(), null);
                } else {
                    dev.mintpvp.gui.TierGui.open(plugin, p, null);
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private void main(CommandSender s, String[] args) {
        Theme t = plugin.theme();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("mintpvp.admin")) {
                s.sendMessage(t.error("no permission."));
                return;
            }
            plugin.reloadConfig();
            plugin.applyConfig();
            if (plugin.tab() != null) plugin.tab().reregister();
            s.sendMessage(t.info("config reloaded."));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("region")) {
            if (!s.hasPermission("mintpvp.admin")) {
                s.sendMessage(t.error("no permission."));
                return;
            }
            var cfg = plugin.getConfig();
            s.sendMessage(t.info("region settings (what the menu reads)"));
            s.sendMessage(t.raw("region.this = " + cfg.getString("region.this", "(missing, default na)") + "  ->  you are: " + plugin.lobby().currentRegion(), t.accent()));
            for (String id : new String[] {"na", "eu"}) {
                String a = plugin.lobby().regionAddress(id);
                s.sendMessage(t.raw(id + ": " + (a == null ? "not set" : a) + (id.equals(plugin.lobby().currentRegion()) ? "  (this server)" : "")
                        + (cfg.getStringList("region.link-only").contains(id) ? "  (link only)" : ""), t.accent()));
            }
            java.io.File f = new java.io.File(plugin.getDataFolder(), "config.yml");
            s.sendMessage(t.raw("file: " + f.getAbsolutePath() + " (" + f.length() + " bytes, changed " + (System.currentTimeMillis() - f.lastModified()) / 1000 + "s ago)", t.muted()));
            var disk = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            s.sendMessage(t.raw("straight from the file: this=" + disk.getString("region.this") + ", na=" + disk.getString("region.servers.na")
                    + ", eu=" + disk.getString("region.servers.eu"), t.accent()));
            s.sendMessage(t.raw("getConfig(): this=" + cfg.getString("region.this") + ", na=" + cfg.getString("region.servers.na")
                    + ", eu=" + cfg.getString("region.servers.eu"), t.accent()));
            s.sendMessage(t.raw("set in loaded config (not a default): this=" + cfg.contains("region.this", true)
                    + ", na=" + cfg.contains("region.servers.na", true) + ", eu=" + cfg.contains("region.servers.eu", true), t.accent()));
            s.sendMessage(t.raw("top-level keys in the file: " + disk.getKeys(false), t.muted()));
            try {
                var lines = java.nio.file.Files.readAllLines(f.toPath());
                int shown = 0;
                for (int i = 0; i < lines.size() && shown < 14; i++) {
                    String l = lines.get(i);
                    if (l.matches("^\\s*(region|servers|this|na|eu|link-only)\\s*:.*")) {
                        s.sendMessage(t.raw((i + 1) + ": " + l.replace(' ', '\u00b7'), t.muted()));
                        shown++;
                    }
                }
            } catch (java.io.IOException e) {
                s.sendMessage(t.raw("could not read the file: " + e.getMessage(), t.muted()));
            }
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("tags")) {
            if (!s.hasPermission("mintpvp.admin")) {
                s.sendMessage(t.error("no permission."));
                return;
            }
            s.sendMessage(t.info("tier tags check"));
            s.sendMessage(t.raw(TabHook.tabPresent()
                    ? "TAB is installed: it owns nametags and the tab list. placeholders registered: " + (plugin.tab() != null && plugin.tab().hooked())
                            + ". status: " + (plugin.tab() != null ? plugin.tab().status() : "none")
                            + ". if tags still don't show, put %mintpvp_tag% in TAB's tagprefix and tabprefix."
                    : "TAB is not installed: the plugin sets nametags and the tab list itself.", t.accent()));
            var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            if (s instanceof Player viewer) {
                var board = viewer.getScoreboard();
                boolean ours = board.getObjective("mint") != null;
                s.sendMessage(t.raw("your scoreboard is " + (ours ? "the plugin's" : "NOT the plugin's (another plugin replaced it)")
                        + ", teams on it: " + board.getTeams().size(), t.accent()));
                for (Player o : Bukkit.getOnlinePlayers()) {
                    var st = plugin.stats().get(o.getUniqueId());
                    int best = st.bestTier();
                    var team = board.getEntryTeam(o.getName());
                    String have = team == null ? "no team" : "team " + team.getName() + " prefix [" + plain.serialize(team.prefix()) + "]";
                    s.sendMessage(t.raw(o.getName() + ": best tier " + dev.mintpvp.util.Tier.name(best) + " -> expected tag "
                            + (best >= 0 ? dev.mintpvp.util.Tier.name(best) : "none") + " | on your scoreboard: " + have, t.muted()));
                }
            } else {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    s.sendMessage(t.raw(o.getName() + ": best tier " + dev.mintpvp.util.Tier.name(plugin.stats().get(o.getUniqueId()).bestTier())
                            + ", scoreboard ours: " + (o.getScoreboard().getObjective("mint") != null), t.muted()));
                }
            }
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("db")) {
            if (!s.hasPermission("mintpvp.admin")) {
                s.sendMessage(t.error("no permission."));
                return;
            }
            s.sendMessage(t.info("stats database"));
            for (String line : plugin.stats().dbStatus()) s.sendMessage(t.raw(line, t.accent()));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("tier")) {
            if (!s.hasPermission("mintpvp.admin")) {
                s.sendMessage(t.error("no permission."));
                return;
            }
            adminTier(s, args);
            return;
        }
        s.sendMessage(t.info(t.serverName() + " v" + plugin.getPluginMeta().getVersion()));
        s.sendMessage(t.usage("/stats [player]", "view stats"));
        s.sendMessage(t.usage("/leaderboard [tier|wins|kills|kd|streak]", "top players"));
        s.sendMessage(t.usage("/menu", "open the duels menu"));
        s.sendMessage(t.usage("/queue [kit|leave]", "join a random match"));
        s.sendMessage(t.usage("/duel [player] [kit]", "challenge a player"));
        s.sendMessage(t.usage("/kit", "edit your kit layouts"));
        if (s.hasPermission("mintpvp.admin")) {
            s.sendMessage(t.usage("/mintpvp reload", "reload config"));
            s.sendMessage(t.usage("/mintpvp db", "stats database status"));
            s.sendMessage(t.usage("/mintpvp region", "region settings check"));
            s.sendMessage(t.usage("/mintpvp tags", "tier tag check"));
            s.sendMessage(t.usage("/mintpvp tier <set|reset|info> ...", "set players' kit tiers"));
            s.sendMessage(t.usage("/arena", "manage duel arenas"));
        }
    }

    /** admin tier command */
    private void adminTier(CommandSender s, String[] args) {
        Theme t = plugin.theme();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!List.of("set", "reset", "info").contains(sub) || args.length < 3) {
            s.sendMessage(t.usage("/mintpvp tier set <player> <kit|all> <lt5..ht1|unranked>", "set a kit tier"));
            s.sendMessage(t.usage("/mintpvp tier reset <player> [kit|all]", "make a kit (or every kit) unranked"));
            s.sendMessage(t.usage("/mintpvp tier info <player>", "see a player's kit tiers"));
            return;
        }
        UUID id;
        String name;
        Player online = Bukkit.getPlayerExact(args[2]);
        if (online != null) {
            id = online.getUniqueId();
            name = online.getName();
        } else {
            var found = plugin.stats().findByName(args[2]);
            if (found.isEmpty()) { s.sendMessage(t.error("no player with that name has played here.")); return; }
            id = found.get();
            name = plugin.stats().nameOf(id);
        }
        var st = plugin.stats().get(id);
        if (sub.equals("info")) {
            s.sendMessage(t.info(t.raw(name, t.primary()).append(t.text(" · tier " + dev.mintpvp.util.Tier.overallLabel(st.score()), t.accent()))));
            for (var kit : plugin.kits().all()) s.sendMessage(t.line(kit.display, dev.mintpvp.util.Tier.name(st.tierIndex(kit.id))));
            return;
        }
        String kitArg = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "all";
        List<String> kitIds = new java.util.ArrayList<>();
        if (kitArg.equals("all")) {
            if (sub.equals("set") && args.length <= 3) { s.sendMessage(t.error("usage: /mintpvp tier set <player> <kit|all> <tier>")); return; }
            plugin.kits().all().forEach(k -> kitIds.add(k.id));
        } else {
            var kit = plugin.kits().get(kitArg);
            if (kit == null) { s.sendMessage(t.error("that kit doesn't exist.")); return; }
            kitIds.add(kit.id);
        }
        int idx = -1;
        if (sub.equals("set")) {
            if (args.length < 5) { s.sendMessage(t.error("usage: /mintpvp tier set <player> <kit|all> <lt5..ht1|unranked>")); return; }
            String tn = args[4].toLowerCase(Locale.ROOT);
            if (!tn.equals("unranked")) {
                for (var tier : dev.mintpvp.util.Tier.values()) if (tier.label.equals(tn)) idx = tier.ordinal();
                if (idx < 0) { s.sendMessage(t.error("tier must be lt5, ht5, lt4, ht4, lt3, ht3, lt2, ht2, lt1, ht1 or unranked.")); return; }
            }
        }
        for (String kid : kitIds) plugin.stats().setTier(id, kid, idx);
        s.sendMessage(t.info(t.raw(name, t.primary())
                .append(t.text(" is now " + dev.mintpvp.util.Tier.name(idx) + " in " + (kitIds.size() == 1 ? kitIds.get(0) : "all " + kitIds.size() + " kits")
                        + " · tier " + dev.mintpvp.util.Tier.overallLabel(plugin.stats().get(id).score()), t.accent()))));
    }

    private void stats(CommandSender s, String[] args) {
        Theme t = plugin.theme();
        UUID id;
        String name;
        if (args.length > 0) {
            Optional<UUID> found = plugin.stats().findByName(args[0]);
            if (found.isEmpty()) {
                s.sendMessage(t.error("no stats found for that player."));
                return;
            }
            id = found.get();
            name = plugin.stats().nameOf(id);
        } else if (s instanceof Player p) {
            id = p.getUniqueId();
            name = p.getName();
        } else {
            s.sendMessage(t.error("specify a player."));
            return;
        }
        StatsManager.Stats st = plugin.stats().get(id);
        s.sendMessage(t.info(t.raw(name, t.primary()).append(t.text("'s stats", t.accent()))));
        s.sendMessage(t.line("kills", String.valueOf(st.kills)));
        s.sendMessage(t.line("deaths", String.valueOf(st.deaths)));
        s.sendMessage(t.line("k/d", String.format(Locale.ROOT, "%.2f", st.kd())));
        s.sendMessage(t.line("best streak", String.valueOf(st.bestStreak)));
        s.sendMessage(t.line("tier", dev.mintpvp.util.Tier.overallLabel(st.score())));
        for (var kit : plugin.kits().all()) {
            int ti = st.tierIndex(kit.id);
            if (ti >= 0) s.sendMessage(t.line(kit.display, dev.mintpvp.util.Tier.name(ti)));
        }
        s.sendMessage(t.line("ranked", st.rankedWins + "w / " + st.rankedLosses + "l"));
        s.sendMessage(t.line("duels", st.duelWins + "w / " + st.duelLosses + "l"));
    }

    private void leaderboard(CommandSender s, String[] args) {
        Theme t = plugin.theme();
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "tier";
        Comparator<StatsManager.Stats> by;
        switch (mode) {
            case "kd" -> by = Comparator.comparingDouble(StatsManager.Stats::kd);
            case "streak" -> by = Comparator.comparingInt(st -> st.bestStreak);
            case "kills" -> by = Comparator.comparingInt(st -> st.kills);
            case "wins" -> by = Comparator.comparingInt(st -> st.duelWins);
            default -> {
                mode = "tier";
                by = Comparator.comparingInt(StatsManager.Stats::score);
            }
        }
        s.sendMessage(t.info("top 10 - " + mode));
        int rank = 1;
        for (var entry : plugin.stats().top(by, 10)) {
            StatsManager.Stats st = entry.getValue();
            String value = switch (mode) {
                case "kd" -> String.format(Locale.ROOT, "%.2f", st.kd());
                case "streak" -> String.valueOf(st.bestStreak);
                case "kills" -> String.valueOf(st.kills);
                case "wins" -> String.valueOf(st.duelWins);
                default -> dev.mintpvp.util.Tier.overallLabel(st.score());
            };
            s.sendMessage(t.raw("#" + rank++ + " ", t.secondary())
                    .append(t.raw(plugin.stats().nameOf(entry.getKey()), t.primary()))
                    .append(t.raw(" - " + value, t.accent())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("mintpvp") && s.hasPermission("mintpvp.admin") && args.length >= 2 && args[0].equalsIgnoreCase("tier")) {
            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> opts = switch (args.length) {
                case 2 -> List.of("set", "reset", "info");
                case 3 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case 4 -> args[1].equalsIgnoreCase("info") ? List.<String>of() : java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), plugin.kits().all().stream().map(k -> k.id)).toList();
                case 5 -> args[1].equalsIgnoreCase("set") ? java.util.stream.Stream.concat(java.util.stream.Stream.of("unranked"), java.util.Arrays.stream(dev.mintpvp.util.Tier.values()).map(x -> x.label)).toList() : List.<String>of();
                default -> List.<String>of();
            };
            return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
        }
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "mintpvp" -> s.hasPermission("mintpvp.admin") ? List.of("help", "reload", "tier", "db", "region", "tags") : List.of("help");
            case "leaderboard" -> List.of("tier", "wins", "kills", "kd", "streak");
            case "tiers", "stats" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            default -> List.of();
        };
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
