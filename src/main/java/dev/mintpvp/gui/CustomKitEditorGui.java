package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** custom kit item editor */
public final class CustomKitEditorGui extends Gui {

    private static final int[] ARMOR_GUI = {45, 46, 47, 48, 49};
    private static final int[] ARMOR_KIT = {39, 38, 37, 36, 40};
    private static final String[] ARMOR_NAME = {"helmet", "chestplate", "leggings", "boots", "offhand"};

    private final Kit kit;
    private final Player viewer;
    private final Runnable back;
    private boolean skipKeep;

    public CustomKitEditorGui(MintPvP plugin, Player p, Kit kit, Runnable back) {
        super(plugin, 6, plugin.theme().title("edit " + kit.display));
        this.kit = kit;
        this.viewer = p;
        this.back = back;
        Theme t = plugin.theme();

        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).blank().build();
        for (int i = 0; i < 9; i++) set(i, dark);
        for (int i = 50; i < 54; i++) set(i, dark);

        ItemStack[] draft = CustomKits.draft(p.getUniqueId(), kit);
        for (int g = 9; g < 45; g++) {
            int ks = kitSlot(g);
            ItemStack it = draft[ks];
            inv.setItem(g, Items.isEmpty(it) ? null : it.clone());
        }
        for (int i = 0; i < ARMOR_GUI.length; i++) {
            ItemStack it = draft[ARMOR_KIT[i]];
            inv.setItem(ARMOR_GUI[i], Items.isEmpty(it) ? placeholder(ARMOR_NAME[i]) : it.clone());
        }

