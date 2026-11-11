package dev.mintpvp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small fluent item builder for GUI icons, plus item helpers. */
public final class Items {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<Component> lore = new ArrayList<>();

    private Items(Material m) {
        item = new ItemStack(m);
        meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.values());
    }

    public static Items of(Material m) {
        return new Items(m);
    }

    public static boolean isEmpty(ItemStack i) {
        return i == null || i.getType().isAir();
    }

    public Items name(Component c) {
        meta.displayName(noItalic(c));
        return this;
    }

    public Items lore(Component... lines) {
        for (Component c : lines) lore.add(noItalic(c));
        return this;
    }

    public Items lore(List<Component> lines) {
        lines.forEach(c -> lore.add(noItalic(c)));
        return this;
    }

    /** Pane/filler with no tooltip at all. */
    public Items blank() {
        meta.setHideTooltip(true);
        return this;
    }

    public Items glow() {
        meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public Items amount(int n) {
        item.setAmount(Math.max(1, Math.min(64, n)));
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Total amount per item type (ignoring stack layout), for layout validation. */
    public static Map<ItemStack, Integer> tally(ItemStack[] items) {
        Map<ItemStack, Integer> out = new HashMap<>();
        for (ItemStack i : items) {
            if (isEmpty(i)) continue;
            out.merge(i.asOne(), i.getAmount(), Integer::sum);
        }
        return out;
    }
}
