package dev.mintpvp.listeners;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.DuelManager.Duel;
import dev.mintpvp.managers.DuelManager.State;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

/** duel isolation */
public final class DuelListener implements Listener {

    private final MintPvP plugin;

    public DuelListener(MintPvP plugin) {
        this.plugin = plugin;
    }

    /** track last hit */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        Duel d = plugin.duels().duelOf(victim.getUniqueId());
        if (d == null || d != plugin.duels().duelOf(attacker.getUniqueId())) return;
        d.lastHitBy.put(victim.getUniqueId(), attacker.getUniqueId());
    }

    /** isolate fights */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        Duel dv = plugin.duels().duelOf(victim.getUniqueId());
        Duel da = plugin.duels().duelOf(attacker.getUniqueId());
        if ((dv != null || da != null) && dv != da) {
            e.setCancelled(true);
        } else if (dv != null && !victim.equals(attacker)) {
            boolean friendly = dv.sameTeam(attacker, victim) && !plugin.getConfig().getBoolean("party.friendly-fire", false);
            if (!dv.isAlive(attacker) || !dv.isAlive(victim) || friendly) e.setCancelled(true);
        }
    }

    /** lethal damage */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Duel d = plugin.duels().duelOf(victim.getUniqueId());
        if (d == null) return;
        if (!d.isAlive(victim) || d.state == State.COUNTDOWN || d.state == State.ENDING) {
            e.setCancelled(true);
            return;
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            // totem saves
            if (savedByTotem(victim, e.getCause())) return;
            e.setCancelled(true);
            plugin.duels().eliminate(d, victim, false);
        }
    }

    private boolean savedByTotem(Player p, EntityDamageEvent.DamageCause cause) {
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE) {
            return false;
        }
        var inv = p.getInventory();
        return inv.getItemInMainHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING
                || inv.getItemInOffHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING;
    }

    /** death safety net */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Duel d = plugin.duels().duelOf(p.getUniqueId());
        if (d == null) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        plugin.duels().eliminate(d, p, false);
    }

    /** border and freeze */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Duel sd = plugin.duels().spectatedDuel(p.getUniqueId());
        if (sd != null) {
            if (!sd.arena.contains(e.getTo())) {
                if (sd.arena.contains(e.getFrom())) e.setTo(e.getFrom().clone().setDirection(e.getTo().getDirection()));
                else p.teleport(sd.arena.center());
            }
            return;
        }
        Duel d = plugin.duels().duelOf(p.getUniqueId());
        if (d == null) return;
        var from = e.getFrom();
        var to = e.getTo();
        if (!d.arena.containsXZ(to)) {
            if (d.arena.containsXZ(from)) {
                e.setTo(from.clone().setDirection(to.getDirection()));
            } else {
                returnToSpawn(p, d);
            }
            p.sendActionBar(plugin.theme().text("you can't leave the arena", plugin.theme().secondary()));
            return;
        }
        if (to.getY() < d.arena.minY()) {
            returnToSpawn(p, d);
            p.sendActionBar(plugin.theme().text("out of bounds - back to your spawn", plugin.theme().secondary()));
            return;
        }
        if (to.getY() > d.arena.maxY()) {
            e.setTo(from.clone().setDirection(to.getDirection()));
            p.sendActionBar(plugin.theme().text("height limit reached", plugin.theme().secondary()));
            return;
        }
        if (d.state == State.COUNTDOWN && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            e.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    private void returnToSpawn(Player p, Duel d) {
        p.teleport(d.spawnOf(p));
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    /** no mob spawns */
    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!plugin.getConfig().getBoolean("duel.clear-mobs", true)) return;
        if (plugin.arenas().activeAt(e.getLocation()).isPresent()) e.setCancelled(true);
    }

    /** teleport lock */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Duel sd = plugin.duels().spectatedDuel(e.getPlayer().getUniqueId());
        if (sd != null) {
            var cause = e.getCause();
            boolean ok = cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                    || (cause == PlayerTeleportEvent.TeleportCause.SPECTATE && sd.arena.contains(e.getTo()));
            if (!ok) e.setCancelled(true);
            return;
        }
        Duel d = plugin.duels().duelOf(e.getPlayer().getUniqueId());
        if (d == null) return;
        var cause = e.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        boolean hop = cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT;
        if (hop && d.state == State.FIGHTING && e.getTo() != null && d.arena.innerContains(e.getTo())) return;
        e.setCancelled(true);
        if (hop && d.state == State.FIGHTING) {
            e.getPlayer().sendActionBar(plugin.theme().text("that would land outside the arena", plugin.theme().secondary()));
        }
    }

    /** pearl bounce */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.EnderPearl pearl) || !(pearl.getShooter() instanceof Player p)) return;
        Duel d = plugin.duels().duelOf(p.getUniqueId());
        var block = e.getHitBlock();
        var face = e.getHitBlockFace();
        if (d == null || block == null || face == null) return;
        boolean wall = block.getType() == org.bukkit.Material.BARRIER
                || !d.arena.innerContains(block.getLocation().add(0.5, 0.5, 0.5));
        if (!wall) return;
        e.setCancelled(true);
        org.bukkit.util.Vector n = face.getDirection();
        org.bukkit.util.Vector v = pearl.getVelocity();
        org.bukkit.util.Vector bounced = v.clone().subtract(n.clone().multiply(2 * v.dot(n))).multiply(0.8);
        org.bukkit.Location back = block.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pearl.isDead() || !pearl.isValid()) return;
            pearl.teleport(back);
            pearl.setVelocity(bounced);
            pearl.getWorld().playSound(back, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.4f);
            pearl.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, back, 6, .15, .15, .15, .02);
        });
    }

    private boolean protectedTerrain(Player p) {
        return p != null && plugin.duels().inDuel(p.getUniqueId())
                && !plugin.getConfig().getBoolean("duel.allow-building", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protectedTerrain(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (protectedTerrain(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (protectedTerrain(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (protectedTerrain(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        boolean spectating = plugin.duels().isSpectating(p.getUniqueId());
        if (!(plugin.duels().inDuel(p.getUniqueId()) || spectating) || p.hasPermission("mintpvp.admin")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> allowed = plugin.getConfig().getStringList("duel.allowed-commands");
        if (allowed.stream().anyMatch(a -> a.equalsIgnoreCase(label))) return;
        if (spectating && (label.equals("spectate") || label.equals("spec") || label.equals("watch"))) return;
        e.setCancelled(true);
        p.sendMessage(plugin.theme().error(spectating
                ? "you can't use that while spectating. (/spectate leave)"
                : "you can't use that during a duel. (/duel forfeit to leave)"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.queue().leave(e.getPlayer().getUniqueId());
        plugin.lobby().builderOff(e.getPlayer());
        plugin.parties().leave(e.getPlayer(), true);
        plugin.duels().stopSpectating(e.getPlayer());
        plugin.duels().forfeit(e.getPlayer());
    }
}
