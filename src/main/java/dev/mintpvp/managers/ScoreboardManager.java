package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Theme;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Mint-green small-caps sidebar + combat-timer action bar. */
public final class ScoreboardManager {

    private final MintPvP plugin;
    private BukkitTask task;

    public ScoreboardManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                update(p);
                actionBar(p);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void actionBar(Player p) {
        Theme t = plugin.theme();
        UUID id = p.getUniqueId();
        var q = plugin.queue().entryOf(id);
        if (q != null && !plugin.combat().isTagged(id)) {
            long sec = (System.currentTimeMillis() - q.since()) / 1000;
            p.sendActionBar(t.text("queued · " + q.kit().display + " · ", t.secondary())
                    .append(Component.text(String.format("%d:%02d", sec / 60, sec % 60), t.accent())));
        }
        if (plugin.combat().isTagged(id)) {
            p.sendActionBar(t.text("in combat ", t.secondary())
                    .append(Component.text(plugin.combat().secondsLeft(id) + "s", t.accent())));
        }
    }

    public void update(Player p) {
        Theme t = plugin.theme();
        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());

        // fresh scoreboard each tick keeps this simple and flicker-free enough at 1hz
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("mint", Criteria.DUMMY, t.text(t.serverName(), t.primary()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());

        List<Component> lines = List.of(
                Component.text(" "),
                t.line("kills", String.valueOf(s.kills)),
                t.line("deaths", String.valueOf(s.deaths)),
                t.line("k/d", String.format(Locale.ROOT, "%.2f", s.kd())),
                t.line("streak", String.valueOf(s.streak)),
                t.line("best", String.valueOf(s.bestStreak)),
                Component.text("  "),
                t.text("online: ", t.secondary())
                        .append(Component.text(String.valueOf(Bukkit.getOnlinePlayers().size()), t.accent()))
        );

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            // unique invisible entry per line; the visible text is the custom name
            String entry = "§" + Integer.toHexString(i);
            var line = obj.getScore(entry);
            line.customName(lines.get(i));
            line.setScore(score--);
        }
        p.setScoreboard(board);
    }
}
