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
        /** last ranked match per kit (ms) */
        public final Map<String, Long> kitLast = new HashMap<>();
        /** highest tier reached per kit */
        public final Map<String, Integer> kitPeak = new HashMap<>();
        /** last tier test per kit (ms) */
        public final Map<String, Long> kitTest = new HashMap<>();
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
        var kl = section.getConfigurationSection("kit-last");
        if (kl != null) for (String k : kl.getKeys(false)) s.kitLast.put(k, kl.getLong(k));
        var kx = section.getConfigurationSection("kit-test");
        if (kx != null) for (String k : kx.getKeys(false)) s.kitTest.put(k, kx.getLong(k));
        var kk = section.getConfigurationSection("kit-peak");
        if (kk != null) for (String k : kk.getKeys(false)) s.kitPeak.put(k, kk.getInt(k));
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
        s.kitLast.forEach((k, v) -> y.set("kit-last." + k, v));
        s.kitPeak.forEach((k, v) -> y.set("kit-peak." + k, v));
        s.kitTest.forEach((k, v) -> y.set("kit-test." + k, v));
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
        if (plugin.aboveTags() != null) plugin.aboveTags().refresh(id);
        if (plugin.tab() != null) plugin.tab().refresh(id);
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
            cur.kitLast.clear();
            cur.kitLast.putAll(parsed.kitLast);
            cur.kitPeak.clear();
            cur.kitPeak.putAll(parsed.kitPeak);
            cur.kitTest.clear();
            cur.kitTest.putAll(parsed.kitTest);
            if (r.name() != null) names.put(r.id(), r.name());
            if (plugin.aboveTags() != null) plugin.aboveTags().refresh(r.id());
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

    /** read only: the stats if known, without creating an entry (safe from other threads) */
    public Stats peek(UUID id) {
        return stats.get(id);
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
    /** progress is counted in half wins */
    public static final int UNITS = RankedRules.UNITS;

    /** "1.5 / 2" for progress units out of a number of wins. */
    public static String progressText(int units, int wins) {
        double v = Math.abs(units) / (double) UNITS;
        return (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + " / " + wins;
    }

    public record TierChange(String kitId, int oldTier, int newTier, int progress, int scoreDelta, int score, boolean placement, String note) {}

    private RankedRules.Cfg rankedCfg() {
        var c = plugin.getConfig();
        return new RankedRules.Cfg(Math.max(1, c.getInt("ranked.promote-wins", 2)), Math.max(1, c.getInt("ranked.demote-losses", 3)),
                c.getInt("ranked.streak-bonus-from", 2), eliteFrom(), c.getBoolean("ranked.tier-scaling", true));
    }

    private int tierByName(String name, int fallback) {
        if (name != null) for (var t : dev.mintpvp.util.Tier.values()) if (t.label.equalsIgnoreCase(name.trim())) return t.ordinal();
        return fallback;
    }

    /** first elite tier (default ht3) */
    public int eliteFrom() {
        return tierByName(plugin.getConfig().getString("ranked.elite-from", "ht3"), 5);
    }

    /** wins needed to leave a tier */
    public int promoteWinsFor(int tier) {
        return RankedRules.promoteNeed(rankedCfg(), tier) / UNITS;
    }

    /** losses that drop you from a tier */
    public int demoteLossesFor(int tier) {
        return RankedRules.demoteNeed(rankedCfg(), tier) / UNITS;
    }

    /** apply ranked result; winnerRounds/loserRounds are the round scores of a finished match */
    public TierChange[] recordRanked(UUID winner, UUID loser, boolean draw, String kitId, int winnerRounds, int loserRounds, int firstTo) {
        touch(winner);
        touch(loser);
        Stats sw = get(winner), sl = get(loser);
        int tw = sw.tierIndex(kitId), tl = sl.tierIndex(kitId);
        int scoreW = sw.score(), scoreL = sl.score();
        boolean placeW = tw < 0, placeL = tl < 0;
        if (draw) {
            return new TierChange[] {
                    new TierChange(kitId, tw, tw, sw.kitProgress.getOrDefault(kitId, 0), 0, scoreW, false, ""),
                    new TierChange(kitId, tl, tl, sl.kitProgress.getOrDefault(kitId, 0), 0, scoreL, false, "")};
        }
        var cfg = rankedCfg();
        int top = dev.mintpvp.util.Tier.values().length - 1;
        long now = System.currentTimeMillis();
        sl.kitLast.put(kitId, now);
        sw.kitLast.put(kitId, now);
        boolean complete = firstTo > 1 && winnerRounds >= firstTo;
        boolean flawless = complete && loserRounds == 0;
        boolean close = complete && loserRounds >= firstTo - 1;

        sw.rankedWins++;
        sl.rankedLosses++;
        sw.rankedStreak = Math.max(0, sw.rankedStreak) + 1;
        sl.rankedStreak = Math.min(0, sl.rankedStreak) - 1;

        String noteW = "", noteL = "";
        boolean crownTaken = false;
        if (placeW) {
            sw.kitTier.put(kitId, 0);
            sw.kitProgress.put(kitId, 0);
        } else if (tw == top - 1 && tl == top) {
            // beating the HT1 holder takes the crown: there is only one HT1 per kit
            sw.kitTier.put(kitId, top);
            sw.kitProgress.put(kitId, 0);
            sl.kitTier.put(kitId, top - 1);
            sl.kitProgress.put(kitId, 0);
            noteW = "you took the HT1 crown";
            noteL = "you lost the HT1 crown";
            crownTaken = true;
        } else {
            var g = RankedRules.winGain(cfg, tw, tl, sw.rankedStreak, flawless);
            noteW = g.note();
            int prog = sw.kitProgress.getOrDefault(kitId, 0) + g.units();
            int tier = tw;
            while (tier < top && prog >= RankedRules.promoteNeed(cfg, tier)) { // extra progress carries into the next tier
                if (tier == top - 1) {
                    UUID king = crownHolder(kitId);
                    if (king != null && !king.equals(winner)) { // HT1 is taken: beat the holder instead
                        prog = Math.min(prog, RankedRules.promoteNeed(cfg, tier) - 1);
                        noteW = "HT1 is held by " + nameOf(king) + ", beat them to take it";
                        break;
                    }
                }
                prog -= RankedRules.promoteNeed(cfg, tier);
                tier++;
            }
            if (tier >= top) prog = 0;
            if (tier != tw) sw.kitTier.put(kitId, tier);
            sw.kitProgress.put(kitId, prog);
        }
        if (placeL) {
            sl.kitTier.put(kitId, 0);
            sl.kitProgress.put(kitId, 0);
        } else if (!crownTaken) {
            var p = RankedRules.lossPenalty(cfg, tw, tl, close);
            noteL = p.note();
            if (p.units() > 0) {
                int prog = sl.kitProgress.getOrDefault(kitId, 0) - p.units();
                if (prog <= -RankedRules.demoteNeed(cfg, tl)) {
                    if (tl > 0) sl.kitTier.put(kitId, tl - 1);
                    prog = 0;
                }
                sl.kitProgress.put(kitId, prog);
            }
        }
        sw.kitPeak.merge(kitId, sw.tierIndex(kitId), Math::max);
        sl.kitPeak.merge(kitId, sl.tierIndex(kitId), Math::max);
        return new TierChange[] {
                new TierChange(kitId, tw, sw.tierIndex(kitId), sw.kitProgress.getOrDefault(kitId, 0), sw.score() - scoreW, sw.score(), placeW, noteW),
                new TierChange(kitId, tl, sl.tierIndex(kitId), sl.kitProgress.getOrDefault(kitId, 0), sl.score() - scoreL, sl.score(), placeL, noteL)};
    }

    /** who holds HT1 in a kit, or null (there is only ever one) */
    public UUID crownHolder(String kitId) {
        int top = dev.mintpvp.util.Tier.values().length - 1;
        for (var en : stats.entrySet()) if (en.getValue().tierIndex(kitId) == top) return en.getKey();
        return null;
    }

    /** minutes until this player can take another tier test in a kit (0 = now) */
    public long testCooldownMinutes(UUID id, String kitId) {
        Long last = get(id).kitTest.get(kitId);
        if (last == null) return 0;
        long cool = plugin.getConfig().getInt("test.cooldown-minutes", 30);
        long left = cool - (System.currentTimeMillis() - last) / 60_000L;
        return Math.max(0, left);
    }

    /**
     * An instant tier test result (no ranked matches needed). The winner moves up one tier if they win by 2+ rounds
     * (4-2 or better); nobody loses a tier except an HT1 holder, who falls to LT1. Ranked progress is not touched.
     */
    public TierChange[] recordTest(UUID winner, UUID loser, String kitId, int winnerRounds, int loserRounds, int firstTo) {
        touch(winner);
        touch(loser);
        Stats sw = get(winner), sl = get(loser);
        int tw = sw.tierIndex(kitId), tl = sl.tierIndex(kitId);
        int scoreW = sw.score(), scoreL = sl.score();
        int top = dev.mintpvp.util.Tier.values().length - 1;
        UUID king = crownHolder(kitId);
        var crown = king == null ? TierTestRules.Crown.NONE
                : king.equals(winner) ? TierTestRules.Crown.WINNER
                : king.equals(loser) ? TierTestRules.Crown.LOSER : TierTestRules.Crown.OTHER;
        var o = TierTestRules.resolve(tw, tl, winnerRounds, loserRounds, firstTo, top, crown);
        long now = System.currentTimeMillis();
        sw.kitTest.put(kitId, now);
        sl.kitTest.put(kitId, now);
        sw.kitLast.put(kitId, now);
        sl.kitLast.put(kitId, now);
        if (o.winnerTier() != tw) {
            sw.kitTier.put(kitId, o.winnerTier());
            sw.kitProgress.put(kitId, 0);
            sw.kitPeak.merge(kitId, o.winnerTier(), Math::max);
        }
        if (o.loserTier() != tl) {
            sl.kitTier.put(kitId, o.loserTier());
            sl.kitProgress.put(kitId, 0);
        }
        return new TierChange[] {
                new TierChange(kitId, tw, sw.tierIndex(kitId), sw.kitProgress.getOrDefault(kitId, 0), sw.score() - scoreW, sw.score(), tw < 0 && o.winnerTier() >= 0, o.winnerNote()),
                new TierChange(kitId, tl, sl.tierIndex(kitId), sl.kitProgress.getOrDefault(kitId, 0), sl.score() - scoreL, sl.score(), false, o.loserNote())};
    }

    /** peak tier in a kit (at least the current one) */
    public int peakTier(Stats s, String kitId) {
        return Math.max(s.kitPeak.getOrDefault(kitId, -1), s.tierIndex(kitId));
    }

    /** days until an elite tier decays, or -1 if it can't */
    public long daysUntilDecay(Stats s, String kitId) {
        int idx = s.tierIndex(kitId);
        Long last = s.kitLast.get(kitId);
        if (idx < eliteFrom() || last == null) return -1;
        int grace = plugin.getConfig().getInt("ranked.decay-grace-days", 14);
        if (grace <= 0) return -1;
        return Math.max(0, grace - (System.currentTimeMillis() - last) / 86_400_000L);
    }

    /** decay for everyone, so inactive players don't keep elite tiers; online players are told */
    public void decayAll() {
        for (UUID id : new ArrayList<>(stats.keySet())) {
            var msgs = applyDecay(id);
            var p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null) for (String m : msgs) p.sendMessage(plugin.theme().info(m));
        }
    }

    /** inactivity decay for elite tiers; returns messages for the player */
    public List<String> applyDecay(UUID id) {
        List<String> out = new ArrayList<>();
        var c = plugin.getConfig();
        int grace = c.getInt("ranked.decay-grace-days", 14);
        if (grace <= 0) return out;
        int step = c.getInt("ranked.decay-step-days", 7);
        int elite = eliteFrom();
        int floor = Math.max(0, tierByName(c.getString("ranked.decay-floor", "lt3"), elite - 1));
        Stats s = get(id);
        long now = System.currentTimeMillis();
        for (var en : new ArrayList<>(s.kitTier.entrySet())) {
            String kit = en.getKey();
            int idx = en.getValue();
            if (idx < elite) continue;
            Long last = s.kitLast.get(kit);
            if (last == null) { // first time we see this kit: start the clock
                s.kitLast.put(kit, now);
                touch(id);
                continue;
            }
            var d = RankedRules.decay(idx, last, now, grace, step, floor);
            var kitObj = plugin.kits().get(kit);
            String name = kitObj != null ? kitObj.display : kit;
            if (d.dropped() > 0) {
                s.kitTier.put(kit, d.tier());
                s.kitProgress.remove(kit);
                s.kitLast.put(kit, d.last());
                touch(id);
                out.add(name + " dropped " + dev.mintpvp.util.Tier.name(idx) + " → " + dev.mintpvp.util.Tier.name(d.tier()) + " (no ranked match in a while)");
            } else if (idx > floor) {
                long left = daysUntilDecay(s, kit);
                if (left >= 0 && left <= 3) out.add(name + " " + dev.mintpvp.util.Tier.name(idx) + " decays in " + left + "d - play a ranked match to keep it");
            }
        }
        return out;
    }

    /** admin set tier */
    public void setTier(UUID id, String kitId, int tierIndex) {
        touch(id);
        Stats s = get(id);
        if (tierIndex < 0) s.kitTier.remove(kitId);
        else {
            int top = dev.mintpvp.util.Tier.values().length - 1;
            if (tierIndex >= top) { // the old HT1 holder steps down
                UUID king = crownHolder(kitId);
                if (king != null && !king.equals(id)) {
                    get(king).kitTier.put(kitId, top - 1);
                    get(king).kitProgress.remove(kitId);
                    touch(king);
                }
            }
            s.kitTier.put(kitId, Math.min(tierIndex, top));
            s.kitPeak.merge(kitId, s.kitTier.get(kitId), Math::max);
            s.kitLast.put(kitId, System.currentTimeMillis());
        }
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
