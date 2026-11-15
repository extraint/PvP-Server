package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Free-for-all lobbies, one per kit. Once enough players have joined a countdown starts
 * (it starts at once if the lobby fills up); then everyone is dropped into an arena and the
 * last player standing wins.
 */
public final class FfaManager {

    private static final class Lobby {
        final Kit kit;
        final LinkedHashSet<UUID> players = new LinkedHashSet<>();
        long readySince;

        Lobby(Kit kit) {
            this.kit = kit;
        }
    }

    private final MintPvP plugin;
    private final Map<String, Lobby> lobbies = new LinkedHashMap<>();
    private final Map<UUID, String> where = new HashMap<>();
    private BukkitTask task;

    public FfaManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        lobbies.clear();
        where.clear();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("ffa.enabled", true);
    }

    private int min() { return Math.max(2, plugin.getConfig().getInt("ffa.min-players", 3)); }

    private int max() { return Math.max(min(), plugin.getConfig().getInt("ffa.max-players", 10)); }

    private int delay() { return Math.max(5, plugin.getConfig().getInt("ffa.start-delay-seconds", 30)); }

    public boolean isIn(UUID id) {
        return where.containsKey(id);
    }

    public Kit kitOf(UUID id) {
        Lobby l = where.containsKey(id) ? lobbies.get(where.get(id)) : null;
        return l == null ? null : l.kit;
    }

    public int count(Kit kit) {
        Lobby l = lobbies.get(kit.id);
        return l == null ? 0 : l.players.size();
    }

    public boolean join(Player p, Kit kit) {
        Theme t = plugin.theme();
        UUID id = p.getUniqueId();
        if (!enabled()) { p.sendMessage(t.error("free-for-all is disabled right now.")); return false; }
        if (isIn(id)) { p.sendMessage(t.error("you're already in a free-for-all lobby (/ffa leave).")); return false; }
        if (plugin.duels().inDuel(id)) { p.sendMessage(t.error("you're in a duel.")); return false; }
        if (plugin.duels().isSpectating(id)) { p.sendMessage(t.error("stop spectating first (/spectate leave).")); return false; }
        if (plugin.queue().isQueued(id)) { p.sendMessage(t.error("leave the queue first (/queue leave).")); return false; }
        if (plugin.combat().isTagged(id)) { p.sendMessage(t.error("you can't join while in combat.")); return false; }
        if (plugin.arenas().all().stream().noneMatch(a -> a.ready()) && !plugin.generator().building()) {
            p.sendMessage(t.error(plugin.duels().noArenaReason()));
            return false;
        }
        Lobby l = lobbies.computeIfAbsent(kit.id, k -> new Lobby(kit));
        if (l.players.size() >= max()) { p.sendMessage(t.error("that lobby is full - it's about to start.")); return false; }
        l.players.add(id);
        where.put(id, kit.id);
        for (UUID other : l.players) {
            Player o = Bukkit.getPlayer(other);
            if (o != null) {
                o.sendMessage(t.info(t.raw(other.equals(id) ? "you joined" : p.getName() + " joined", t.primary())
                        .append(t.text(" the " + kit.display + " free-for-all (" + l.players.size() + "/" + max() + "). need " + min() + " to start.", t.accent()))));
            }
        }
        return true;
    }

    public boolean leave(UUID id) {
        String kitId = where.remove(id);
        if (kitId == null) return false;
        Lobby l = lobbies.get(kitId);
        if (l != null) {
            l.players.remove(id);
            if (l.players.isEmpty()) lobbies.remove(kitId);
        }
        return true;
    }

    public void leaveKit(Kit kit) {
        Lobby l = lobbies.get(kit.id);
        if (l != null) new ArrayList<>(l.players).forEach(this::leave);
    }

    /** The action-bar text for a waiting player, or null. */
    public Component barFor(UUID id) {
        Theme t = plugin.theme();
        String kitId = where.get(id);
        Lobby l = kitId == null ? null : lobbies.get(kitId);
        if (l == null) return null;
        if (l.players.size() < min()) {
            return t.text("free-for-all · " + l.kit.display + " · waiting for players " + l.players.size() + "/" + min(), t.secondary());
        }
        long left = Math.max(0, delay() - (System.currentTimeMillis() - l.readySince) / 1000);
        return t.text("free-for-all · " + l.kit.display + " · " + l.players.size() + "/" + max() + " · starts in " + left + "s", t.secondary());
    }

    private void tick() {
        for (Lobby l : new ArrayList<>(lobbies.values())) {
            l.players.removeIf(id -> {
                boolean gone = Bukkit.getPlayer(id) == null;
                if (gone) where.remove(id);
                return gone;
            });
            if (l.players.isEmpty()) {
                lobbies.remove(l.kit.id);
                continue;
            }
            if (l.players.size() < min()) {
                l.readySince = 0;
                continue;
            }
            if (l.readySince == 0) l.readySince = System.currentTimeMillis();
            long waited = (System.currentTimeMillis() - l.readySince) / 1000;
            if (l.players.size() < max() && waited < delay()) continue;

            List<Player> ready = new ArrayList<>();
            for (UUID id : l.players) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !plugin.duels().inDuel(id) && !plugin.duels().isSpectating(id) && !plugin.combat().isTagged(id)) ready.add(p);
            }
            if (ready.size() < min()) {
                l.readySince = 0; // someone is busy: wait for them
                continue;
            }
            if (plugin.duels().startFfa(ready, l.kit)) {
                for (Player p : ready) where.remove(p.getUniqueId());
                l.players.removeAll(ready.stream().map(Player::getUniqueId).toList());
                if (l.players.isEmpty()) lobbies.remove(l.kit.id);
                else l.readySince = 0;
            } // else no arena is free yet: try again next second
        }
    }
}
