package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Pick how many rounds a duel is played to ("first to N"). */
public final class RoundsGui extends Gui {

    public RoundsGui(MintPvP plugin, Player viewer, Runnable back, Consumer<Integer> onPick) {
        super(plugin, 3, plugin.theme().title("first to"));
        Theme t = plugin.theme();
        frame(true);

        List<Integer> options = new ArrayList<>(plugin.getConfig().getIntegerList("duel.round-options"));
        if (options.isEmpty()) options = List.of(1, 2, 3, 5, 7, 10);
        int max = plugin.getConfig().getInt("duel.max-rounds", 15);
        options.removeIf(n -> n < 1 || n > max);
        int count = Math.min(options.size(), 7);
        int start = 10 + (7 - count) / 2;
        for (int i = 0; i < count; i++) {
            int n = options.get(i);
            Items it = Items.of(n == 1 ? Material.CLOCK : Material.PAPER)
                    .name(t.text(n == 1 ? "single round" : "first to " + n, t.primary()))
                    .amount(Math.min(64, n))
                    .lore(t.text(n == 1 ? "one fight, winner takes it" : "first to win " + n + " rounds", t.accent()),
                            net.kyori.adventure.text.Component.empty(),
                            t.text("click to send the challenge", t.secondary()));
            if (n == 1) it.glow();
            set(start + i, it.build(), e -> {
                viewer.closeInventory();
                onPick.accept(n);
            });
        }

        if (back != null) {
            set(18, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(18, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> viewer.closeInventory());
        }
    }
}
