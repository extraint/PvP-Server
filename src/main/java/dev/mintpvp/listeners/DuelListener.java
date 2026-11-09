package dev.mintpvp.listeners;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.DuelManager.Duel;
import dev.mintpvp.managers.DuelManager.State;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

/** Keeps duels isolated: lethal damage ends the duel instead of killing. */
public final class DuelListener implements Listener {

    private final MintPvP plugin;

    public DuelListener(MintPvP plugin) {
        this.plugin = plugin;
    }

    /** Outsiders can't hit duelists and duelists can't hit outsiders. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        Duel dv = plugin.duels().duelOf(victim.getUniqueId());
        Duel da = plugin.duels().duelOf(attacker.getUniqueId());
        if ((dv != null || da != null) && dv != da) e.setCancelled(true);
    }

    /** Lethal damage in a duel ends it (no death screen, no item loss). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Duel d = plugin.duels().duelOf(victim.getUniqueId());
        if (d == null) return;
        if (d.state == State.COUNTDOWN) {
            e.setCancelled(true);
            return;
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            plugin.duels().end(d, d.opponentOf(victim));
        }
    }

    /** Safety net for deaths that bypass the damage check (e.g. /kill). */
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
        plugin.duels().end(d, d.opponentOf(p));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Duel d = plugin.duels().duelOf(e.getPlayer().getUniqueId());
        if (d == null || d.state != State.COUNTDOWN) return;
        var from = e.getFrom();
        var to = e.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            e.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    /** No teleporting out of a duel (pearls, plugin teleports), except our own. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Duel d = plugin.duels().duelOf(e.getPlayer().getUniqueId());
        if (d == null) return;
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.duels().inDuel(p.getUniqueId()) || p.hasPermission("mintpvp.admin")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> allowed = plugin.getConfig().getStringList("duel.allowed-commands");
        if (allowed.stream().anyMatch(a -> a.equalsIgnoreCase(label))) return;
        e.setCancelled(true);
        p.sendMessage(plugin.theme().error("you can't use that during a duel. (/duel forfeit to leave)"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.duels().forfeit(e.getPlayer());
    }
}
