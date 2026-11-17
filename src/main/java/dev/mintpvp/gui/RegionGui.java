package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** region picker */
public final class RegionGui extends Gui {

    public RegionGui(MintPvP plugin, Player viewer, Runnable back) {
        super(plugin, 3, plugin.theme().title("region"));
        Theme t = plugin.theme();
        frame(true);
        String here = plugin.lobby().currentRegion();
        entry(plugin, viewer, t, 11, "na", "north america", Material.BLUE_CONCRETE, here);
        entry(plugin, viewer, t, 15, "eu", "europe", Material.YELLOW_CONCRETE, here);
        if (back != null) {
            set(18, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(18, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> viewer.closeInventory());
        }
    }

    private void entry(MintPvP plugin, Player viewer, Theme t, int slot, String id, String name, Material icon, String here) {
        boolean current = id.equals(here);
        boolean ready = plugin.lobby().regionAddress(id) != null;
        Items it = Items.of(icon).name(t.text(name, t.primary()))
                .lore(t.text(current ? "you are here" : ready ? "click to switch" : "not set up yet", current ? t.accent() : ready ? t.secondary() : t.muted()),
                        Component.empty(),
                        t.text("better ping if you live near it", t.muted()));
        if (current) it.glow();
        set(slot, it.build(), current || !ready ? null : e -> {
            viewer.closeInventory();
            plugin.lobby().transfer(viewer, id);
        });
    }
}