        set(0, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary()))
                .lore(t.text("keeps your unsaved changes", t.muted())).build(), e -> leave(false));
        set(1, Items.of(Material.COMPASS).name(t.text("✦ add items", t.primary()))
                .lore(t.text("every item in the game", t.accent()), t.text("except spawn eggs", t.muted())).glow().build(), e -> addItems());
        set(3, Items.of(Material.LAVA_BUCKET).name(t.text("clear everything", t.secondary()))
                .lore(t.text("shift-click to empty the kit", t.muted())).build(), e -> {
            if (!e.isShiftClick()) { viewer.sendMessage(t.error("shift-click to clear the kit.")); return; }
            for (int g = 9; g < 45; g++) inv.setItem(g, null);
            for (int i = 0; i < ARMOR_GUI.length; i++) inv.setItem(ARMOR_GUI[i], placeholder(ARMOR_NAME[i]));
        });
        set(4, Items.of(Material.PAPER).name(t.text("how it works", t.primary()))
                .lore(t.text("click an item to move it", t.accent()),
                        t.text("shift-click: enchant and options", t.accent()),
                        t.text("q: remove it, or drop it on the bin", t.accent()),
                        t.text("bottom row: armor and offhand", t.muted()),
                        t.text("row 1 is the hotbar", t.muted())).build());
        set(5, Items.of(Material.CAULDRON).name(t.text("bin", t.secondary()))
                .lore(t.text("drop an item here to delete it", t.muted())).build(), e -> {
            if (!Items.isEmpty(e.getCursor())) viewer.setItemOnCursor(null);
        });
        set(7, Items.of(Material.BARRIER).name(t.text("discard changes", t.secondary()))
                .lore(t.text("shift-click to reset to saved", t.muted())).build(), e -> {
            if (!e.isShiftClick()) { viewer.sendMessage(t.error("shift-click to discard.")); return; }
            skipKeep = true;
            CustomKits.dropDraft(p.getUniqueId(), kit);
            new CustomKitEditorGui(plugin, p, kit, back).open(p);
        });
        set(8, Items.of(Material.EMERALD).name(t.text("save kit", t.primary()))
                .lore(t.text("keep this kit", t.accent())).glow().build(), e -> save());
    }

    private ItemStack placeholder(String what) {
        return Items.of(Material.GRAY_STAINED_GLASS_PANE).name(plugin.theme().text(what, plugin.theme().muted()))
                .lore(plugin.theme().text("put " + (what.equals("offhand") ? "any item" : what) + " here", plugin.theme().muted())).tag("slot").build();
    }

    private static boolean isPlaceholder(ItemStack it) {
        return "slot".equals(Items.tagOf(it));
    }

    /** gui slot to kit slot */
    static int kitSlot(int g) {
        if (g >= 9 && g <= 17) return g - 9;
        if (g >= 18 && g <= 44) return g - 18 + 9;
        for (int i = 0; i < ARMOR_GUI.length; i++) if (ARMOR_GUI[i] == g) return ARMOR_KIT[i];
        return -1;
    }

    private static boolean fits(int kitSlot, ItemStack item) {
        if (kitSlot == 40 || kitSlot < 36) return true;
        EquipmentSlot want = switch (kitSlot) {
            case 39 -> EquipmentSlot.HEAD;
            case 38 -> EquipmentSlot.CHEST;
            case 37 -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
        return item.getType().getEquipmentSlot() == want;
    }

    ItemStack[] capture() {
        ItemStack[] out = new ItemStack[KitManager.SIZE];
        for (int g = 9; g < 54; g++) {
            int ks = kitSlot(g);
            if (ks < 0) continue;
            ItemStack it = inv.getItem(g);
            out[ks] = Items.isEmpty(it) || isPlaceholder(it) ? null : it.clone();
        }
        return out;
    }

    private boolean cursorBusy() {
        if (!Items.isEmpty(viewer.getItemOnCursor())) {
            viewer.sendMessage(plugin.theme().error("put down the item you're holding first."));
            return true;
        }
        return false;
    }

    private void addItems() {
        if (cursorBusy()) return;
        CustomKits.keepDraft(viewer.getUniqueId(), kit, capture());
        new ItemBrowserGui(plugin, viewer, kit, "", 0, 0, back).open(viewer);
    }

    private void leave(boolean saved) {
        if (cursorBusy()) return;
        if (!saved) CustomKits.keepDraft(viewer.getUniqueId(), kit, capture());
        if (back != null) back.run();
        else viewer.closeInventory();
    }

    private void save() {
        Theme t = plugin.theme();
        if (cursorBusy()) return;
        ItemStack[] items = capture();
        int n = 0;
        for (ItemStack i : items) if (!Items.isEmpty(i)) n++;
        if (n == 0) { viewer.sendMessage(t.error("add at least one item first.")); return; }
        skipKeep = true;
        plugin.kits().updateCustom(kit, items);
        CustomKits.dropDraft(viewer.getUniqueId(), kit);
        viewer.sendMessage(t.info("saved " + kit.display + " (" + n + " items)."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
        leave(true);
    }

    private void refill() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < ARMOR_GUI.length; i++) {
                if (Items.isEmpty(inv.getItem(ARMOR_GUI[i]))) inv.setItem(ARMOR_GUI[i], placeholder(ARMOR_NAME[i]));
            }
        });
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        int ks = kitSlot(raw);
        if (ks < 0) {
            super.handleClick(e);
            return;
        }
        ItemStack here = inv.getItem(raw);
        boolean empty = Items.isEmpty(here) || isPlaceholder(here);
        ClickType c = e.getClick();
        ItemStack cursor = e.getCursor();
        if (c == ClickType.SHIFT_LEFT || c == ClickType.SHIFT_RIGHT) {
            if (!empty && Items.isEmpty(cursor)) {
                CustomKits.keepDraft(viewer.getUniqueId(), kit, capture());
                new ItemOptionsGui(plugin, viewer, kit, ks, back).open(viewer);
            }
            return;
        }
        if (c == ClickType.DROP || c == ClickType.CONTROL_DROP) {
            if (!empty) {
                inv.setItem(raw, ks >= 36 ? placeholder(ARMOR_NAME[armorIndex(raw)]) : null);
            }
            return;
        }
        if (c != ClickType.LEFT && c != ClickType.RIGHT) return;
        if (!Items.isEmpty(cursor) && ks >= 36 && !fits(ks, cursor)) {
            viewer.sendMessage(plugin.theme().error("that doesn't fit in the " + ARMOR_NAME[armorIndex(raw)] + " slot."));
            return;
        }
        if (isPlaceholder(here)) {
            if (!Items.isEmpty(cursor)) {
                inv.setItem(raw, cursor.clone());
                viewer.setItemOnCursor(null);
            }
            return;
        }
        e.setCancelled(false);
        if (ks >= 36) refill();
    }

    private static int armorIndex(int gui) {
        for (int i = 0; i < ARMOR_GUI.length; i++) if (ARMOR_GUI[i] == gui) return i;
        return 0;
    }

    @Override
    public void onClose(Player p) {
        if (!Items.isEmpty(p.getItemOnCursor())) p.setItemOnCursor(null);
        if (!skipKeep) CustomKits.keepDraft(p.getUniqueId(), kit, capture());
    }
}
