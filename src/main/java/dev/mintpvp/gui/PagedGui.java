package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** A bordered 5-row list of up to 21 entries per page with prev/next/back controls. */
public class PagedGui extends Gui {

    public record Entry(ItemStack item, Consumer<InventoryClickEvent> action) {}

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final List<Entry> entries;
    private final Runnable back;
    private final String emptyText;
    private int page;

    public PagedGui(MintPvP plugin, Component title, List<Entry> entries, Runnable back, String emptyText) {
        super(plugin, 5, title);
        this.entries = entries;
        this.back = back;
        this.emptyText = emptyText;
        frame(true);
        render();
    }

    private int pages() {
        return Math.max(1, (entries.size() + SLOTS.length - 1) / SLOTS.length);
    }

    private void render() {
        Theme t = plugin.theme();
        for (int s : SLOTS) clearSlot(s);
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < entries.size(); i++) {
            Entry e = entries.get(start + i);
            set(SLOTS[i], e.item(), e.action());
        }
        if (entries.isEmpty()) {
            set(22, Items.of(Material.BARRIER).name(t.text(emptyText, t.muted())).build());
        }

        set(4, Items.of(Material.PAPER)
                .name(t.text("page " + (page + 1) + " / " + pages(), t.accent())).build());
        if (page > 0) {
            set(38, Items.of(Material.ARROW).name(t.text("‹ previous", t.primary())).build(), e -> { page--; render(); });
        } else {
            setBorderPane(38);
        }
        if (page < pages() - 1) {
            set(42, Items.of(Material.ARROW).name(t.text("next ›", t.primary())).build(), e -> { page++; render(); });
        } else {
            setBorderPane(42);
        }
        if (back != null) {
            set(40, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(40, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(),
                    e -> e.getWhoClicked().closeInventory());
        }
    }
}
