package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** every item, searchable */
public final class ItemBrowserGui extends Gui {

    private static final String[] CATEGORIES = {"weapons & gear", "potions", "food", "blocks", "miscellaneous", "all items"};
    private static final Material[] ICONS = {Material.DIAMOND_SWORD, Material.SPLASH_POTION, Material.GOLDEN_APPLE, Material.GRASS_BLOCK, Material.ENDER_CHEST, Material.COMPASS};
    private static final int[] SECTION_SLOTS = {10, 12, 14, 16, 22, 31};
    private static final int PER_PAGE = 45;
    private static List<ItemStack> catalog;

    private final Kit kit;
    private final Player viewer;
    private final String filter;
    private final int category;
    private final int page;
    private final Runnable back;

    public ItemBrowserGui(MintPvP plugin, Player p, Kit kit, String filter, int category, int page, Runnable back) {
        super(plugin, 6, plugin.theme().title("add items"));
        this.kit = kit;
        this.viewer = p;
        this.filter = filter;
        this.category = category;
        this.back = back;
        Theme t = plugin.theme();
        if (category < 0) {
            this.page = 0;
            sections(t);
            return;
        }

        List<ItemStack> list = new ArrayList<>();
        String f = filter.toLowerCase(Locale.ROOT).replace(' ', '_');
        for (ItemStack it : catalog()) {
            if (!matchesCategory(it)) continue;
            if (!f.isEmpty() && !name(it).contains(f)) continue;
            list.add(it);
        }
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            ItemStack base = list.get(start + i);
            ItemStack show = base.clone();
            var meta = show.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            lore.add(Component.empty());
            lore.add(t.text("left-click: 1", t.secondary()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(t.text("right-click: 16", t.secondary()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(t.text("shift-click: full stack", t.secondary()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(lore);
            show.setItemMeta(meta);
            inv.setItem(i, show);
            actionFor(i, base);
        }
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).blank().build();
        for (int i = 45; i < 54; i++) set(i, dark);
        set(45, Items.of(Material.SPECTRAL_ARROW).name(t.text("« done", t.secondary()))
                .lore(t.text("back to the kit", t.muted())).build(), e -> new CustomKitEditorGui(plugin, p, kit, back).open(p));
        if (this.page > 0) {
            int prev = this.page - 1;
            set(47, Items.of(Material.ARROW).name(t.text("‹ previous", t.primary())).build(), e -> new ItemBrowserGui(plugin, p, kit, filter, category, prev, back).open(p));
        }
        set(48, Items.of(Material.PAPER).name(t.text("page " + (this.page + 1) + " / " + pages, t.accent()))
                .lore(t.text(list.size() + " items", t.muted())).build());
        if (this.page < pages - 1) {
            int next = this.page + 1;
            set(49, Items.of(Material.ARROW).name(t.text("next ›", t.primary())).build(), e -> new ItemBrowserGui(plugin, p, kit, filter, category, next, back).open(p));
        }
        set(51, Items.of(ICONS[category]).name(t.text("✦ " + CATEGORIES[category], t.primary()))
                .lore(t.text("click for all sections", t.secondary())).build(),
                e -> new ItemBrowserGui(plugin, p, kit, filter, -1, 0, back).open(p));
        set(52, Items.of(Material.OAK_SIGN).name(t.text("✦ search" + (filter.isEmpty() ? "" : ": " + filter), t.primary()))
                .lore(t.text("click, then type in chat", t.secondary()), t.text("right-click to clear", t.muted())).build(), e -> {
            if (e.isRightClick()) {
                new ItemBrowserGui(plugin, p, kit, "", category, 0, back).open(p);
                return;
            }
            plugin.chatPrompt().ask(p, "type part of an item name.", text ->
                    new ItemBrowserGui(plugin, p, kit, text == null ? filter : text, category, 0, back).open(p));
        });
    }

    private void actionFor(int slot, ItemStack base) {
        // slot actions use the raw
        set(slot, inv.getItem(slot), e -> {
            int amount = e.isShiftClick() ? base.getMaxStackSize() : e.isRightClick() ? Math.min(16, base.getMaxStackSize()) : 1;
            ItemStack give = base.clone();
            give.setAmount(Math.max(1, amount));
            addToDraft(give);
        });
    }

    private void addToDraft(ItemStack item) {
        Theme t = plugin.theme();
        ItemStack[] draft = CustomKits.draft(viewer.getUniqueId(), kit);
        int slot = freeSlot(draft, item);
        if (slot < 0) {
            viewer.sendMessage(t.error("the kit is full - remove something first."));
            return;
        }
        draft[slot] = item;
        CustomKits.keepDraft(viewer.getUniqueId(), kit, draft);
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.6f);
        viewer.sendActionBar(t.text("added " + readable(item.getType()) + " - " + count(draft) + " items", t.accent()));
    }

    private static int count(ItemStack[] d) {
        int n = 0;
        for (ItemStack i : d) if (!Items.isEmpty(i)) n++;
        return n;
    }

    private static int freeSlot(ItemStack[] d, ItemStack item) {
        EquipmentSlot es = item.getType().getEquipmentSlot();
        int armor = switch (es) {
            case HEAD -> 39;
            case CHEST -> 38;
            case LEGS -> 37;
            case FEET -> 36;
            default -> -1;
        };
        if (armor >= 0 && Items.isEmpty(d[armor])) return armor;
        for (int i = 0; i < KitManager.MAIN; i++) if (Items.isEmpty(d[i])) return i;
        return -1;
    }

    private static String readable(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String name(ItemStack it) {
        String base = it.getType().name().toLowerCase(Locale.ROOT);
        if (it.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() != null) base += "_" + pm.getBasePotionType().getKey().getKey();
        return base;
    }

    private void sections(Theme t) {
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).blank().build();
        for (int i = 0; i < 54; i++) set(i, dark);
        int[] counts = new int[CATEGORIES.length];
        for (ItemStack it : catalog()) {
            counts[sectionOf(it)]++;
            counts[5]++;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            int cat = i;
            set(SECTION_SLOTS[i], Items.of(ICONS[i]).name(t.text(CATEGORIES[i], t.primary()))
                    .lore(t.text(counts[i] + " items", t.accent()), Component.empty(), t.text("click to browse", t.secondary())).glow().build(),
                    e -> new ItemBrowserGui(plugin, viewer, kit, filter, cat, 0, back).open(viewer));
        }
        set(4, Items.of(Material.PAPER).name(t.text("add items", t.primary()))
                .lore(t.text("pick a section, then click items", t.accent()), t.text("to add them to your kit", t.accent())).build());
        set(45, Items.of(Material.SPECTRAL_ARROW).name(t.text("« done", t.secondary()))
                .lore(t.text("back to the kit", t.muted())).build(), e -> new CustomKitEditorGui(plugin, viewer, kit, back).open(viewer));
    }

    private boolean matchesCategory(ItemStack it) {
        return category == 5 || sectionOf(it) == category;
    }

    /** 0 gear, 1 potions, 2 food, 3 blocks, 4 miscellaneous */
    private static int sectionOf(ItemStack it) {
        Material m = it.getType();
        String n = m.name();
        if (it.getItemMeta() instanceof PotionMeta) return 1;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.endsWith("_SPEAR")
                || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE") || n.equals("SHIELD") || n.equals("ELYTRA")
                || n.equals("TOTEM_OF_UNDYING") || n.equals("ARROW") || n.equals("SPECTRAL_ARROW") || n.equals("ENDER_PEARL")
                || n.equals("WIND_CHARGE") || n.equals("FIRE_CHARGE") || n.equals("FLINT_AND_STEEL") || n.equals("TNT") || n.equals("FISHING_ROD")
                || n.equals("SNOWBALL") || n.equals("EGG") || n.equals("COBWEB") || n.equals("LAVA_BUCKET") || n.equals("WATER_BUCKET")
                || n.equals("FIREWORK_ROCKET") || n.equals("END_CRYSTAL") || n.equals("RESPAWN_ANCHOR") || n.equals("GLOWSTONE")
                || n.equals("SHEARS") || n.equals("TURTLE_HELMET") || n.equals("CARVED_PUMPKIN")) return 0;
        if (m.isEdible()) return 2;
        if (m.isBlock()) return 3;
        return 4;
    }

    /** all items except spawn eggs */
    private static synchronized List<ItemStack> catalog() {
        if (catalog != null) return catalog;
        List<ItemStack> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir()) continue;
            String n = m.name();
            if (n.endsWith("_SPAWN_EGG")) continue;
            if (m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION || m == Material.TIPPED_ARROW) continue;
            out.add(new ItemStack(m));
        }
        for (Material m : new Material[] {Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW}) {
            for (PotionType type : Registry.POTION.stream().toList()) {
                ItemStack it = new ItemStack(m);
                PotionMeta pm = (PotionMeta) it.getItemMeta();
                pm.setBasePotionType(type);
                it.setItemMeta(pm);
                out.add(it);
            }
        }
        out.sort(java.util.Comparator.comparing(i -> name(i)));
        catalog = out;
        return out;
    }
}
