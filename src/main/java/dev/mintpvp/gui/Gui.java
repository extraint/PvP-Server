package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** menu base */
public class Gui implements InventoryHolder {

    private static final ItemStack FILLER = Items.of(Material.BLACK_STAINED_GLASS_PANE).blank().build();
    private static final ItemStack BASE = Items.of(Material.LIME_STAINED_GLASS_PANE).blank().build();
    private static final ItemStack MID = Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE).blank().build();
    private static final ItemStack HEAD = Items.of(Material.WHITE_STAINED_GLASS_PANE).blank().build();

    protected final MintPvP plugin;
    protected final Inventory inv;
    private final int rows;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private final List<Integer> ring = new ArrayList<>();
    private boolean animated;
    private int tick;

    public Gui(MintPvP plugin, int rows, Component title) {
        this.plugin = plugin;
        this.rows = rows;
        this.inv = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    /** menu frame */
    public void frame(boolean animated) {
        this.animated = animated;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, FILLER);
        ring.clear();
        for (int c = 0; c < 9; c++) ring.add(c);
        for (int r = 1; r < rows - 1; r++) ring.add(r * 9 + 8);
        for (int c = 8; c >= 0; c--) ring.add((rows - 1) * 9 + c);
        for (int r = rows - 2; r >= 1; r--) ring.add(r * 9);
        for (int s : ring) inv.setItem(s, BASE);
    }

    public void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        ring.remove(Integer.valueOf(slot));
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    public void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** clear slot */
    protected void clearSlot(int slot) {
        inv.setItem(slot, FILLER);
        actions.remove(slot);
    }

    /** border pane */
    protected void setBorderPane(int slot) {
        set(slot, BASE);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** click handler */
    public void handleClick(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a == null) return;
        if (e.getWhoClicked() instanceof Player p) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.5f);
        }
        a.accept(e);
    }

    public void onClose(Player p) {}

    void animate() {
        if (!animated || ring.size() < 4) return;
        tick++;
        int n = ring.size();
        inv.setItem(ring.get(Math.floorMod(tick, n)), HEAD);
        inv.setItem(ring.get(Math.floorMod(tick - 1, n)), MID);
        inv.setItem(ring.get(Math.floorMod(tick - 2, n)), BASE);
    }
}
