package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Random matchmaking queue, one queue per kit.
 * {@link Type#RANKED} is reserved: the type exists so ranked (Elo) queues can be
 * added later without reshaping the API, but it can't be joined yet.
 */
public final class QueueManager {

    public enum Type {
        UNRANKED(true), RANKED(false);

        private final boolean available;

        Type(boolean available) {
            this.available = available;
        }

        public boolean available() {
            return available;
        }
    }

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

    public boolean join(Player p, Kit kit, Type type) {
        Theme t = plugin.theme();
        UUID id = p.getUniqueId();
        if (!type.available()) { p.sendMessage(t.error("ranked queues are coming soon.")); return false; }
        if (plugin.duels().inDuel(id)) { p.sendMessage(t.error("you're already in a duel.")); return false; }
        if (plugin.duels().isSpectating(id)) { p.sendMessage(t.error("stop spectating first (/spectate leave).")); return false; }
        if (plugin.combat().isTagged(id)) { p.sendMessage(t.error("you can't queue while in combat.")); return false; }
        if (queued.containsKey(id)) { p.sendMessage(t.error("you're already queued. use /queue leave first.")); return false; }
        if (plugin.arenas().all().stream().noneMatch(a -> a.ready())) {
            p.sendMessage(t.error("no arenas are set up yet."));
            return false;
        }
        queued.put(id, new Entry(id, kit, type, System.currentTimeMillis()));
        p.sendMessage(t.info(t.text("you joined the ", t.accent())
                .append(t.text(type.name().toLowerCase(Locale.ROOT) + " ", t.secondary()))
                .append(t.text(kit.display, t.primary()))
                .append(t.text(" queue. /queue leave to cancel.", t.accent()))));
        matchAll();
        return true;
    }

    public boolean leave(UUID id) {
        return queued.remove(id) != null;
    }

    public void leaveKit(Kit kit) {
        queued.values().removeIf(e -> e.kit() == kit);
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
        List<Player> ready = new ArrayList<>();
        for (Entry e : queued.values()) {
            if (e.kit() != kit || e.type() != type) continue;
            Player p = Bukkit.getPlayer(e.id());
            if (eligible(p)) ready.add(p);
        }
        while (ready.size() >= 2) {
            Player a = ready.remove(0);
            Player b = ready.remove(0);
            if (!plugin.duels().startMatch(a, b, kit)) return; // no free arena: try again next tick
        }
    }
}
