package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Admin kit manager: list kits, then edit layout / set icon / re-capture / delete. */
public final class KitAdminGui extends PagedGui {

    public KitAdminGui(MintPvP plugin, Player p, Runnable back) {
        super(plugin, plugin.theme().title("kit admin"), entries(plugin, p, back), back,
                "no kits - use /kit create <name>");
    }

    private static List<Entry> entries(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        List<Entry> out = new ArrayList<>();
        for (Kit kit : plugin.kits().all()) {
            ItemStack icon = Guis.kitIcon(plugin, kit, List.of(Component.empty(), t.text("click to manage", t.secondary())));
            out.add(new Entry(icon, e -> options(plugin, p, kit, () -> new KitAdminGui(plugin, p, back).open(p)).open(p)));
        }
        return out;
    }

    private static Gui options(MintPvP plugin, Player p, Kit kit, Runnable back) {
        Theme t = plugin.theme();
        Gui g = new Gui(plugin, 3, t.title("kit - " + kit.display));
        g.frame(true);

        g.set(4, Guis.kitIcon(plugin, kit, List.of()));

        g.set(10, Items.of(Material.CRAFTING_TABLE).name(t.text("edit default layout", t.primary()))
                .lore(t.text("rearrange the kit's slots", t.accent()),
                        t.text("(players keep their own layouts)", t.muted())).build(),
                e -> new LayoutEditorGui(plugin, p, kit, true, () -> options(plugin, p, kit, back).open(p)).open(p));

        g.set(12, Items.of(Material.ITEM_FRAME).name(t.text("set icon", t.primary()))
                .lore(t.text("hold an item and click here,", t.accent()),
                        t.text("or click with it on your cursor.", t.accent())).build(), e -> {
            ItemStack src = !Items.isEmpty(e.getCursor()) ? e.getCursor() : p.getInventory().getItemInMainHand();
            if (Items.isEmpty(src)) {
                p.sendMessage(t.error("hold an item (or carry one on your cursor) to use as the icon."));
                return;
            }
            kit.icon = src.getType();
            plugin.kits().saveKits();
            p.sendMessage(t.info("icon updated."));
            options(plugin, p, kit, back).open(p);
        });

        g.set(14, Items.of(Material.HOPPER).name(t.text("re-capture from my inventory", t.primary()))
                .lore(t.text("replace the kit with what you're", t.accent()),
                        t.text("carrying right now. player layouts", t.accent()),
                        t.text("for it reset if the items changed.", t.muted())).build(), e -> {
            plugin.kits().recapture(kit, p);
            p.sendMessage(t.info("kit updated from your inventory."));
            options(plugin, p, kit, back).open(p);
        });

        g.set(16, Items.of(Material.TNT).name(t.text("delete kit", t.secondary()))
                .lore(t.text("this can't be undone", t.muted())).build(), e -> confirmDelete(plugin, p, kit, back).open(p));

        g.set(22, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        return g;
    }

    private static Gui confirmDelete(MintPvP plugin, Player p, Kit kit, Runnable back) {
        Theme t = plugin.theme();
        Gui g = new Gui(plugin, 3, t.title("delete " + kit.display + "?"));
        g.frame(true);
        g.set(11, Items.of(Material.LIME_CONCRETE).name(t.text("yes, delete it", t.primary()))
                .lore(t.text("removes the kit and all player layouts", t.muted())).build(), e -> {
            p.closeInventory();
            if (plugin.kits().all().size() <= 1) {
                p.sendMessage(t.error("you can't delete the last kit."));
                return;
            }
            plugin.queue().leaveKit(kit);
            plugin.kits().delete(kit.id);
            p.sendMessage(t.info("kit deleted."));
        });
        g.set(15, Items.of(Material.RED_CONCRETE).name(t.text("cancel", t.secondary())).build(),
                e -> options(plugin, p, kit, back).open(p));
        return g;
    }
}
