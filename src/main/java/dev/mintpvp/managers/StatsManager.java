package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Persistent kills / deaths / streak tracking, stored in stats.yml. */
public final class StatsManager {

    public static final class Stats {
        public int kills, deaths, streak, bestStreak, duelWins, duelLosses;

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
