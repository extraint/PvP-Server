package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Theme;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** sidebar */
public final class ScoreboardManager {

    private final MintPvP plugin;
    private BukkitTask task;
    private final java.util.Map<UUID, Scoreboard> boards = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> lineCount = new java.util.HashMap<>();

    public ScoreboardManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            lineCount.keySet().removeIf(id -> !boards.containsKey(id));
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    update(p);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Scoreboard update failed: " + e);
                }
                actionBar(p);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
        lineCount.clear();
    }

    /** a player left */
    public void forget(UUID id) {
        boards.remove(id);
        lineCount.remove(id);
    }

    private void actionBar(Player p) {
        Theme t = plugin.theme();
        UUID id = p.getUniqueId();
        var q = plugin.queue().entryOf(id);
        if (q != null && !plugin.combat().isTagged(id)) {
            long sec = (System.currentTimeMillis() - q.since()) / 1000;
            Component bar = t.text("queued · " + (q.type() == dev.mintpvp.managers.QueueManager.Type.RANKED ? "ranked · " : "")
                    + q.kit().display + " · ", t.secondary())
                    .append(Component.text(String.format("%d:%02d", sec / 60, sec % 60), t.accent()));
            if (q.type() == dev.mintpvp.managers.QueueManager.Type.RANKED) {
                bar = bar.append(t.text(" · ±" + plugin.queue().window(q) + " tiers", t.muted()));
            }
            p.sendActionBar(bar);
        }
        if (plugin.combat().isTagged(id)) {
            p.sendActionBar(t.text("in combat ", t.secondary())
                    .append(Component.text(plugin.combat().secondsLeft(id) + "s", t.accent())));
        }
    }

    /**
     * Tier tags in front of every name (nametags and tab list) through scoreboard teams: in a match it is each
     * player's tier in that kit, elsewhere their best tier. In a party team match names are also red or blue.
     * The board is kept between updates and only the players whose team changed are moved.
     */
    private void tagPlayers(Scoreboard board, DuelManager.Duel match) {
        boolean teams = match != null && match.mode == DuelManager.Mode.PARTY;
        String kitId = match != null ? match.kit.id : null;
        int crown = dev.mintpvp.util.Tier.values().length - 1;
        java.util.Set<String> online = new java.util.HashSet<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            var st = plugin.stats().get(o.getUniqueId());
            int idx = kitId != null ? st.tierIndex(kitId) : st.bestTier();
            NamedTextColor color = null;
            String ck = "n";
            if (teams && match.players.contains(o)) {
                boolean blue = match.teamOf(o) == 1;
                color = blue ? NamedTextColor.BLUE : NamedTextColor.RED;
                ck = blue ? "b" : "r";
            }
            String name = "m" + ck + idx;
            var want = board.getTeam(name);
            if (want == null) {
                want = board.registerNewTeam(name);
                if (idx >= 0) {
                    String label = (idx == crown ? "★" : "") + dev.mintpvp.util.Tier.name(idx).toUpperCase(Locale.ROOT);
                    want.prefix(Component.text(label + " ", dev.mintpvp.util.Tier.at(idx).color)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                }
                if (color != null) want.color(color);
            }
            var cur = board.getEntryTeam(o.getName());
            if (cur != want) {
                if (cur != null) cur.removeEntry(o.getName());
                want.addEntry(o.getName());
            }
            online.add(o.getName());
        }
        for (var tm : new ArrayList<>(board.getTeams())) {
            if (!tm.getName().startsWith("m")) continue;
            for (String e : new java.util.HashSet<>(tm.getEntries())) if (!online.contains(e)) tm.removeEntry(e);
            if (tm.getSize() == 0) tm.unregister();
        }
    }

    public void update(Player p) {
        Theme t = plugin.theme();
        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());

        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(), k -> {
            Scoreboard nb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = nb.registerNewObjective("mint", Criteria.DUMMY, t.text(t.serverName(), t.primary()));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            return nb;
        });
        Objective obj = board.getObjective("mint");
        obj.displayName(t.text(t.serverName(), t.primary()));

        var match = plugin.duels().duelOf(p.getUniqueId());
        if (match == null) match = plugin.duels().spectatedDuel(p.getUniqueId());
        boolean teams = match != null && match.mode == DuelManager.Mode.PARTY;
        if (plugin.getConfig().getBoolean("tags.teams", true)) {
            try {
                tagPlayers(board, match);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Tier tags failed: " + e);
            }
        }

        List<Component> lines = new ArrayList<>(List.of(
                Component.text(" "),
                t.line("tier", dev.mintpvp.util.Tier.overallLabel(s.score())),
                t.line("kills", String.valueOf(s.kills)),
                t.line("deaths", String.valueOf(s.deaths)),
                t.line("k/d", String.format(Locale.ROOT, "%.2f", s.kd())),
                t.line("streak", String.valueOf(s.streak)),
                t.line("best", String.valueOf(s.bestStreak)),
                Component.text("  "),
                t.text("online: ", t.secondary())
                        .append(Component.text(String.valueOf(Bukkit.getOnlinePlayers().size()), t.accent()))
        ));
        if (teams && match.teamOf(p) >= 0) {
            boolean blue = match.teamOf(p) == 1;
            lines.add(1, t.text("team: ", t.secondary()).append(Component.text(blue ? "blue" : "red", blue ? NamedTextColor.BLUE : NamedTextColor.RED)));
        }

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String entry = "§" + Integer.toHexString(i);
            var line = obj.getScore(entry);
            line.customName(lines.get(i));
            line.setScore(score--);
        }
        int prev = lineCount.getOrDefault(p.getUniqueId(), 0);
        for (int i = lines.size(); i < prev; i++) board.resetScores("§" + Integer.toHexString(i));
        lineCount.put(p.getUniqueId(), lines.size());
        if (p.getScoreboard() != board) p.setScoreboard(board);
    }
}
