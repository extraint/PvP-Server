package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Rearranges the 36 storage slots of a kit. Rows 1-3 are the main inventory and
 * row 4 is the hotbar, exactly like the real inventory screen. Armor is shown but
 * fixed. Items can only be moved around, never added or removed: on save the
 * layout must contain exactly the kit's items.
 *
 * The same editor is used by players (their personal layout) and admins (the
 * kit's default layout, admin = true).
 */
public final class LayoutEditorGui extends Gui {

    private static final int EDIT_SLOTS = 36;

    private final Kit kit;
    private final boolean admin;
    private final Player viewer;
    private final Runnable back;

    public LayoutEditorGui(MintPvP plugin, Player p, Kit kit, boolean admin, Runnable back) {
        super(plugin, 6, plugin.theme().title((admin ? "edit " : "layout - ") + kit.display));
        this.kit = kit;
        this.admin = admin;
        this.viewer = p;
        this.back = back;
        Theme t = plugin.theme();

        // editable area (not framed, so the slots stay free)
        ItemStack[] layout = admin ? kit.main() : plugin.kits().layoutOf(p.getUniqueId(), kit);
        load(layout);

        // decor rows
        ItemStack lime = Items.of(Material.LIME_STAINED_GLASS_PANE).blank().build();
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).blank().build();
        for (int i = 36; i < 54; i++) set(i, i < 45 ? lime : dark);

        // fixed armor / offhand preview
        preview(38, kit.contents[39], "helmet");
        preview(39, kit.contents[38], "chestplate");
        preview(40, kit.contents[37], "leggings");
        preview(41, kit.contents[36], "boots");
        preview(43, kit.contents[40], "offhand");

        set(49, Items.of(Material.PAPER).name(t.text("how it works", t.primary()))
                .lore(t.text("click an item, then click a slot", t.accent()),
                        t.text("to move it. bottom row = hotbar.", t.accent()),
                        t.text("you can only rearrange, not add.", t.muted()),
                        t.text("press save when you're done.", t.muted()))
                .build());

        set(46, Items.of(Material.REDSTONE).name(t.text(admin ? "revert" : "reset to default", t.secondary()))
                .lore(t.text("discard your changes", t.muted())).build(), e -> {
            if (!Items.isEmpty(e.getCursor())) { cursorWarn(p); return; }
            if (!admin) plugin.kits().resetLayout(p.getUniqueId(), kit);
            load(admin ? kit.main() : kit.main());
            p.sendMessage(t.info(admin ? "changes reverted." : "layout reset to the kit default."));
        });

        set(52, Items.of(Material.EMERALD).name(t.text("save layout", t.primary()))
                .lore(t.text("keep this arrangement", t.accent())).glow().build(), e -> save(e));

        if (back != null) {
            set(45, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(45, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> p.closeInventory());
        }
    }

    /** GUI slot -> real inventory slot (rows 1-3 = inv 9..35, row 4 = hotbar 0..8). */
    private static int toInv(int g) {
        return g < 27 ? g + 9 : g - 27;
    }

    private static int toGui(int invSlot) {
        return invSlot >= 9 ? invSlot - 9 : invSlot + 27;
    }

    private void load(ItemStack[] layout) {
        for (int i = 0; i < EDIT_SLOTS; i++) {
            ItemStack it = layout[i];
            inv.setItem(toGui(i), Items.isEmpty(it) ? null : it.clone());
        }
    }

    private void preview(int slot, ItemStack item, String what) {
        Theme t = plugin.theme();
        if (Items.isEmpty(item)) {
            set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE).name(t.text("no " + what, t.muted())).build());
        } else {
            set(slot, item.clone());
        }
    }

    private void cursorWarn(Player p) {
        p.sendMessage(plugin.theme().error("put down the item you're holding first."));
    }

    private void save(InventoryClickEvent e) {
        Theme t = plugin.theme();
        if (!Items.isEmpty(e.getCursor())) { cursorWarn(viewer); return; }
        ItemStack[] layout = new ItemStack[KitManager.MAIN];
        for (int g = 0; g < EDIT_SLOTS; g++) {
            ItemStack it = inv.getItem(g);
            layout[toInv(g)] = Items.isEmpty(it) ? null : it.clone();
        }
        if (!KitManager.sameItems(layout, kit.main())) {
            viewer.sendMessage(t.error("that layout doesn't match the kit's items - try reset and rearrange again."));
            return;
        }
        if (admin) {
            kit.setMain(layout);
            plugin.kits().saveKits();
            viewer.sendMessage(t.info("default layout saved for " + kit.display + "."));
        } else {
            plugin.kits().saveLayout(viewer.getUniqueId(), kit, layout);
            viewer.sendMessage(t.info("layout saved for " + kit.display + "."));
        }
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
        viewer.closeInventory();
    }

    /** Moving items inside rows 1-4 with plain left/right clicks is the only thing allowed. */
    @Override
    public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw >= 0 && raw < EDIT_SLOTS) {
            ClickType c = e.getClick();
            if (c == ClickType.LEFT || c == ClickType.RIGHT) e.setCancelled(false);
            return;
        }
        super.handleClick(e);
    }

    @Override
    public void onClose(Player p) {
        // anything still held on the cursor only exists in this editor: never hand it to the player
        if (!Items.isEmpty(p.getItemOnCursor())) p.setItemOnCursor(null);
    }
}
