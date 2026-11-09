package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.ArenaManager.Arena;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** 1v1 duels: requests, countdown, state save/restore, and results. */
public final class DuelManager {

    public enum State { COUNTDOWN, FIGHTING }

    public static final class Duel {
        public final Arena arena;
        public final Player a, b;
        public State state = State.COUNTDOWN;
        final Map<UUID, Saved> saved = new HashMap<>();
        public final Set<UUID> spectators = new LinkedHashSet<>();
        BukkitTask countdown, timeout, sweep;

        Duel(Arena arena, Player a, Player b) {
            this.arena = arena;
            this.a = a;
            this.b = b;
        }

        public Player opponentOf(Player p) {
            return p.getUniqueId().equals(a.getUniqueId()) ? b : a;
        }
    }

    private record Saved(Location loc, ItemStack[] contents, GameMode mode, double health,
                         int food, float saturation, int level, float exp,
                         Collection<PotionEffect> effects) {}

    private record SpecSaved(Location loc, GameMode mode) {}

    private record Request(UUID from, String arena, long expires) {}

    private final MintPvP plugin;
    private final Map<UUID, Request> requests = new HashMap<>(); // keyed by target
    private final Map<UUID, Duel> byPlayer = new HashMap<>();
    private final Map<UUID, Duel> spectating = new HashMap<>();
    private final Map<UUID, SpecSaved> specSaved = new HashMap<>();

    public DuelManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public boolean inDuel(UUID id) {
        return byPlayer.containsKey(id);
    }

    public Duel duelOf(UUID id) {
        return byPlayer.get(id);
    }

    public boolean isSpectating(UUID id) {
        return spectating.containsKey(id);
    }

    public Duel spectatedDuel(UUID id) {
        return spectating.get(id);
    }

    // ---- requests -------------------------------------------------------

