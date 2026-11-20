package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A floating label above every player's head: their tier tag, and in a team match their name in red or blue.
 * It is a text display that follows the player, so it works even when scoreboard teams don't show on nametags.
 */
public final class FloatingTags {

    private final MintPvP plugin;
    private final Map<UUID, TextDisplay> tags = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();
    private BukkitTask task;
    private int tick;

    public FloatingTags(MintPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("tags.floating", true)) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 20L, 2L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (TextDisplay td : tags.values()) if (td != null && td.isValid()) td.remove();
        tags.clear();
        shown.clear();
    }

    private void run() {
        tick++;
        try {
            Iterator<Map.Entry<UUID, TextDisplay>> it = tags.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                Player p = Bukkit.getPlayer(en.getKey());
                if (p == null || !p.isOnline()) {
                    if (en.getValue() != null) en.getValue().remove();
                    shown.remove(en.getKey());
                    it.remove();
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        } catch (RuntimeException e) {
            if (tick % 200 == 0) plugin.getLogger().warning("Floating tags failed: " + e);
        }
    }

    private Component label(Player p) {
        var st = plugin.stats().get(p.getUniqueId());
        var duel = plugin.duels().duelOf(p.getUniqueId());
        int idx = duel != null ? st.tierIndex(duel.kit.id) : st.bestTier();
        boolean team = duel != null && duel.mode == DuelManager.Mode.PARTY && duel.teamOf(p) >= 0;
        Component out = Component.empty();
        if (idx >= 0) {
            boolean king = idx == Tier.values().length - 1;
            out = out.append(Component.text((king ? "★" : "") + Tier.name(idx).toUpperCase(java.util.Locale.ROOT) + (team ? " " : ""),
                    Tier.at(idx).color).decoration(TextDecoration.BOLD, true));
        }
        if (team) {
            boolean blue = duel.teamOf(p) == 1;
            out = out.append(Component.text(p.getName(), blue ? NamedTextColor.BLUE : NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
        }
        return out;
    }

    private void update(Player p) {
        UUID id = p.getUniqueId();
        TextDisplay td = tags.get(id);
        Component text = p.getGameMode() == GameMode.SPECTATOR || p.isDead() ? Component.empty() : label(p);
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        if (plain.isEmpty()) {
            if (td != null && td.isValid()) td.remove();
            tags.remove(id);
            shown.remove(id);
            return;
        }
        Location at = p.getLocation().add(0, p.getHeight() + 0.75, 0);
        if (td == null || !td.isValid() || !td.getWorld().equals(at.getWorld())) {
            if (td != null && td.isValid()) td.remove();
            td = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setViewRange(0.5f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(3);
            });
            p.hideEntity(plugin, td); // you don't see your own
            tags.put(id, td);
            shown.remove(id);
        } else {
            td.teleport(at);
        }
        String key = text.toString();
        if (!key.equals(shown.get(id))) {
            td.text(text);
            shown.put(id, key);
        }
    }
}
