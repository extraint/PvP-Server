package dev.mintpvp.listeners;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Combat tagging, kill/death handling, streaks. */
public final class CombatListener implements Listener {

    private final MintPvP plugin;

    public CombatListener(MintPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;

        Theme t = plugin.theme();
        if (plugin.combat().tag(victim.getUniqueId())) {
            victim.sendMessage(t.info("you are now in combat. don't log out!"));
        }
        if (plugin.combat().tag(attacker.getUniqueId())) {
            attacker.sendMessage(t.info("you are now in combat. don't log out!"));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        Theme t = plugin.theme();

        if (plugin.getConfig().getBoolean("gameplay.keep-inventory", true)) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }

        plugin.combat().clear(victim.getUniqueId());

        if (killer != null && !killer.equals(victim)) {
            int streak = plugin.stats().recordKill(killer.getUniqueId(), victim.getUniqueId());
            e.deathMessage(t.info(
                    t.raw(victim.getName(), t.secondary())
                            .append(t.text(" was slain by ", t.muted()))
                            .append(t.raw(killer.getName(), t.primary()))
                            .append(t.text(" [" + streak + " streak]", t.muted()))));

            double heal = plugin.getConfig().getDouble("gameplay.heal-killer", 4.0);
            if (heal > 0) {
                double max = killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                killer.setHealth(Math.min(max, killer.getHealth() + heal));
            }
            plugin.combat().clear(killer.getUniqueId());

            int every = plugin.getConfig().getInt("gameplay.streak-announce-every", 5);
            if (every > 0 && streak % every == 0) {
                Bukkit.broadcast(t.info(t.raw(killer.getName(), t.primary())
                        .append(t.text(" is on a " + streak + " kill streak!", t.accent()))));
            }
        } else {
            plugin.stats().recordDeath(victim.getUniqueId());
            e.deathMessage(t.info(t.raw(victim.getName(), t.secondary())
                    .append(t.text(" died", t.muted()))));
        }

        if (plugin.getConfig().getBoolean("gameplay.instant-respawn", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isDead()) victim.spigot().respawn();
            });
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.getConfig().getBoolean("kit.give-on-respawn", true)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.kits().give(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (plugin.combat().isTagged(id) && plugin.getConfig().getBoolean("combat.kill-on-logout", true)) {
            plugin.combat().clear(id);
            plugin.stats().recordDeath(id);
            Theme t = plugin.theme();
            Bukkit.broadcast(t.info(t.raw(p.getName(), t.secondary())
                    .append(t.text(" logged out in combat", t.muted()))));
            p.setHealth(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("combat.block-commands", true)) return;
        if (p.hasPermission("mintpvp.bypass.combat")) return;
        if (!plugin.combat().isTagged(p.getUniqueId())) return;

        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> allowed = plugin.getConfig().getStringList("combat.allowed-commands");
        if (allowed.stream().anyMatch(a -> a.equalsIgnoreCase(label))) return;

        e.setCancelled(true);
        p.sendMessage(plugin.theme().error("you can't use commands in combat ("
                + plugin.combat().secondsLeft(p.getUniqueId()) + "s left)"));
    }
}
