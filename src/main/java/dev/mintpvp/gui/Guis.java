package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.DuelManager.Duel;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.managers.QueueManager;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Reusable pickers shared by the hub, queue and challenge flows. */
public final class Guis {

    private Guis() {}

    /** The icon shown for a kit anywhere in the GUIs. */
    public static ItemStack kitIcon(MintPvP plugin, Kit kit, List<Component> extra) {
        Theme t = plugin.theme();
        List<Component> lore = new ArrayList<>();
        lore.add(t.text("✦ " + kit.itemCount() + " items", t.muted()));
        lore.addAll(extra);
        return Items.of(kit.icon).name(t.text(kit.display, t.primary())).lore(lore).build();
    }

    /** Paged list of every kit; {@code detail} adds extra lore lines per kit. */
    public static void pickKit(MintPvP plugin, Player p, String title, Runnable back,
                               java.util.function.Function<Kit, List<Component>> detail, Consumer<Kit> onPick) {
        List<PagedGui.Entry> entries = new ArrayList<>();
        for (Kit kit : plugin.kits().all()) {
            entries.add(new PagedGui.Entry(kitIcon(plugin, kit, detail.apply(kit)), e -> onPick.accept(kit)));
        }
        new PagedGui(plugin, plugin.theme().title(title), entries, back, "no kits yet").open(p);
    }

    /** Paged list of online players (excluding the viewer). */
    public static void pickPlayer(MintPvP plugin, Player p, String title, Runnable back, Consumer<Player> onPick) {
        Theme t = plugin.theme();
        List<PagedGui.Entry> entries = new ArrayList<>();
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (Player other : online) {
            if (other.equals(p)) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(other);
            meta.displayName(t.raw(other.getName(), t.primary()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            boolean busy = plugin.duels().inDuel(other.getUniqueId()) || plugin.duels().isSpectating(other.getUniqueId());
            meta.lore(List.of(t.text(busy ? "busy right now" : "click to challenge", busy ? t.muted() : t.accent())
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            head.setItemMeta(meta);
            entries.add(new PagedGui.Entry(head, e -> onPick.accept(other)));
        }
        new PagedGui(plugin, t.title(title), entries, back, "no other players online").open(p);
    }

    /** Running duels, click to spectate. */
    public static void spectateList(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        List<PagedGui.Entry> entries = new ArrayList<>();
        for (Duel d : plugin.duels().activeDuels()) {
            ItemStack icon = Items.of(d.kit.icon)
                    .name(t.raw(d.a.getName() + " vs " + d.b.getName(), t.primary()))
                    .lore(t.text("kit: " + d.kit.display, t.accent()),
                            t.text("arena: " + d.arena.name, t.accent()),
                            t.text(d.spectators.size() + " watching", t.muted()),
                            Component.empty(),
                            t.text("click to spectate", t.secondary()))
                    .build();
            entries.add(new PagedGui.Entry(icon, e -> {
                p.closeInventory();
                plugin.duels().spectate(p, d.a.getName());
            }));
        }
        new PagedGui(plugin, t.title("spectate"), entries, back, "no duels running").open(p);
    }

    /** Pick a kit, then join the unranked queue for it (clicking your own queue leaves it). */
    public static void queueKits(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        pickKit(plugin, p, "queue", back, kit -> {
            QueueManager q = plugin.queue();
            List<Component> lore = new ArrayList<>();
            lore.add(t.text(q.count(kit, QueueManager.Type.UNRANKED) + " in queue", t.accent()));
            lore.add(Component.empty());
            var mine = q.entryOf(p.getUniqueId());
            boolean here = mine != null && mine.kit() == kit;
            lore.add(t.text(here ? "click to leave the queue" : "click to join", t.secondary()));
            return lore;
        }, kit -> {
            p.closeInventory();
            var mine = plugin.queue().entryOf(p.getUniqueId());
            if (mine != null && mine.kit() == kit) {
                plugin.queue().leave(p.getUniqueId());
                p.sendMessage(t.info("you left the queue."));
            } else {
                if (mine != null) plugin.queue().leave(p.getUniqueId());
                plugin.queue().join(p, kit, QueueManager.Type.UNRANKED);
            }
        });
    }

    /** Pick a player, then a kit, then send the duel request. */
    public static void challenge(MintPvP plugin, Player p, Runnable back) {
        pickPlayer(plugin, p, "challenge", back, target -> pickKit(plugin, p, "choose kit",
                () -> challenge(plugin, p, back),
                kit -> List.of(plugin.theme().text("click to send the challenge", plugin.theme().secondary())),
                kit -> {
                    p.closeInventory();
                    plugin.duels().request(p, target, kit.id, null);
                }));
    }

    /** Pick a kit, then open the layout editor for it. */
    public static void layoutKits(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        pickKit(plugin, p, "kit layout", back, kit -> List.of(
                t.text(plugin.kits().hasCustomLayout(p.getUniqueId(), kit) ? "custom layout saved" : "default layout", t.accent()),
                Component.empty(),
                t.text("click to edit", t.secondary())),
                kit -> new LayoutEditorGui(plugin, p, kit, false, () -> layoutKits(plugin, p, back)).open(p));
    }
}
