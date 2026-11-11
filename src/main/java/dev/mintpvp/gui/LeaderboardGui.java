package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.StatsManager.Stats;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import dev.mintpvp.util.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Top players with tabs for elo / duel wins / kills / best streak. */
public final class LeaderboardGui extends Gui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public LeaderboardGui(MintPvP plugin, Player viewer, String mode, Runnable back) {
        super(plugin, 5, plugin.theme().title("leaderboard"));
        Theme t = plugin.theme();
        frame(true);

        Comparator<Stats> by = switch (mode) {
            case "wins" -> Comparator.comparingInt(s -> s.duelWins);
            case "kills" -> Comparator.comparingInt(s -> s.kills);
            case "streak" -> Comparator.comparingInt(s -> s.bestStreak);
            default -> Comparator.comparingInt(s -> s.elo);
        };

        List<Map.Entry<UUID, Stats>> all = plugin.stats().top(by, Integer.MAX_VALUE);
        for (int i = 0; i < SLOTS.length && i < all.size(); i++) {
            var entry = all.get(i);
            set(SLOTS[i], head(plugin, t, i + 1, entry.getKey(), entry.getValue(), mode));
        }

        int myRank = 0;
        for (int i = 0; i < all.size(); i++) if (all.get(i).getKey().equals(viewer.getUniqueId())) myRank = i + 1;
        Stats mine = plugin.stats().get(viewer.getUniqueId());
        set(4, Items.of(Material.NETHER_STAR).name(t.text("your standing", t.primary()))
                .lore(t.line("rank", myRank > 0 ? "#" + myRank : "unranked"),
                        t.line(label(mode), value(mine, mode)),
                        t.line("tier", Tier.of(mine.elo).label))
                .glow().build());

        tab(t, 37, "elo", Material.DIAMOND_SWORD, mode, plugin, viewer, back);
        tab(t, 39, "wins", Material.IRON_SWORD, mode, plugin, viewer, back);
        tab(t, 41, "kills", Material.SKELETON_SKULL, mode, plugin, viewer, back);
        tab(t, 43, "streak", Material.BLAZE_POWDER, mode, plugin, viewer, back);

        if (back != null) {
            set(36, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(36, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(),
                    e -> e.getWhoClicked().closeInventory());
        }
    }

    private void tab(Theme t, int slot, String id, Material icon, String current, MintPvP plugin, Player viewer, Runnable back) {
        Items b = Items.of(icon).name(t.text(label(id), id.equals(current) ? t.primary() : t.muted()))
                .lore(t.text(id.equals(current) ? "showing now" : "click to view", t.accent()));
        if (id.equals(current)) b.glow();
        set(slot, b.build(), id.equals(current) ? null : e -> new LeaderboardGui(plugin, viewer, id, back).open(viewer));
    }

    private static String label(String mode) {
        return switch (mode) {
            case "wins" -> "duel wins";
            case "kills" -> "kills";
            case "streak" -> "best streak";
            default -> "elo";
        };
    }

    private static String value(Stats s, String mode) {
        return switch (mode) {
            case "wins" -> String.valueOf(s.duelWins);
            case "kills" -> String.valueOf(s.kills);
            case "streak" -> String.valueOf(s.bestStreak);
            default -> s.elo + " · " + Tier.of(s.elo).label;
        };
    }

    private static ItemStack head(MintPvP plugin, Theme t, int rank, UUID id, Stats s, String mode) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        meta.displayName(Component.text("#" + rank + " ", t.secondary())
                .append(t.raw(plugin.stats().nameOf(id), t.primary()))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(t.line(label(mode), value(s, mode)).decoration(TextDecoration.ITALIC, false));
        lore.add(t.line("duels", s.duelWins + "w / " + s.duelLosses + "l").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }
}
