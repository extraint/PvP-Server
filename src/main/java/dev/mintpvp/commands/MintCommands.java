package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.StatsManager;
import dev.mintpvp.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/** /mintpvp, /stats, /leaderboard, /kit */
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
            s.sendMessage(t.info("config reloaded."));
            return;
        }
        s.sendMessage(t.info(t.serverName() + " v" + plugin.getPluginMeta().getVersion()));
        s.sendMessage(t.usage("/stats [player]", "view stats"));
        s.sendMessage(t.usage("/leaderboard [elo|wins|kills|kd|streak]", "top players"));
        s.sendMessage(t.usage("/menu", "open the duels menu"));
        s.sendMessage(t.usage("/queue [kit|leave]", "join a random match"));
        s.sendMessage(t.usage("/duel [player] [kit]", "challenge a player"));
        s.sendMessage(t.usage("/kit", "edit your kit layouts"));
        if (s.hasPermission("mintpvp.admin")) {
            s.sendMessage(t.usage("/mintpvp reload", "reload config"));
            s.sendMessage(t.usage("/arena", "manage duel arenas"));
        }
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
        s.sendMessage(t.line("elo", st.elo + " · " + dev.mintpvp.util.Tier.of(st.elo).label));
        s.sendMessage(t.line("ranked", st.rankedWins + "w / " + st.rankedLosses + "l"));
        s.sendMessage(t.line("duels", st.duelWins + "w / " + st.duelLosses + "l"));
    }

    private void leaderboard(CommandSender s, String[] args) {
        Theme t = plugin.theme();
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "elo";
        Comparator<StatsManager.Stats> by;
        switch (mode) {
            case "kd" -> by = Comparator.comparingDouble(StatsManager.Stats::kd);
            case "streak" -> by = Comparator.comparingInt(st -> st.bestStreak);
            case "kills" -> by = Comparator.comparingInt(st -> st.kills);
            case "wins" -> by = Comparator.comparingInt(st -> st.duelWins);
            default -> {
                mode = "elo";
                by = Comparator.comparingInt(st -> st.elo);
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
                default -> st.elo + " (" + dev.mintpvp.util.Tier.of(st.elo).label + ")";
            };
            s.sendMessage(t.raw("#" + rank++ + " ", t.secondary())
                    .append(t.raw(plugin.stats().nameOf(entry.getKey()), t.primary()))
                    .append(t.raw(" - " + value, t.accent())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "mintpvp" -> s.hasPermission("mintpvp.admin") ? List.of("help", "reload") : List.of("help");
            case "leaderboard" -> List.of("elo", "wins", "kills", "kd", "streak");
            case "stats" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            default -> List.of();
        };
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
