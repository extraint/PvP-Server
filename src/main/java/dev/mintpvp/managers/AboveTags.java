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
import org.bukkit.scheduler.BukkitTask;
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
    private final Map<UUID, TextDisplay> shown = new HashMap<>();
    private final Map<UUID, Component> texts = new HashMap<>();
    private BukkitTask task;

    public AboveTags(MintPvP plugin) {
        this.plugin = plugin;
    }

    /** true when the tag is the floating line instead of a prefix */
    public boolean above() {
        return plugin.getConfig().getString("tags.mode", "above").equalsIgnoreCase("above");
    }

    public void start() {
        stop();
        if (!above()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (TextDisplay d : shown.values()) if (d.isValid()) d.remove();
        shown.clear();
        texts.clear();
    }

    public void forget(UUID id) {
        TextDisplay d = shown.remove(id);
        texts.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    private void tick() {
        try {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
            shown.keySet().removeIf(id -> {
                if (Bukkit.getPlayer(id) != null) return false;
                TextDisplay d = shown.get(id);
                if (d != null && d.isValid()) d.remove();
                texts.remove(id);
                return true;
            });
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Floating tags failed: " + e);
        }
    }

    private void update(Player p) {
        boolean hide = p.getGameMode() == GameMode.SPECTATOR || p.isDead()
                || p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        TextDisplay d = shown.get(p.getUniqueId());
        if (hide) {
            if (d != null) forget(p.getUniqueId());
            return;
        }
        if (d != null && (!d.isValid() || !p.equals(d.getVehicle()) || !d.getWorld().equals(p.getWorld()))) {
            forget(p.getUniqueId());
            d = null;
        }
        Component text = tag(p.getUniqueId());
        if (d == null) {
            d = p.getWorld().spawn(p.getLocation(), TextDisplay.class, e -> {
                e.setPersistent(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setSeeThrough(false);
                e.setViewRange(0.5f);
                double up = plugin.getConfig().getDouble("tags.above-offset", 0.65);
                e.setTransformation(new Transformation(new Vector3f(0, (float) up, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
                e.text(text);
            });
            p.addPassenger(d);
            p.hideEntity(plugin, d);
            shown.put(p.getUniqueId(), d);
            texts.put(p.getUniqueId(), text);
            return;
        }
        if (!text.equals(texts.get(p.getUniqueId()))) {
            d.text(text);
            texts.put(p.getUniqueId(), text);
        }
    }

    /** tier in the kit they are playing, else their best tier */
    private Component tag(UUID id) {
        var st = plugin.stats().peek(id);
        int idx = -1;
        if (st != null) {
            var duel = plugin.duels().duelOf(id);
            idx = duel != null ? st.tierIndex(duel.kit.id) : st.bestTier();
        }
        if (idx < 0) return Component.text("UNRANKED", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true);
        String label = (idx == Tier.values().length - 1 ? "★" : "") + Tier.name(idx).toUpperCase(Locale.ROOT);
        return Component.text(label, Tier.at(idx).color).decoration(TextDecoration.BOLD, true);
    }
}
