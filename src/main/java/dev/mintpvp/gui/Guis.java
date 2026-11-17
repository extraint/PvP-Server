package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.DuelManager.Duel;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.managers.QueueManager;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import dev.mintpvp.util.Tier;
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

/** shared pickers */
public final class Guis {

    private Guis() {}

    /** kit icon */
    public static ItemStack kitIcon(MintPvP plugin, Kit kit, List<Component> extra) {
        Theme t = plugin.theme();
        List<Component> lore = new ArrayList<>();
        lore.add(t.text("✦ " + kit.itemCount() + " items", t.muted()));
        lore.addAll(extra);
        return Items.of(kit.icon).name(t.text(kit.display, t.primary())).lore(lore).build();
    }

    /** kit list */
    public static void pickKit(MintPvP plugin, Player p, String title, Runnable back,
                               java.util.function.Function<Kit, List<Component>> detail, Consumer<Kit> onPick) {
        List<PagedGui.Entry> entries = new ArrayList<>();
        for (Kit kit : plugin.kits().all()) {
            entries.add(new PagedGui.Entry(kitIcon(plugin, kit, detail.apply(kit)), e -> onPick.accept(kit)));
        }
        new PagedGui(plugin, plugin.theme().title(title), entries, back, "no kits yet").open(p);
    }

    /** player list */
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

    /** running duels */
    public static void spectateList(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        List<PagedGui.Entry> entries = new ArrayList<>();
        for (Duel d : plugin.duels().activeDuels()) {
            ItemStack icon = Items.of(d.kit.icon)
                    .name(t.raw(d.title(), t.primary()))
                    .lore(t.text("kit: " + d.kit.display, t.accent()),
                            t.text("arena: " + d.arena.name, t.accent()),
                            t.text(d.spectators.size() + " watching", t.muted()),
                            Component.empty(),
                            t.text("click to spectate", t.secondary()))
                    .build();
            entries.add(new PagedGui.Entry(icon, e -> {
                p.closeInventory();
                plugin.duels().spectate(p, d.first().getName());
            }));
        }
        new PagedGui(plugin, t.title("spectate"), entries, back, "no duels running").open(p);
    }

    /** queue picker */
    public static void queueKits(MintPvP plugin, Player p, QueueManager.Type type, Runnable back) {
        Theme t = plugin.theme();
        boolean ranked = type == QueueManager.Type.RANKED;
        if (ranked && !plugin.queue().rankedEnabled()) {
            p.sendMessage(t.error("ranked is disabled right now."));
            return;
        }
        pickKit(plugin, p, ranked ? "ranked queue" : "queue", back, kit -> {
            QueueManager q = plugin.queue();
            List<Component> lore = new ArrayList<>();
            lore.add(t.text(q.count(kit, type) + " in queue", t.accent()));
            if (ranked) {
                lore.add(t.text("your tier: " + Tier.name(plugin.stats().get(p.getUniqueId()).tierIndex(kit.id)), t.muted()));
            }
            lore.add(Component.empty());
            var mine = q.entryOf(p.getUniqueId());
            boolean here = mine != null && mine.kit() == kit && mine.type() == type;
            lore.add(t.text(here ? "click to leave the queue" : "click to join", t.secondary()));
            return lore;
        }, kit -> {
            p.closeInventory();
            var mine = plugin.queue().entryOf(p.getUniqueId());
            if (mine != null && mine.kit() == kit && mine.type() == type) {
                plugin.queue().leave(p.getUniqueId());
                p.sendMessage(t.info("you left the queue."));
            } else {
                if (mine != null) plugin.queue().leave(p.getUniqueId());
                plugin.queue().join(p, kit, type);
            }
        });
    }

    /** one challenge flow: player, then ranked or friendly, then kit (and rounds) */
    public static void challenge(MintPvP plugin, Player p, Runnable back) {
        pickPlayer(plugin, p, "challenge", back, target -> challengeMode(plugin, p, target, () -> challenge(plugin, p, back)));
    }

    /** ranked or friendly */
    public static void challengeMode(MintPvP plugin, Player p, Player target, Runnable back) {
        Theme t = plugin.theme();
        Gui g = new Gui(plugin, 3, t.title("challenge"));
        g.frame(true);
        g.set(11, Items.of(Material.IRON_SWORD).name(t.text("✦ friendly duel", t.primary()))
                .lore(t.text("you pick the kit and rounds", t.accent()), Component.empty(), t.text("click to continue", t.secondary()))
                .build(), e -> kitAndRounds(plugin, p, target, false, () -> challengeMode(plugin, p, target, back)));
        boolean ranked = plugin.queue().rankedEnabled();
        g.set(15, Items.of(Material.DIAMOND_SWORD).name(t.text("✦ ranked duel", ranked ? t.primary() : t.muted()))
                .lore(t.text(ranked ? "counts for your tier, first to " + plugin.duels().rankedRounds() : "ranked is disabled", t.accent()),
                        Component.empty(), t.text(ranked ? "click to continue" : "unavailable", t.secondary()))
                .glow().build(), ranked ? e -> kitAndRounds(plugin, p, target, true, () -> challengeMode(plugin, p, target, back)) : null);
        g.set(18, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        g.open(p);
    }

    /** ranked challenge flow */
    public static void challenge(MintPvP plugin, Player p, Runnable back, boolean ranked) {
        pickPlayer(plugin, p, ranked ? "ranked challenge" : "challenge", back, target -> kitAndRounds(plugin, p, target, ranked,
                () -> challenge(plugin, p, back, ranked)));
    }

    /** kit then rounds */
    public static void kitAndRounds(MintPvP plugin, Player p, Player target, boolean ranked, Runnable back) {
        pickKit(plugin, p, "choose kit", back,
                kit -> List.of(plugin.theme().text(ranked ? "first to " + plugin.duels().rankedRounds() + " · click to send" : "click to pick the rounds", plugin.theme().secondary())),
                kit -> {
                    if (ranked) {
                        sendRanked(plugin, p, target, kit.id);
                        return;
                    }
                    new RoundsGui(plugin, p, () -> kitAndRounds(plugin, p, target, ranked, back),
                            rounds -> plugin.duels().request(p, target, kit.id, null, false, rounds)).open(p);
                });
    }

    private static void sendRanked(MintPvP plugin, Player p, Player target, String kitId) {
        p.closeInventory();
        plugin.duels().request(p, target, kitId, null, true, plugin.duels().rankedRounds());
    }

    /** rounds only */
    public static void roundsOnly(MintPvP plugin, Player p, Player target, String kitId, String arena, boolean ranked) {
        new RoundsGui(plugin, p, null, rounds -> plugin.duels().request(p, target, kitId, arena, ranked, rounds)).open(p);
    }

    /** layout kit picker */
    public static void layoutKits(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        pickKit(plugin, p, "kit layout", back, kit -> List.of(
                t.text(plugin.kits().hasCustomLayout(p.getUniqueId(), kit) ? "custom layout saved" : "default layout", t.accent()),
                Component.empty(),
                t.text("click to edit", t.secondary())),
                kit -> new LayoutEditorGui(plugin, p, kit, false, () -> layoutKits(plugin, p, back)).open(p));
    }
}
