package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Persistent kills / deaths / streak tracking, stored in stats.yml. */
public final class StatsManager {

    public static final class Stats {
        public int kills, deaths, streak, bestStreak, duelWins, duelLosses, rankedWins, rankedLosses;
        public int elo = START_ELO;
        /** Ranked win streak (positive) or loss streak (negative). */
        public int rankedStreak;

        public int rankedGames() {
            return rankedWins + rankedLosses;
        }

        public double kd() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

    public static final int START_ELO = 1000;

    private final MintPvP plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public StatsManager(MintPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var section = y.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Stats s = new Stats();
                s.kills = section.getInt(key + ".kills");
                s.deaths = section.getInt(key + ".deaths");
                s.bestStreak = section.getInt(key + ".best-streak");
                s.duelWins = section.getInt(key + ".duel-wins");
                s.duelLosses = section.getInt(key + ".duel-losses");
                s.elo = section.getInt(key + ".elo", START_ELO);
                s.rankedWins = section.getInt(key + ".ranked-wins");
                s.rankedLosses = section.getInt(key + ".ranked-losses");
                s.rankedStreak = section.getInt(key + ".ranked-streak");
                stats.put(id, s);
                names.put(id, section.getString(key + ".name", "unknown"));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        stats.forEach((id, s) -> {
            String p = "players." + id;
            y.set(p + ".name", names.getOrDefault(id, "unknown"));
            y.set(p + ".kills", s.kills);
            y.set(p + ".deaths", s.deaths);
            y.set(p + ".best-streak", s.bestStreak);
            y.set(p + ".duel-wins", s.duelWins);
            y.set(p + ".duel-losses", s.duelLosses);
            y.set(p + ".elo", s.elo);
            y.set(p + ".ranked-wins", s.rankedWins);
            y.set(p + ".ranked-losses", s.rankedLosses);
            y.set(p + ".ranked-streak", s.rankedStreak);
        });
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
        }
    }

    public Stats get(UUID id) {
        return stats.computeIfAbsent(id, k -> new Stats());
    }

    public void rememberName(UUID id, String name) {
        names.put(id, name);
    }

    public String nameOf(UUID id) {
        return names.getOrDefault(id, "unknown");
    }

    public Optional<UUID> findByName(String name) {
        return names.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst();
    }

    /** @return the killer's new streak. */
    public int recordKill(UUID killer, UUID victim) {
        Stats k = get(killer);
        k.kills++;
        k.streak++;
        k.bestStreak = Math.max(k.bestStreak, k.streak);
        recordDeath(victim);
        return k.streak;
    }

    public void addDuelWin(UUID id) {
        get(id).duelWins++;
    }

    public void addDuelLoss(UUID id) {
        get(id).duelLosses++;
    }

    public void recordDuel(UUID winner, UUID loser) {
        get(winner).duelWins++;
        get(loser).duelLosses++;
    }

    /** One player's rating change from a ranked result. */
    public record EloChange(int delta, int k, int streakBonusPercent, boolean provisional) {}

    /**
     * The K-factor for a player: how many points a result is worth. New players swing a lot while the
     * system finds their level; established ones move slower; the very top is steadier still.
     */
    public int kFor(Stats s, int baseK) {
        var c = plugin.getConfig();
        int games = s.rankedGames();
        if (games < c.getInt("ranked.provisional-games", 10)) return c.getInt("ranked.k-provisional", 64);
        if (games < c.getInt("ranked.settling-games", 30)) return c.getInt("ranked.k-settling", 40);
        if (s.elo >= c.getInt("ranked.top-elo", 1900)) return c.getInt("ranked.k-top", 20);
        return baseK;
    }

    /**
     * Applies an adaptive Elo result: each player's change uses their own K-factor (so a newcomer
     * beating a veteran can gain a lot while the veteran loses less), and a winner on a streak of 2+
     * wins gets a bonus (+10% per extra win, up to +50%). A win is always at least +1, a loss at least -1,
     * and ratings never go below 0.
     * @param a the winner (or either player for a draw)
     * @return the changes {a, b}
     */
    public EloChange[] recordRanked(UUID a, UUID b, boolean draw, int baseK) {
        Stats sa = get(a), sb = get(b);
        boolean provA = sa.rankedGames() < plugin.getConfig().getInt("ranked.provisional-games", 10);
        boolean provB = sb.rankedGames() < plugin.getConfig().getInt("ranked.provisional-games", 10);
        int ka = kFor(sa, baseK), kb = kFor(sb, baseK);
        double expectedA = 1.0 / (1.0 + Math.pow(10, (sb.elo - sa.elo) / 400.0));
        double scoreA = draw ? 0.5 : 1.0;
        int da = (int) Math.round(ka * (scoreA - expectedA));
        int db = (int) Math.round(kb * ((1 - scoreA) - (1 - expectedA)));
        int bonus = 0;
        if (!draw) {
            bonus = Math.min(50, 10 * Math.max(0, sa.rankedStreak - 1));
            da = (int) Math.round(da * (1 + bonus / 100.0));
            if (da < 1) da = 1;
            if (db > -1) db = -1;
        }
        int oldA = sa.elo, oldB = sb.elo;
        sa.elo = Math.max(0, sa.elo + da);
        sb.elo = Math.max(0, sb.elo + db);
        if (draw) {
            sa.rankedStreak = 0;
            sb.rankedStreak = 0;
        } else {
            sa.rankedWins++;
            sb.rankedLosses++;
            sa.rankedStreak = Math.max(0, sa.rankedStreak) + 1;
            sb.rankedStreak = Math.min(0, sb.rankedStreak) - 1;
        }
        return new EloChange[] {
                new EloChange(sa.elo - oldA, ka, bonus, provA),
                new EloChange(sb.elo - oldB, kb, 0, provB)};
    }

    public void recordDeath(UUID victim) {
        Stats v = get(victim);
        v.deaths++;
        v.streak = 0;
    }

    public List<Map.Entry<UUID, Stats>> top(Comparator<Stats> by, int limit) {
        return stats.entrySet().stream()
                .sorted((a, b) -> by.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .toList();
    }
}