    public void request(Player from, Player to, String arenaName) {
        Theme t = plugin.theme();
        if (from.equals(to)) { from.sendMessage(t.error("you can't duel yourself.")); return; }
        if (inDuel(from.getUniqueId())) { from.sendMessage(t.error("you're already in a duel.")); return; }
        if (isSpectating(from.getUniqueId())) { from.sendMessage(t.error("stop spectating first (/spectate leave).")); return; }
        if (isSpectating(to.getUniqueId())) { from.sendMessage(t.error("that player is spectating a duel.")); return; }
        if (inDuel(to.getUniqueId())) { from.sendMessage(t.error("that player is already in a duel.")); return; }
        if (plugin.combat().isTagged(from.getUniqueId())) { from.sendMessage(t.error("you can't duel while in combat.")); return; }
        if (plugin.combat().isTagged(to.getUniqueId())) { from.sendMessage(t.error("that player is in combat.")); return; }

        String chosen = null;
        if (arenaName != null) {
            Arena a = plugin.arenas().get(arenaName);
            if (a == null || !a.ready()) { from.sendMessage(t.error("that arena doesn't exist or isn't set up.")); return; }
            chosen = a.name;
        } else if (plugin.arenas().randomFree().isEmpty()) {
            from.sendMessage(t.error("no arenas are available right now."));
            return;
        }

        int expire = plugin.getConfig().getInt("duel.request-expire-seconds", 30);
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), chosen, System.currentTimeMillis() + expire * 1000L));

        from.sendMessage(t.info(t.text("duel request sent to ", t.accent()).append(t.raw(to.getName(), t.primary()))));
        Component accept = t.text("[accept]", t.primary())
                .clickEvent(ClickEvent.runCommand("/duel accept " + from.getName()));
        Component deny = t.text("[deny]", t.muted())
                .clickEvent(ClickEvent.runCommand("/duel deny " + from.getName()));
        to.sendMessage(t.info(t.raw(from.getName(), t.primary())
                .append(t.text(" challenged you to a duel" + (chosen != null ? " on " + chosen : "") + "! ", t.accent()))
                .append(accept).append(Component.text(" ")).append(deny)));
    }

    private Request validRequest(Player target, String fromName) {
        Request r = requests.get(target.getUniqueId());
        if (r == null || r.expires < System.currentTimeMillis()) {
            requests.remove(target.getUniqueId());
            return null;
        }
        Player from = Bukkit.getPlayer(r.from);
        if (from == null || (fromName != null && !from.getName().equalsIgnoreCase(fromName))) return null;
        return r;
    }

    public void accept(Player target, String fromName) {
        Theme t = plugin.theme();
        Request r = validRequest(target, fromName);
        if (r == null) { target.sendMessage(t.error("no pending duel request.")); return; }
        Player from = Bukkit.getPlayer(r.from);
        requests.remove(target.getUniqueId());

        if (inDuel(from.getUniqueId()) || inDuel(target.getUniqueId())
                || isSpectating(from.getUniqueId()) || isSpectating(target.getUniqueId())
                || plugin.combat().isTagged(from.getUniqueId()) || plugin.combat().isTagged(target.getUniqueId())) {
            target.sendMessage(t.error("the duel can't start right now."));
            return;
        }
        Arena arena = r.arena != null ? plugin.arenas().get(r.arena) : plugin.arenas().randomFree().orElse(null);
        if (arena == null || !arena.ready() || arena.inUse) {
            target.sendMessage(t.error("that arena isn't available."));
            from.sendMessage(t.error("that arena isn't available."));
            return;
        }
        start(from, target, arena);
    }

    public void deny(Player target, String fromName) {
        Theme t = plugin.theme();
        Request r = validRequest(target, fromName);
        if (r == null) { target.sendMessage(t.error("no pending duel request.")); return; }
        requests.remove(target.getUniqueId());
        target.sendMessage(t.info("duel request denied."));
        Player from = Bukkit.getPlayer(r.from);
        if (from != null) from.sendMessage(t.info(t.raw(target.getName(), t.primary()).append(t.text(" denied your duel request.", t.accent()))));
    }

    // ---- lifecycle ------------------------------------------------------

    private void start(Player a, Player b, Arena arena) {
        Duel duel = new Duel(arena, a, b);
        arena.inUse = true;
        byPlayer.put(a.getUniqueId(), duel);
        byPlayer.put(b.getUniqueId(), duel);
        requests.remove(a.getUniqueId());
        requests.remove(b.getUniqueId());

        for (Player p : List.of(a, b)) {
            duel.saved.put(p.getUniqueId(), new Saved(p.getLocation(), p.getInventory().getContents().clone(),
                    p.getGameMode(), p.getHealth(), p.getFoodLevel(), p.getSaturation(),
                    p.getLevel(), p.getExp(), new ArrayList<>(p.getActivePotionEffects())));
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            p.setGameMode(GameMode.SURVIVAL);
            p.setFireTicks(0);
            p.setLevel(0);
            p.setExp(0);
            plugin.kits().give(p);
        }
        a.teleport(arena.spawn1);
        b.teleport(arena.spawn2);

        // visible per-player border so duelists can see the arena limits
        applyBorder(a, arena);
        applyBorder(b, arena);

        if (plugin.getConfig().getBoolean("duel.announce-start", true)) {
            Theme t = plugin.theme();
            Bukkit.broadcast(t.info(t.raw(a.getName(), t.primary())
                    .append(t.text(" vs ", t.muted()))
                    .append(t.raw(b.getName(), t.primary()))
                    .append(t.text(" is starting on " + arena.name + " ", t.accent()))
                    .append(t.text("[watch]", t.secondary())
                            .clickEvent(ClickEvent.runCommand("/spectate " + a.getName())))));
        }

        if (plugin.getConfig().getBoolean("duel.clear-mobs", true)) {
            clearMobs(arena);
            duel.sweep = Bukkit.getScheduler().runTaskTimer(plugin, () -> clearMobs(arena), 40L, 40L);
        }

        int seconds = Math.max(1, plugin.getConfig().getInt("duel.countdown-seconds", 5));
        Theme t = plugin.theme();
        int[] left = {seconds};
        duel.countdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] > 0) {
                for (Player p : List.of(a, b)) {
                    p.showTitle(Title.title(t.raw(String.valueOf(left[0]), t.primary()),
                            t.text(a.getName() + " vs " + b.getName(), t.secondary()),
                            Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1100), java.time.Duration.ZERO)));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                left[0]--;
                return;
            }
            duel.countdown.cancel();
            duel.state = State.FIGHTING;
            for (Player p : List.of(a, b)) {
                p.showTitle(Title.title(t.text("fight!", t.primary()), Component.empty()));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            }
            int max = plugin.getConfig().getInt("duel.max-duration-seconds", 300);
            if (max > 0) {
                duel.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> end(duel, null), max * 20L);
            }
        }, 0L, 20L);
    }

    private void applyBorder(Player p, Arena arena) {
        WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(arena.center());
        wb.setSize(arena.radius() * 2.0);
        wb.setWarningDistance(5);
        p.setWorldBorder(wb);
    }

    // ---- spectating -----------------------------------------------------

    /** Finds a running duel by a duelist's name or by arena name. */
    public Duel find(String query) {
        Player pl = Bukkit.getPlayerExact(query);
        if (pl != null && byPlayer.containsKey(pl.getUniqueId())) return byPlayer.get(pl.getUniqueId());
        return new HashSet<>(byPlayer.values()).stream()
                .filter(d -> d.arena.name.equalsIgnoreCase(query)).findFirst().orElse(null);
    }

    public Collection<Duel> activeDuels() {
        return new HashSet<>(byPlayer.values());
    }

    public void spectate(Player sp, String query) {
        Theme t = plugin.theme();
        UUID id = sp.getUniqueId();
        if (!plugin.getConfig().getBoolean("duel.allow-spectators", true)) { sp.sendMessage(t.error("spectating is disabled.")); return; }
        if (inDuel(id)) { sp.sendMessage(t.error("you're in a duel.")); return; }
        if (isSpectating(id)) { sp.sendMessage(t.error("you're already spectating (/spectate leave).")); return; }
        if (plugin.combat().isTagged(id)) { sp.sendMessage(t.error("you can't spectate while in combat.")); return; }
        Duel d = find(query);
        if (d == null) { sp.sendMessage(t.error("no running duel found for that player or arena.")); return; }
        int max = plugin.getConfig().getInt("duel.max-spectators", 20);
        if (max > 0 && d.spectators.size() >= max) { sp.sendMessage(t.error("that duel has too many spectators.")); return; }

        specSaved.put(id, new SpecSaved(sp.getLocation(), sp.getGameMode()));
        d.spectators.add(id);
        spectating.put(id, d);
        applyBorder(sp, d.arena);
        sp.setGameMode(GameMode.SPECTATOR);
        sp.teleport(d.a.getLocation());
        sp.sendMessage(t.info(t.text("now spectating ", t.accent())
                .append(t.raw(d.a.getName() + " vs " + d.b.getName(), t.primary()))
                .append(t.text(". /spectate leave to stop.", t.accent()))));
    }

    /** @return false if the player wasn't spectating. */
    public boolean stopSpectating(Player sp) {
        Duel d = spectating.get(sp.getUniqueId());
        if (d == null) return false;
        removeSpectator(d, sp.getUniqueId());
        return true;
    }

    private void removeSpectator(Duel d, UUID id) {
        d.spectators.remove(id);
        spectating.remove(id);
        SpecSaved s = specSaved.remove(id);
        Player sp = Bukkit.getPlayer(id);
        if (sp == null || s == null) return;
        sp.setWorldBorder(null);
        sp.setGameMode(s.mode());
        sp.teleport(s.loc());
    }

    /**
     * Removes mobs from the arena volume. Players, tamed pets and name-tagged
     * mobs are left alone; removal is silent (no drops or xp).
     */
    public void clearMobs(Arena arena) {
        var center = arena.center();
        if (center == null || center.getWorld() == null) return;
        center.getWorld().getNearbyEntities(arena.box(), e ->
                e instanceof org.bukkit.entity.LivingEntity
                        && !(e instanceof Player)
                        && !(e instanceof org.bukkit.entity.ArmorStand)
                        && !(e instanceof org.bukkit.entity.Tameable t && t.isTamed())
                        && e.customName() == null)
                .forEach(org.bukkit.entity.Entity::remove);
    }

    /** End a duel. winner == null means a draw. */
    public void end(Duel duel, Player winner) {
        if (byPlayer.get(duel.a.getUniqueId()) != duel && byPlayer.get(duel.b.getUniqueId()) != duel) return;
        if (duel.countdown != null) duel.countdown.cancel();
        if (duel.timeout != null) duel.timeout.cancel();
        if (duel.sweep != null) duel.sweep.cancel();
        byPlayer.remove(duel.a.getUniqueId());
        byPlayer.remove(duel.b.getUniqueId());
        duel.arena.inUse = false;

        for (UUID sid : new ArrayList<>(duel.spectators)) {
            Player sp = Bukkit.getPlayer(sid);
            if (sp != null) sp.sendMessage(plugin.theme().info("the duel has ended."));
            removeSpectator(duel, sid);
        }

        Theme t = plugin.theme();
        if (winner != null) {
            Player loser = duel.opponentOf(winner);
            plugin.stats().recordDuel(winner.getUniqueId(), loser.getUniqueId());
            Bukkit.broadcast(t.info(t.raw(winner.getName(), t.primary())
                    .append(t.text(" defeated ", t.accent()))
                    .append(t.raw(loser.getName(), t.secondary()))
                    .append(t.text(" in a duel!", t.accent()))));
        } else {
            for (Player p : List.of(duel.a, duel.b)) p.sendMessage(t.info("the duel ended in a draw."));
        }
        for (Player p : List.of(duel.a, duel.b)) restore(p, duel.saved.get(p.getUniqueId()));
    }

    private void restore(Player p, Saved s) {
        p.setWorldBorder(null);
        if (s == null) return;
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.getInventory().setContents(s.contents());
        p.setGameMode(s.mode());
        p.setLevel(s.level());
        p.setExp(s.exp());
        p.setFoodLevel(s.food());
        p.setSaturation(s.saturation());
        p.setFireTicks(0);
        p.addPotionEffects(s.effects());
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(1.0, Math.min(max, s.health())));
        p.teleport(s.loc());
    }

    /** A player left (quit / forfeit command): the opponent wins. */
    public void forfeit(Player p) {
        Duel d = byPlayer.get(p.getUniqueId());
        if (d != null) end(d, d.opponentOf(p));
    }

    public void shutdown() {
        new HashSet<>(byPlayer.values()).forEach(d -> end(d, null));
    }
}
