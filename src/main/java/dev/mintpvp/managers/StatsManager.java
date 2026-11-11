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

    public void recordDuel(UUID winner, UUID loser) {
        get(winner).duelWins++;
        get(loser).duelLosses++;
    }

    /**
     * Applies an Elo result. A win is always worth at least +1; ratings never drop below 0.
     * @param draw true for a draw (a and b are then interchangeable)
     * @return the actual rating changes {a, b}
     */
    public int[] recordRanked(UUID a, UUID b, boolean draw, int k) {
        Stats sa = get(a), sb = get(b);
        double expectedA = 1.0 / (1.0 + Math.pow(10, (sb.elo - sa.elo) / 400.0));
        double scoreA = draw ? 0.5 : 1.0;
        int delta = (int) Math.round(k * (scoreA - expectedA));
        if (!draw && delta < 1) delta = 1;
        int oldA = sa.elo, oldB = sb.elo;
        sa.elo = Math.max(0, sa.elo + delta);
        sb.elo = Math.max(0, sb.elo - delta);
        if (!draw) {
            sa.rankedWins++;
            sb.rankedLosses++;
        }
        return new int[] {sa.elo - oldA, sb.elo - oldB};
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
