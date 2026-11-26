package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** enchant and tweak one item */
public final class ItemOptionsGui extends Gui {

    private static final int[] SPOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44};

    private final Kit kit;
    private final Player viewer;
    private final int slot;
    private final Runnable back;
    private final boolean book;

    public ItemOptionsGui(MintPvP plugin, Player p, Kit kit, int slot, Runnable back) {
        super(plugin, 6, plugin.theme().title("enchant"));
        this.kit = kit;
        this.viewer = p;
        this.slot = slot;
        this.back = back;
        ItemStack[] draft = CustomKits.draft(p.getUniqueId(), kit);
        ItemStack item = draft[slot];
        this.book = item != null && item.getType() == Material.ENCHANTED_BOOK;
        render(draft, item);
    }

    private void render(ItemStack[] draft, ItemStack item) {
        Theme t = plugin.theme();
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).blank().build();
        for (int i = 0; i < 54; i++) set(i, dark);
        if (Items.isEmpty(item)) {
            back();
            return;
        }
        set(4, item.clone());
        set(0, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back to the kit", t.secondary())).build(), e -> back());

        List<Enchantment> options = new ArrayList<>();
        for (Enchantment en : Registry.ENCHANTMENT) {
            if (book || en.canEnchantItem(item)) options.add(en);
        }
        options.sort(Comparator.comparing(en -> en.getKey().getKey()));
        for (int i = 0; i < SPOTS.length && i < options.size(); i++) {
            Enchantment en = options.get(i);
            int level = level(item, en);
            List<Component> lore = new ArrayList<>();
            lore.add(t.text("level " + level + " / " + en.getMaxLevel(), level > 0 ? t.primary() : t.muted()));
            lore.add(Component.empty());
            lore.add(t.text("left-click: level up", t.secondary()));
            lore.add(t.text("right-click: level down", t.secondary()));
            lore.add(t.text("shift-click: max level", t.secondary()));
            var b = Items.of(level > 0 ? Material.ENCHANTED_BOOK : Material.BOOK)
                    .name(Component.translatable(en.translationKey()).color(level > 0 ? t.primary() : t.muted()))
                    .lore(lore);
            if (level > 0) b.glow();
            set(SPOTS[i], b.build(), e -> change(draft, en, e.isShiftClick() ? en.getMaxLevel() : e.isRightClick() ? level - 1 : level + 1));
        }
        if (options.isEmpty()) {
            set(22, Items.of(Material.BARRIER).name(t.text("this item can't be enchanted", t.muted())).build());
        }

        set(45, Items.of(Material.LAVA_BUCKET).name(t.text("remove all enchants", t.secondary())).build(), e -> {
            ItemStack it = draft[slot].clone();
            ItemMeta m = it.getItemMeta();
            m.removeEnchantments();
            if (m instanceof EnchantmentStorageMeta sm) sm.getStoredEnchants().keySet().forEach(sm::removeStoredEnchant);
            it.setItemMeta(m);
            draft[slot] = it;
            CustomKits.keepDraft(viewer.getUniqueId(), kit, draft);
            render(draft, it);
        });
        set(48, Items.of(Material.RED_DYE).name(t.text("amount -", t.secondary())).lore(t.text("shift-click: -8", t.muted())).build(),
                e -> amount(draft, e.isShiftClick() ? -8 : -1));
        set(49, Items.of(Material.PAPER).name(t.text("amount: " + item.getAmount(), t.primary())).build());
        set(50, Items.of(Material.LIME_DYE).name(t.text("amount +", t.primary())).lore(t.text("shift-click: +8", t.muted())).build(),
                e -> amount(draft, e.isShiftClick() ? 8 : 1));
        if (item.getType().getMaxDurability() > 0) {
            boolean un = item.getItemMeta().isUnbreakable();
            set(53, Items.of(Material.ANVIL).name(t.text("unbreakable: " + (un ? "on" : "off"), un ? t.primary() : t.muted()))
                    .lore(t.text("click to toggle", t.secondary())).build(), e -> {
                ItemStack it = draft[slot].clone();
                ItemMeta m = it.getItemMeta();
                m.setUnbreakable(!un);
                it.setItemMeta(m);
                draft[slot] = it;
                CustomKits.keepDraft(viewer.getUniqueId(), kit, draft);
                render(draft, it);
            });
        }
    }

    private int level(ItemStack item, Enchantment en) {
        return item.getItemMeta() instanceof EnchantmentStorageMeta sm ? sm.getStoredEnchantLevel(en) : item.getEnchantmentLevel(en);
    }

    private void change(ItemStack[] draft, Enchantment en, int wanted) {
        Theme t = plugin.theme();
        ItemStack it = draft[slot].clone();
        ItemMeta m = it.getItemMeta();
        int lv = Math.max(0, Math.min(en.getMaxLevel(), wanted));
        if (lv > 0) {
            Iterable<Enchantment> have = m instanceof EnchantmentStorageMeta sm ? sm.getStoredEnchants().keySet() : m.getEnchants().keySet();
            for (Enchantment other : have) {
                if (!other.equals(en) && other.conflictsWith(en)) {
                    viewer.sendMessage(t.error("that conflicts with an enchant already on the item."));
                    return;
                }
            }
        }
        if (m instanceof EnchantmentStorageMeta sm) {
            if (lv == 0) sm.removeStoredEnchant(en);
            else sm.addStoredEnchant(en, lv, true);
        } else {
            if (lv == 0) m.removeEnchant(en);
            else m.addEnchant(en, lv, true);
        }
        it.setItemMeta(m);
        draft[slot] = it;
        CustomKits.keepDraft(viewer.getUniqueId(), kit, draft);
        render(draft, it);
    }

    private void amount(ItemStack[] draft, int delta) {
        ItemStack it = draft[slot].clone();
        it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), it.getAmount() + delta)));
        draft[slot] = it;
        CustomKits.keepDraft(viewer.getUniqueId(), kit, draft);
        render(draft, it);
    }

    private void back() {
        new CustomKitEditorGui(plugin, viewer, kit, back).open(viewer);
    }
}
