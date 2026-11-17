package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** player stats */
public final class StatsManager {

    public static final class Stats {
        public int kills, deaths, streak, bestStreak, duelWins, duelLosses, rankedWins, rankedLosses;
        /** tier per kit */
        public final Map<String, Integer> kitTier = new HashMap<>();
        /** promotion progress */
        public final Map<String, Integer> kitProgress = new HashMap<>();
        /** ranked streak */
        public int rankedStreak;

        public int rankedGames() {
            return rankedWins + rankedLosses;
        }

        /** tier index */
        public int tierIndex(String kitId) {
            return kitTier.getOrDefault(kitId, -1);
        }

        /** total points */
        public int score() {
            int sum = 0;
            for (int o : kitTier.values()) sum += dev.mintpvp.util.Tier.at(o) == null ? 0 : dev.mintpvp.util.Tier.at(o).points;
            return sum;
        }

        /** best tier */
        public int bestTier() {
            int best = -1;
            for (int o : kitTier.values()) best = Math.max(best, o);
            return best;
        }

        public double kd() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

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
                var kt = section.getConfigurationSection(key + ".kit-tier");
                if (kt != null) for (String k : kt.getKeys(false)) s.kitTier.put(k, Math.max(0, Math.min(dev.mintpvp.util.Tier.values().length - 1, kt.getInt(k))));
                var kp = section.getConfigurationSection(key + ".kit-progress");
                if (kp != null) for (String k : kp.getKeys(false)) s.kitProgress.put(k, kp.getInt(k));
                s.rankedWins = section.getInt(key + ".ranked-wins");
                s.rankedLosses = section.getInt(key + ".ranked-losses");
                s.rankedStreak = section.getInt(key + ".ranked-streak");
                stats.put(id, s);
                names.put(id, section.getString(key + ".name", "unknown"));
            } catch (IllegalArgumentException ignored) {
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
            s.kitTier.forEach((k, v) -> y.set(p + ".kit-tier." + k, v));
            s.kitProgress.forEach((k, v) -> { if (v != 0) y.set(p + ".kit-progress." + k, v); });
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

    /** new streak */
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

    /** tier change */
    public record TierChange(String kitId, int oldTier, int newTier, int progress, int scoreDelta, int score, boolean placement) {}

    /** apply ranked result */
    public TierChange[] recordRanked(UUID winner, UUID loser, boolean draw, String kitId) {
        Stats sw = get(winner), sl = get(loser);
        int tw = sw.tierIndex(kitId), tl = sl.tierIndex(kitId);
        int scoreW = sw.score(), scoreL = sl.score();
        boolean placeW = tw < 0, placeL = tl < 0;
        if (draw) {
            return new TierChange[] {
                    new TierChange(kitId, tw, tw, sw.kitProgress.getOrDefault(kitId, 0), 0, scoreW, false),
                    new TierChange(kitId, tl, tl, sl.kitProgress.getOrDefault(kitId, 0), 0, scoreL, false)};
        }
        var c = plugin.getConfig();
        int promote = Math.max(1, c.getInt("ranked.wins-to-promote", 2));
        int demote = Math.max(1, c.getInt("ranked.losses-to-demote", 2));
        int streakAt = c.getInt("ranked.streak-bonus-at", 3);
        int top = dev.mintpvp.util.Tier.values().length - 1;

        sw.rankedWins++;
        sl.rankedLosses++;
        sw.rankedStreak = Math.max(0, sw.rankedStreak) + 1;
        sl.rankedStreak = Math.min(0, sl.rankedStreak) - 1;

        if (placeW) {
            sw.kitTier.put(kitId, 0);
            sw.kitProgress.put(kitId, 0);
        } else if (tl >= tw) {
            int gain = tl >= tw + 2 ? 2 : 1;
            if (streakAt > 0 && sw.rankedStreak >= streakAt) gain++;
            int prog = sw.kitProgress.getOrDefault(kitId, 0) + gain;
            if (prog >= promote && tw < top) {
                sw.kitTier.put(kitId, tw + 1);
                prog = 0;
            } else if (prog >= promote) {
                prog = 0;
            }
            sw.kitProgress.put(kitId, prog);
        }
        if (placeL) {
            sl.kitTier.put(kitId, 0);
            sl.kitProgress.put(kitId, 0);
        } else if (tw <= tl) {
            int prog = sl.kitProgress.getOrDefault(kitId, 0) - 1;
            if (prog <= -demote) {
                if (tl > 0) sl.kitTier.put(kitId, tl - 1);
                prog = 0;
            }
            sl.kitProgress.put(kitId, prog);
        }
        return new TierChange[] {
                new TierChange(kitId, tw, sw.tierIndex(kitId), sw.kitProgress.getOrDefault(kitId, 0), sw.score() - scoreW, sw.score(), placeW),
                new TierChange(kitId, tl, sl.tierIndex(kitId), sl.kitProgress.getOrDefault(kitId, 0), sl.score() - scoreL, sl.score(), placeL)};
    }

    /** admin set tier */
    public void setTier(UUID id, String kitId, int tierIndex) {
        Stats s = get(id);
        if (tierIndex < 0) s.kitTier.remove(kitId);
        else s.kitTier.put(kitId, Math.min(tierIndex, dev.mintpvp.util.Tier.values().length - 1));
        s.kitProgress.remove(kitId);
        save();
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
