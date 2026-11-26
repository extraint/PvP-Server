package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.managers.QueueManager;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** custom kit menus */
public final class CustomKits {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};
    private static final Map<UUID, Map<String, ItemStack[]>> DRAFTS = new HashMap<>();

    private CustomKits() {}

    /** unsaved edits survive closing the */
    static ItemStack[] draft(UUID who, Kit kit) {
        Map<String, ItemStack[]> mine = DRAFTS.get(who);
        ItemStack[] d = mine == null ? null : mine.get(kit.id);
        return d != null ? d : java.util.Arrays.copyOf(kit.contents, KitManager.SIZE);
    }

    static boolean hasDraft(UUID who, Kit kit) {
        Map<String, ItemStack[]> mine = DRAFTS.get(who);
        return mine != null && mine.containsKey(kit.id);
    }

    static void keepDraft(UUID who, Kit kit, ItemStack[] items) {
        DRAFTS.computeIfAbsent(who, k -> new HashMap<>()).put(kit.id, items);
    }

    static void dropDraft(UUID who, Kit kit) {
        Map<String, ItemStack[]> mine = DRAFTS.get(who);
        if (mine != null) mine.remove(kit.id);
    }

    public static void forget(UUID who) {
        DRAFTS.remove(who);
    }

    /** your custom kits */
    public static void menu(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        Gui g = new Gui(plugin, 3, t.title("custom kits"));
        g.frame(true);
        List<Kit> mine = plugin.kits().customOf(p.getUniqueId());
        int max = plugin.kits().maxCustom();
        for (int i = 0; i < SLOTS.length; i++) {
            int slot = SLOTS[i];
            if (i >= max) {
                g.set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE).name(t.text("locked", t.muted())).build());
            } else if (i < mine.size()) {
                Kit k = mine.get(i);
                g.set(slot, Items.of(k.icon).name(t.text(k.display, t.primary()))
                        .lore(t.text(k.itemCount() + " items", t.accent()), Component.empty(), t.text("click to manage", t.secondary())).glow().build(),
                        e -> manage(plugin, p, k, () -> menu(plugin, p, back)));
            } else {
                g.set(slot, Items.of(Material.LIME_DYE).name(t.text("✦ new custom kit", t.primary()))
                        .lore(t.text("name it, then add any items", t.accent()), Component.empty(), t.text("click to create", t.secondary())).build(),
                        e -> create(plugin, p, back));
            }
        }
        g.set(4, Items.of(Material.PAPER).name(t.text("custom kits " + mine.size() + "/" + max, t.primary()))
                .lore(t.text("not ranked - duel friends with them", t.accent()),
                        t.text("or host them in the casual queue", t.accent())).build());
        g.set(18, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> { if (back != null) back.run(); else p.closeInventory(); });
        g.open(p);
    }

    private static void create(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        if (plugin.kits().customOf(p.getUniqueId()).size() >= plugin.kits().maxCustom()) {
            p.sendMessage(t.error("you already have " + plugin.kits().maxCustom() + " custom kits - delete one first."));
            return;
        }
        plugin.chatPrompt().ask(p, "name your new kit (1-16 letters, numbers, spaces).", name -> {
            if (name == null) { menu(plugin, p, back); return; }
            String problem = plugin.kits().nameProblem(p.getUniqueId(), name, null);
            if (problem != null) {
                p.sendMessage(t.error(problem));
                menu(plugin, p, back);
                return;
            }
            Kit k = plugin.kits().createCustom(p.getUniqueId(), name);
            if (k == null) {
                p.sendMessage(t.error("couldn't create the kit."));
                menu(plugin, p, back);
                return;
            }
            p.sendMessage(t.info("created " + k.display + ". add some items!"));
            new CustomKitEditorGui(plugin, p, k, () -> menu(plugin, p, back)).open(p);
        });
    }

    /** edit, rename */
    public static void manage(MintPvP plugin, Player p, Kit kit, Runnable back) {
        Theme t = plugin.theme();
        Gui g = new Gui(plugin, 3, t.title(kit.display));
        g.frame(true);
        g.set(4, Items.of(kit.icon).name(t.text(kit.display, t.primary()))
                .lore(t.text(kit.itemCount() + " items", t.accent())).build());
        g.set(10, Items.of(Material.ANVIL).name(t.text("✦ edit items", t.primary()))
                .lore(t.text("add any item, enchant, arrange", t.accent()),
                        hasDraft(p.getUniqueId(), kit) ? t.text("you have unsaved changes", t.secondary()) : Component.empty()).glow().build(),
                e -> new CustomKitEditorGui(plugin, p, kit, () -> manage(plugin, p, kit, back)).open(p));
        g.set(12, Items.of(Material.NAME_TAG).name(t.text("✦ rename", t.primary())).build(), e ->
                plugin.chatPrompt().ask(p, "type the new name for " + kit.display + ".", name -> {
                    if (name != null) {
                        String problem = plugin.kits().nameProblem(p.getUniqueId(), name, kit);
                        if (problem != null) p.sendMessage(t.error(problem));
                        else {
                            plugin.kits().renameCustom(kit, name);
                            p.sendMessage(t.info("renamed to " + kit.display + "."));
                        }
                    }
                    manage(plugin, p, kit, back);
                }));
        var hosting = plugin.queue().entryOf(p.getUniqueId());
        boolean hosted = hosting != null && hosting.type() == QueueManager.Type.HOST && hosting.kit() == kit;
        g.set(14, Items.of(hosted ? Material.REDSTONE : Material.GOLDEN_SWORD).name(t.text(hosted ? "✦ stop hosting" : "✦ host this kit", t.primary()))
                .lore(t.text("others can join it from the", t.accent()), t.text("casual queue's custom kits", t.accent())).build(), e -> {
            p.closeInventory();
            if (hosted) {
                plugin.queue().leave(p.getUniqueId());
                p.sendMessage(t.info("you stopped hosting."));
            } else {
                if (hosting != null) plugin.queue().leave(p.getUniqueId());
                plugin.queue().join(p, kit, QueueManager.Type.HOST);
            }
        });
        g.set(16, Items.of(Material.TNT).name(t.text("✦ delete", t.secondary()))
                .lore(t.text("shift-click to delete for good", t.muted())).build(), e -> {
            if (!e.isShiftClick()) { p.sendMessage(t.error("shift-click to delete " + kit.display + ".")); return; }
            plugin.kits().deleteCustom(kit);
            dropDraft(p.getUniqueId(), kit);
            p.sendMessage(t.info("deleted " + kit.display + "."));
            menu(plugin, p, null);
        });
        g.set(18, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        g.open(p);
    }

    /** host a kit or join */
    public static void lobby(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        Gui g = new Gui(plugin, 6, t.title("custom kit games"));
        g.frame(true);
        g.set(4, Items.of(Material.PAPER).name(t.text("casual custom kits", t.primary()))
                .lore(t.text("host one of yours, or join a host", t.accent()), t.text("never ranked", t.muted())).build());
        List<Kit> mine = plugin.kits().customOf(p.getUniqueId());
        var current = plugin.queue().entryOf(p.getUniqueId());
        for (int i = 0; i < SLOTS.length; i++) {
            int slot = SLOTS[i];
            if (i < mine.size()) {
                Kit k = mine.get(i);
                boolean hosted = current != null && current.type() == QueueManager.Type.HOST && current.kit() == k;
                g.set(slot, Items.of(k.icon).name(t.text(k.display, t.primary()))
                        .lore(t.text(k.itemCount() + " items", t.accent()), Component.empty(),
                                t.text(hosted ? "hosting - click to stop" : "click to host", t.secondary())).build(), e -> {
                    p.closeInventory();
                    if (hosted) {
                        plugin.queue().leave(p.getUniqueId());
                        p.sendMessage(t.info("you stopped hosting."));
                    } else {
                        if (current != null) plugin.queue().leave(p.getUniqueId());
                        plugin.queue().join(p, k, QueueManager.Type.HOST);
                    }
                });
            } else {
                g.set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE).name(t.text("empty slot", t.muted()))
                        .lore(t.text("make kits under edit kits", t.muted())).build());
            }
        }
        g.set(22, Items.of(Material.OAK_SIGN).name(t.text("hosted games", t.primary()))
                .lore(t.text("click a kit to play it right now", t.accent())).build());
        int[] spots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int n = 0;
        for (var e : plugin.queue().hosts()) {
            if (n >= spots.length) break;
            Player host = Bukkit.getPlayer(e.id());
            if (host == null) continue;
            Kit k = e.kit();
            boolean own = host.equals(p);
            g.set(spots[n++], Items.of(k.icon).name(t.text(k.display, t.primary()))
                    .lore(t.line("host", host.getName()), t.text(k.itemCount() + " items", t.muted()), Component.empty(),
                            t.text(own ? "this is yours" : "click to play", t.secondary())).glow().build(), own ? null : ev -> {
                p.closeInventory();
                plugin.queue().joinHost(p, e.id());
            });
        }
        if (n == 0) g.set(31, Items.of(Material.BARRIER).name(t.text("nobody is hosting right now", t.muted())).build());
        g.set(45, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> { if (back != null) back.run(); else p.closeInventory(); });
        g.set(49, Items.of(Material.CHEST).name(t.text("✦ edit my custom kits", t.primary())).build(), e -> menu(plugin, p, () -> lobby(plugin, p, back)));
        g.set(53, Items.of(Material.CLOCK).name(t.text("refresh", t.secondary())).build(), e -> lobby(plugin, p, back));
        g.open(p);
    }

    /** kits a party can use */
    static List<Kit> partyKits(MintPvP plugin, java.util.Collection<UUID> members) {
        List<Kit> out = new ArrayList<>(plugin.kits().all());
        for (UUID id : members) out.addAll(plugin.kits().customOf(id));
        return out;
    }
}
