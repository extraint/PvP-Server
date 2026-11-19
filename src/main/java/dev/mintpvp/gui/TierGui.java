package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
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

/** tier menu */
public final class TierGui {

    private TierGui() {}

    /** your tier in every kit */
    public static void open(MintPvP plugin, Player p, Runnable back) {
        openFor(plugin, p, p.getUniqueId(), back);
    }

    /** anyone's tiers, read only */
    public static void openFor(MintPvP plugin, Player p, UUID target, Runnable back) {
        Theme t = plugin.theme();
        Stats s = plugin.stats().get(target);
        boolean mine = target.equals(p.getUniqueId());
        int top = Tier.values().length - 1;
        List<PagedGui.Entry> entries = new ArrayList<>();
        entries.add(new PagedGui.Entry(Items.of(Material.NETHER_STAR)
                .name(t.text(mine ? "overall tier" : plugin.stats().nameOf(target) + " · overall tier", t.primary()))
                .lore(t.line("tier", Tier.overallLabel(s.score())),
                        t.line("ranked", s.rankedWins + "w / " + s.rankedLosses + "l"))
                .glow().build(), null));
        for (Kit kit : plugin.kits().all()) {
            int idx = s.tierIndex(kit.id);
            Tier tier = Tier.at(idx);
            List<Component> lore = new ArrayList<>();
            if (tier == null) {
                lore.add(t.line("tier", "unranked"));
                lore.add(t.text("win a ranked match to place", t.muted()));
            } else {
                lore.add(t.line("tier", tier.label + " · " + tier.points + " pts"));
                int prog = s.kitProgress.getOrDefault(kit.id, 0);
                int need = prog >= 0 ? plugin.stats().promoteWinsFor(idx) : plugin.stats().demoteLossesFor(idx);
                lore.add(t.line(prog >= 0 ? "promotion" : "demotion", dev.mintpvp.managers.StatsManager.progressText(prog, need)));
                int peak = plugin.stats().peakTier(s, kit.id);
                if (peak > idx) lore.add(t.line("peak", Tier.name(peak)));
                UUID king = plugin.stats().crownHolder(kit.id);
                if (idx == top) lore.add(t.text("you hold the HT1 crown", t.accent()));
                else if (idx == top - 1 && king != null) lore.add(t.text("beat " + plugin.stats().nameOf(king) + " to take HT1", t.accent()));
                if (idx >= plugin.stats().eliteFrom()) {
                    lore.add(t.text("elite tier: harder promotion, upset losses cost more", t.muted()));
                    long left = plugin.stats().daysUntilDecay(s, kit.id);
                    if (left >= 0) lore.add(t.line("decay in", left + "d without a ranked match"));
                }
            }
            UUID crown = plugin.stats().crownHolder(kit.id);
            if (crown != null && idx != top) lore.add(t.line("HT1 holder", plugin.stats().nameOf(crown)));
            lore.add(Component.empty());
            lore.add(t.text("click for top players", t.secondary()));
            ItemStack icon = Items.of(kit.icon).name(t.text(kit.display, t.primary())).lore(lore).build();
            entries.add(new PagedGui.Entry(icon, e -> top(plugin, p, kit, () -> openFor(plugin, p, target, back))));
        }
        new PagedGui(plugin, t.title(mine ? "tiers" : plugin.stats().nameOf(target) + " tiers"), entries, back, "no kits yet").open(p);
    }

    /** top players in a kit */
    public static void top(MintPvP plugin, Player p, Kit kit, Runnable back) {
        Theme t = plugin.theme();
        Comparator<Stats> by = Comparator.<Stats>comparingInt(x -> x.tierIndex(kit.id)).thenComparingInt(x -> x.kitProgress.getOrDefault(kit.id, 0));
        List<Map.Entry<UUID, Stats>> all = plugin.stats().top(by, Integer.MAX_VALUE);
        List<PagedGui.Entry> entries = new ArrayList<>();
        int rank = 1;
        for (var en : all) {
            int idx = en.getValue().tierIndex(kit.id);
            if (idx < 0) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(en.getKey()));
            meta.displayName(Component.text("#" + rank++ + " ", t.secondary())
                    .append(t.raw(plugin.stats().nameOf(en.getKey()), t.primary()))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(t.line("tier", Tier.name(idx) + " · " + Tier.at(idx).points + " pts").decoration(TextDecoration.ITALIC, false)));
            head.setItemMeta(meta);
            UUID who = en.getKey();
            entries.add(new PagedGui.Entry(head, e -> openFor(plugin, p, who, () -> top(plugin, p, kit, back))));
        }
        new PagedGui(plugin, t.title(kit.display + " top"), entries, back, "nobody ranked yet").open(p);
    }
}
