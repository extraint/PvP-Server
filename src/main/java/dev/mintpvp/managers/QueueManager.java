package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** matchmaking */
public final class QueueManager {

    public enum Type { UNRANKED, RANKED }

    public record Entry(UUID id, Kit kit, Type type, long since) {}

    private final MintPvP plugin;
    private final Map<UUID, Entry> queued = new LinkedHashMap<>();
    private BukkitTask task;

    public QueueManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::matchAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        queued.clear();
    }

    public boolean rankedEnabled() {
        return plugin.getConfig().getBoolean("ranked.enabled", true);
    }

    public Entry entryOf(UUID id) {
        return queued.get(id);
    }

    public boolean isQueued(UUID id) {
        return queued.containsKey(id);
    }

    public int count(Kit kit, Type type) {
        return (int) queued.values().stream().filter(e -> e.kit() == kit && e.type() == type).count();
    }

    public int total() {
        return queued.size();
    }

    private int eliteCap() {
        return Math.max(1, plugin.getConfig().getInt("ranked.elite-window-max", 2));
    }

    private boolean isElite(Entry e) {
        return e.type() == Type.RANKED && plugin.stats().get(e.id()).tierIndex(e.kit().id) >= plugin.stats().eliteFrom();
    }

    /** tier window */
    public int window(Entry e) {
        var c = plugin.getConfig();
        int base = c.getInt("ranked.window-base", 1);
        int step = c.getInt("ranked.window-step", 1);
        int interval = Math.max(1, c.getInt("ranked.window-interval-seconds", 10));
        int max = Math.max(base, c.getInt("ranked.window-max", 10));
        long waited = (System.currentTimeMillis() - e.since()) / 1000;
        int w = (int) Math.min(max, base + step * (waited / interval));
        return isElite(e) ? Math.min(w, eliteCap()) : w;
    }

    public boolean join(Player p, Kit kit, Type type) {
        Theme t = plugin.theme();
        UUID id = p.getUniqueId();
        if (type == Type.RANKED && !rankedEnabled()) { p.sendMessage(t.error("ranked is disabled right now.")); return false; }
        if (plugin.duels().inDuel(id)) { p.sendMessage(t.error("you're already in a duel.")); return false; }
        if (plugin.duels().isSpectating(id)) { p.sendMessage(t.error("stop spectating first (/spectate leave).")); return false; }
        if (plugin.combat().isTagged(id)) { p.sendMessage(t.error("you can't queue while in combat.")); return false; }
        if (plugin.parties().blocks(p, "join a queue")) return false;
        if (queued.containsKey(id)) { p.sendMessage(t.error("you're already queued. use /queue leave first.")); return false; }
        if (plugin.arenas().all().stream().noneMatch(a -> a.ready()) && !plugin.generator().building()) {
            p.sendMessage(t.error(plugin.duels().noArenaReason()));
            return false;
        }
        queued.put(id, new Entry(id, kit, type, System.currentTimeMillis()));
        plugin.lobby().refresh(p);
        p.sendMessage(t.info(t.text("you joined the ", t.accent())
                .append(t.text(type.name().toLowerCase(Locale.ROOT) + " ", t.secondary()))
                .append(t.text(kit.display, t.primary()))
                .append(t.text(" queue. /queue leave to cancel.", t.accent()))));
        matchAll();
        return true;
    }

    public boolean leave(UUID id) {
        boolean removed = queued.remove(id) != null;
        if (removed) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.lobby().refresh(p);
        }
        return removed;
    }

    public void leaveKit(Kit kit) {
        for (UUID id : queued.values().stream().filter(e -> e.kit() == kit).map(Entry::id).toList()) leave(id);
    }

    private boolean eligible(Player p) {
        return p != null && p.isOnline() && !plugin.duels().inDuel(p.getUniqueId())
                && !plugin.duels().isSpectating(p.getUniqueId())
                && !plugin.combat().isTagged(p.getUniqueId());
    }

    private void matchAll() {
        queued.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        Set<String> seen = new HashSet<>();
        for (Entry e : new ArrayList<>(queued.values())) {
            if (seen.add(e.type() + ":" + e.kit().id)) match(e.kit(), e.type());
        }
    }

    private void match(Kit kit, Type type) {
        List<Player> pool = new ArrayList<>();
        for (Entry e : queued.values()) {
            if (e.kit() != kit || e.type() != type) continue;
            Player p = Bukkit.getPlayer(e.id());
            if (eligible(p)) pool.add(p);
        }
        boolean ranked = type == Type.RANKED;
        while (pool.size() >= 2) {
            Player a = pool.get(0);
            Player b = null;
            if (!ranked) {
                b = pool.get(1);
            } else {
                int tierA = plugin.stats().get(a.getUniqueId()).tierIndex(kit.id);
                int bestDiff = Integer.MAX_VALUE;
                for (int i = 1; i < pool.size(); i++) {
                    Player c = pool.get(i);
                    int diff = Math.abs(tierA - plugin.stats().get(c.getUniqueId()).tierIndex(kit.id));
                    Entry ea = queued.get(a.getUniqueId()), ec = queued.get(c.getUniqueId());
                    int allowed = Math.max(window(ea), window(ec));
                    if (isElite(ea) || isElite(ec)) allowed = Math.min(allowed, eliteCap()); // elite players only face close tiers
                    if (diff <= allowed && diff < bestDiff) {
                        b = c;
                        bestDiff = diff;
                    }
                }
            }
            if (b == null) {
                pool.remove(0);
                continue;
            }
            pool.remove(a);
            pool.remove(b);
            if (!plugin.duels().startMatch(a, b, kit, ranked)) return;
        }
    }
}
