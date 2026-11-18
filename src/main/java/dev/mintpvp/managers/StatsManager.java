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

    private Stats read(org.bukkit.configuration.ConfigurationSection section) {
        Stats s = new Stats();
        s.kills = section.getInt("kills");
        s.deaths = section.getInt("deaths");
        s.bestStreak = section.getInt("best-streak");
        s.duelWins = section.getInt("duel-wins");
        s.duelLosses = section.getInt("duel-losses");
        var kt = section.getConfigurationSection("kit-tier");
        if (kt != null) for (String k : kt.getKeys(false)) s.kitTier.put(k, Math.max(0, Math.min(dev.mintpvp.util.Tier.values().length - 1, kt.getInt(k))));
        var kp = section.getConfigurationSection("kit-progress");
        if (kp != null) for (String k : kp.getKeys(false)) s.kitProgress.put(k, kp.getInt(k));
        s.rankedWins = section.getInt("ranked-wins");
        s.rankedLosses = section.getInt("ranked-losses");
        s.rankedStreak = section.getInt("ranked-streak");
        return s;
    }

    private void write(org.bukkit.configuration.ConfigurationSection y, Stats s) {
        y.set("kills", s.kills);
        y.set("deaths", s.deaths);
        y.set("best-streak", s.bestStreak);
        y.set("duel-wins", s.duelWins);
        y.set("duel-losses", s.duelLosses);
        s.kitTier.forEach((k, v) -> y.set("kit-tier." + k, v));
        s.kitProgress.forEach((k, v) -> { if (v != 0) y.set("kit-progress." + k, v); });
        y.set("ranked-wins", s.rankedWins);
        y.set("ranked-losses", s.rankedLosses);
        y.set("ranked-streak", s.rankedStreak);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var section = y.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                stats.put(id, read(section.getConfigurationSection(key)));
                names.put(id, section.getString(key + ".name", "unknown"));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (var en : new ArrayList<>(stats.entrySet())) {
            var sec = y.createSection("players." + en.getKey());
            sec.set("name", names.getOrDefault(en.getKey(), "unknown"));
            write(sec, en.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
        }
    }

    // shared database sync

    private StatsDb db;
    private volatile String dbError = "";
    private volatile long lastWriteAt, lastPullAt;
    private volatile int dbPlayers;
    private final java.util.Set<UUID> dirty = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long lastPull;

    private String encode(UUID id) {
        YamlConfiguration y = new YamlConfiguration();
        write(y, get(id));
        return y.saveToString();
    }

    private Stats decode(String data) throws org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(data);
        return read(y);
    }

    private void touch(UUID id) {
        if (db != null) dirty.add(id);
    }

    /** start the shared database (storage.type: mysql) */
    public void start() {
        var cfg = plugin.getConfig();
        if (!cfg.getString("storage.type", "yaml").equalsIgnoreCase("mysql")) return;
        String url = cfg.getString("storage.jdbc-url", "");
        String user = cfg.getString("storage.mysql.user", "");
        String pass = cfg.getString("storage.mysql.password", "");
        if (url.isEmpty()) {
            url = "jdbc:mysql://" + cfg.getString("storage.mysql.host", "localhost") + ":" + cfg.getInt("storage.mysql.port", 3306)
                    + "/" + cfg.getString("storage.mysql.database", "mintpvp")
                    + "?sslMode=PREFERRED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=15000&characterEncoding=utf8";
        }
        StatsDb d = new StatsDb(url, user, pass);
        try {
            d.connect();
            var rows = d.fetch(0);
            java.util.Set<UUID> inDb = new java.util.HashSet<>();
            for (var r : rows) {
                inDb.add(r.id());
                applyRow(r);
            }
            db = d;
            dbPlayers = rows.size();
            lastPullAt = System.currentTimeMillis();
            for (UUID id : new ArrayList<>(stats.keySet())) if (!inDb.contains(id)) dirty.add(id); // upload local-only players
            flush(true);
            plugin.getLogger().info("Shared stats database connected (" + rows.size() + " players).");
        } catch (Exception e) {
            dbError = String.valueOf(e.getMessage());
            plugin.getLogger().severe("Could not connect to the stats database, using local stats.yml only: " + e.getMessage());
            d.close();
            return;
        }
        var sched = plugin.getServer().getScheduler();
        sched.runTaskTimer(plugin, () -> flush(false), 200L, 200L);
        long pullTicks = Math.max(5, cfg.getInt("storage.pull-seconds", 60)) * 20L;
        sched.runTaskTimerAsynchronously(plugin, this::pull, pullTicks, pullTicks);
    }

    private void applyRow(StatsDb.Row r) {
        if (dirty.contains(r.id())) return;
        try {
            Stats parsed = decode(r.data());
            Stats cur = stats.computeIfAbsent(r.id(), k -> new Stats());
            cur.kills = parsed.kills;
            cur.deaths = parsed.deaths;
            cur.bestStreak = parsed.bestStreak;
            cur.duelWins = parsed.duelWins;
            cur.duelLosses = parsed.duelLosses;
            cur.rankedWins = parsed.rankedWins;
            cur.rankedLosses = parsed.rankedLosses;
            cur.rankedStreak = parsed.rankedStreak;
            cur.kitTier.clear();
            cur.kitTier.putAll(parsed.kitTier);
            cur.kitProgress.clear();
            cur.kitProgress.putAll(parsed.kitProgress);
            if (r.name() != null) names.put(r.id(), r.name());
            lastPull = Math.max(lastPull, r.updated());
        } catch (Exception e) {
            plugin.getLogger().warning("Skipped a bad stats row for " + r.id() + ": " + e.getMessage());
        }
    }

    /** pull recent changes from the database (async), then apply them on the main thread */
    private void pull() {
        StatsDb d = db;
        if (d == null) return;
        try {
            var rows = d.fetch(Math.max(0, lastPull - 60_000L));
            lastPullAt = System.currentTimeMillis();
            plugin.getServer().getScheduler().runTask(plugin, () -> rows.forEach(this::applyRow));
        } catch (Exception e) {
            dbError = String.valueOf(e.getMessage());
            plugin.getLogger().warning("Stats pull failed: " + e.getMessage());
        }
    }

    /** write changed players to the database; sync = on this thread */
    private void flush(boolean sync) {
        StatsDb d = db;
        if (d == null || dirty.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Object[]> batch = new ArrayList<>();
        for (UUID id : new ArrayList<>(dirty)) {
            dirty.remove(id);
            batch.add(new Object[] {id, names.getOrDefault(id, "unknown"), encode(id)});
        }
        Runnable write = () -> {
            for (Object[] b : batch) {
                try {
                    d.upsert((UUID) b[0], (String) b[1], (String) b[2], now);
                    lastWriteAt = now;
                } catch (Exception e) {
                    dirty.add((UUID) b[0]);
                    dbError = String.valueOf(e.getMessage());
                    plugin.getLogger().warning("Stats write failed: " + e.getMessage());
                }
            }
        };
        if (sync || !plugin.isEnabled()) write.run();
        else plugin.getServer().getScheduler().runTaskAsynchronously(plugin, write);
    }

    /** load one player's latest stats when they join (the other server may have just saved them) */
    public void syncJoin(UUID id) {
        StatsDb d = db;
        if (d == null) return;
        for (long delay : new long[] {10L, 100L}) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    var row = d.fetchOne(id);
                    if (row != null) plugin.getServer().getScheduler().runTask(plugin, () -> applyRow(row));
                } catch (Exception e) {
                    plugin.getLogger().warning("Stats load failed: " + e.getMessage());
                }
            }, delay);
        }
    }

    /** status lines for /mintpvp db */
    public List<String> dbStatus() {
        List<String> out = new ArrayList<>();
        boolean wanted = plugin.getConfig().getString("storage.type", "yaml").equalsIgnoreCase("mysql");
        if (!wanted) {
            out.add("storage: local file only (storage.type is not mysql)");
            return out;
        }
        out.add("storage: shared database " + (db != null ? "connected" : "NOT connected"));
        if (db != null) {
            long now = System.currentTimeMillis();
            out.add("players in database at start: " + dbPlayers + ", waiting to save: " + dirty.size());
            out.add("last save: " + (lastWriteAt == 0 ? "none yet" : (now - lastWriteAt) / 1000 + "s ago"));
            out.add("last refresh: " + (lastPullAt == 0 ? "none yet" : (now - lastPullAt) / 1000 + "s ago"));
        }
        if (!dbError.isEmpty()) out.add("last error: " + dbError);
        return out;
    }

    /** save a leaving player right away so the other server sees it */
    public void flushPlayer(UUID id) {
        StatsDb d = db;
        if (d == null) return;
        dirty.remove(id);
        try {
            d.upsert(id, names.getOrDefault(id, "unknown"), encode(id), System.currentTimeMillis());
        } catch (Exception e) {
            dirty.add(id);
            plugin.getLogger().warning("Stats write failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        flush(true);
        if (db != null) db.close();
        db = null;
    }

    public Stats get(UUID id) {
        return stats.computeIfAbsent(id, k -> new Stats());
    }

    public void rememberName(UUID id, String name) {
        touch(id);
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
        touch(killer);
        touch(victim);
        Stats k = get(killer);
        k.kills++;
        k.streak++;
        k.bestStreak = Math.max(k.bestStreak, k.streak);
        recordDeath(victim);
        return k.streak;
    }

    public void addDuelWin(UUID id) {
        touch(id);
        get(id).duelWins++;
    }

    public void addDuelLoss(UUID id) {
        touch(id);
        get(id).duelLosses++;
    }

    public void recordDuel(UUID winner, UUID loser) {
        touch(winner);
        touch(loser);
        get(winner).duelWins++;
        get(loser).duelLosses++;
    }

    /** tier change */
    /** Progress is counted in half wins: a full win is UNITS. */
    public static final int UNITS = 2;

    /** "1.5 / 2" for progress units out of a number of wins. */
    public static String progressText(int units, int wins) {
        double v = Math.abs(units) / (double) UNITS;
        return (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + " / " + wins;
    }

    public record TierChange(String kitId, int oldTier, int newTier, int progress, int scoreDelta, int score, boolean placement) {}

    /** apply ranked result */
    public TierChange[] recordRanked(UUID winner, UUID loser, boolean draw, String kitId) {
        touch(winner);
        touch(loser);
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
        int promote = Math.max(1, c.getInt("ranked.promote-wins", 2)) * UNITS;
        int demote = Math.max(1, c.getInt("ranked.demote-losses", 3)) * UNITS;
        int streakAt = c.getInt("ranked.streak-bonus-from", 2);
        int top = dev.mintpvp.util.Tier.values().length - 1;

        sw.rankedWins++;
        sl.rankedLosses++;
        sw.rankedStreak = Math.max(0, sw.rankedStreak) + 1;
        sl.rankedStreak = Math.min(0, sl.rankedStreak) - 1;

        if (placeW) {
            sw.kitTier.put(kitId, 0);
            sw.kitProgress.put(kitId, 0);
        } else {
            // a win over your tier or higher is a full win (two over a much higher one); over a lower tier it is half a win
            int gain = tl >= tw + 2 ? 2 * UNITS : tl >= tw ? UNITS : 1;
            if (streakAt > 0 && sw.rankedStreak >= streakAt) gain += UNITS;
            int prog = sw.kitProgress.getOrDefault(kitId, 0) + gain;
            int tier = tw;
            while (prog >= promote && tier < top) { // extra progress carries into the next tier
                tier++;
                prog -= promote;
            }
            if (tier >= top) prog = 0;
            if (tier != tw) sw.kitTier.put(kitId, tier);
            sw.kitProgress.put(kitId, prog);
        }
        if (placeL) {
            sl.kitTier.put(kitId, 0);
            sl.kitProgress.put(kitId, 0);
        } else if (tw <= tl) {
            int prog = sl.kitProgress.getOrDefault(kitId, 0) - UNITS;
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
        touch(id);
        Stats s = get(id);
        if (tierIndex < 0) s.kitTier.remove(kitId);
        else s.kitTier.put(kitId, Math.min(tierIndex, dev.mintpvp.util.Tier.values().length - 1));
        s.kitProgress.remove(kitId);
        save();
    }

    public void recordDeath(UUID victim) {
        touch(victim);
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
