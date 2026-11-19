package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.arenagen.ArenaTheme;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** ban one map */
public final class VetoGui extends Gui {

    public VetoGui(MintPvP plugin, Player viewer, List<ArenaTheme> options) {
        super(plugin, 3, plugin.theme().title("ban a map"));
        Theme t = plugin.theme();
        frame(true);
        int n = options.size();
        int start = 10 + (7 - Math.min(7, n)) / 2;
        for (int i = 0; i < Math.min(7, n); i++) {
            ArenaTheme th = options.get(i);
            set(start + i, Items.of(th.icon).name(t.text(th.display, t.primary()))
                    .lore(t.text("click to ban this map", t.secondary()), Component.empty(),
                            t.text("the match is played on a", t.muted()), t.text("random map that isn't banned", t.muted()))
                    .build(), e -> {
                viewer.closeInventory();
                plugin.duels().vetoBan(viewer, th.id);
            });
        }
        set(22, Items.of(Material.BARRIER).name(t.text("don't ban anything", t.secondary())).build(), e -> {
            viewer.closeInventory();
            plugin.duels().vetoBan(viewer, "");
        });
    }
}
