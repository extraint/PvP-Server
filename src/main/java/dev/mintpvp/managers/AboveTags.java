package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** tier line floating above the nametag, riding the player */
public final class AboveTags {

    private final MintPvP plugin;
    private org.bukkit.scheduler.BukkitTask sweep;
    private final org.bukkit.NamespacedKey MARK;
    private final Map<UUID, TextDisplay> shown = new HashMap<>();
    private final Map<UUID, Integer> keys = new HashMap<>();

    public AboveTags(MintPvP plugin) {
        this.plugin = plugin;
        this.MARK = new org.bukkit.NamespacedKey(plugin, "tiertag");
    }

    /** true when the tag is the floating line instead of a prefix */
    public boolean above() {
        return plugin.getConfig().getString("tags.mode", "above").equalsIgnoreCase("above");
    }

    /** no polling: tags change on tier, match, join, teleport and respawn events */
    public void start() {
        stop();
        for (var w : Bukkit.getWorlds()) { // leftovers from a reload
            for (TextDisplay old : w.getEntitiesByClass(TextDisplay.class)) {
                if (old.getPersistentDataContainer().has(MARK)) old.remove();
            }
        }
        if (!above()) return;
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p.getUniqueId());
        // slow repair pass for anything an event missed
        sweep = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 100L, 100L);
    }

    public void stop() {
        if (sweep != null) sweep.cancel();
        sweep = null;
        for (TextDisplay d : shown.values()) if (d.isValid()) d.remove();
        shown.clear();
        keys.clear();
    }

    /** bring one player's tag up to date (safe from any thread) */
    public void refresh(UUID id) {
        if (!plugin.isEnabled() || !above()) return;
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) update(p);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> refresh(id));
        }
    }

    /** one tick later, after the event that changed things has finished */
    public void refreshSoon(UUID id) {
        if (!plugin.isEnabled() || !above()) return;
        Bukkit.getScheduler().runTask(plugin, () -> refresh(id));
    }

    /** dim the tag while sneaking, like a vanilla nametag */
    public void fade(Player p) {
        TextDisplay d = shown.get(p.getUniqueId());
        if (d != null && d.isValid()) d.setTextOpacity(p.isSneaking() ? (byte) 90 : (byte) -1);
    }

    public void forget(UUID id) {
        TextDisplay d = shown.remove(id);
        keys.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    /** put a dropped tag back on its player: after a teleport it is left behind */
    private void reattach(Player p, TextDisplay d) {
        if (!d.getWorld().equals(p.getWorld())) {
            forget(p.getUniqueId());
            return;
        }
        d.teleport(p.getLocation());
        p.addPassenger(d);
    }

    private void update(Player p) {
        boolean hide = p.getGameMode() == GameMode.SPECTATOR || p.isDead()
                || p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        UUID id = p.getUniqueId();
        TextDisplay d = shown.get(id);
        if (hide) {
            if (d != null) forget(id);
            return;
        }
        if (d != null && !d.isValid()) {
            forget(id);
            d = null;
        }
        if (d != null && !p.equals(d.getVehicle())) {
            reattach(p, d);
            d = shown.get(id);
        }
        int key = tierIndex(id);
        if (d == null) {
            for (var rider : new java.util.ArrayList<>(p.getPassengers())) { // never two tags on one player
                if (rider instanceof TextDisplay old && old.getPersistentDataContainer().has(MARK)) old.remove();
            }
            Component text = tag(key);
            d = p.getWorld().spawn(p.getLocation(), TextDisplay.class, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false); // no flash at the feet before it rides
                e.setBrightness(new Display.Brightness(15, 15));
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.getPersistentDataContainer().set(MARK, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setSeeThrough(true);
                e.setViewRange(0.5f);
                double up = plugin.getConfig().getDouble("tags.above-height", 0.9);
                e.setTransformation(new Transformation(new Vector3f(0, (float) up, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
                e.text(text);
            });
            p.addPassenger(d);
            p.hideEntity(plugin, d);
            d.setVisibleByDefault(true);
            shown.put(id, d);
            fade(p);
            keys.put(id, key);
            return;
        }
        Integer old = keys.get(id);
        if (old == null || old != key) {
            d.text(tag(key));
            keys.put(id, key);
        }
    }

    /** tier in the kit they are playing, else their best tier; -1 = unranked */
    private int tierIndex(UUID id) {
        var st = plugin.stats().peek(id);
        if (st == null) return -1;
        var duel = plugin.duels().duelOf(id);
        return duel != null ? st.tierIndex(duel.kit.id) : st.bestTier();
    }

    private Component tag(int idx) {
        if (idx < 0) return Component.text(dev.mintpvp.util.SmallCaps.of("unranked"), NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true);
        String label = (idx == Tier.values().length - 1 ? "★" : "") + dev.mintpvp.util.SmallCaps.of(Tier.name(idx));
        return Component.text(label, Tier.at(idx).color).decoration(TextDecoration.BOLD, true);
    }
}
