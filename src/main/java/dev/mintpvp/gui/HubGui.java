package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.QueueManager;
import dev.mintpvp.managers.StatsManager;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** The main /menu: queue, challenge, layout editor, spectate, stats. */
public final class HubGui extends Gui {

    public HubGui(MintPvP plugin, Player p) {
        super(plugin, 5, plugin.theme().title("duels"));
        Theme t = plugin.theme();
        frame(true);

        QueueManager q = plugin.queue();
        var mine = q.entryOf(p.getUniqueId());

        set(20, Items.of(Material.DIAMOND_SWORD)
                .name(t.text("✦ queue - unranked", t.primary()))
                .lore(t.text("jump into a random match", t.accent()),
                        t.text(q.total() + " waiting in queues", t.muted()),
                        Component.empty(),
                        t.text("click to pick a kit", t.secondary()))
                .glow().build(), e -> Guis.queueKits(plugin, p, () -> new HubGui(plugin, p).open(p)));

        set(22, Items.of(Material.NETHERITE_SWORD)
                .name(t.text("✦ ranked", t.muted()))
                .lore(t.text("coming soon", t.secondary()),
                        t.text("skill-based matchmaking with", t.muted()),
                        t.text("ratings and seasons.", t.muted()))
                .build(), e -> p.sendMessage(t.info("ranked is coming soon!")));

        set(24, Items.of(Material.BOW)
                .name(t.text("✦ challenge a player", t.primary()))
                .lore(t.text("pick a player and a kit", t.accent()),
                        Component.empty(),
                        t.text("click to choose", t.secondary()))
                .build(), e -> Guis.challenge(plugin, p, () -> new HubGui(plugin, p).open(p)));

        set(29, Items.of(Material.ENDER_CHEST)
                .name(t.text("✦ kit layout", t.primary()))
                .lore(t.text("arrange your kit slots how", t.accent()),
                        t.text("you like them", t.accent()),
                        Component.empty(),
                        t.text("click to edit", t.secondary()))
                .build(), e -> Guis.layoutKits(plugin, p, () -> new HubGui(plugin, p).open(p)));

        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());
        set(31, Items.of(Material.BOOK)
                .name(t.text("✦ your stats", t.primary()))
                .lore(t.line("duels", s.duelWins + "w / " + s.duelLosses + "l"),
                        t.line("kills", String.valueOf(s.kills)),
                        t.line("deaths", String.valueOf(s.deaths)),
                        t.line("k/d", String.format(Locale.ROOT, "%.2f", s.kd())),
                        t.line("best streak", String.valueOf(s.bestStreak)))
                .build());

        set(33, Items.of(Material.ENDER_EYE)
                .name(t.text("✦ spectate", t.primary()))
                .lore(t.text(plugin.duels().activeDuels().size() + " duels running", t.accent()),
                        Component.empty(),
                        t.text("click to watch", t.secondary()))
                .build(), e -> Guis.spectateList(plugin, p, () -> new HubGui(plugin, p).open(p)));

        if (mine != null) {
            set(13, Items.of(Material.REDSTONE)
                    .name(t.text("leave queue", t.secondary()))
                    .lore(t.text("queued for " + mine.kit().display, t.muted()))
                    .build(), e -> {
                plugin.queue().leave(p.getUniqueId());
                p.sendMessage(t.info("you left the queue."));
                new HubGui(plugin, p).open(p);
            });
        }

        if (p.hasPermission("mintpvp.admin")) {
            set(44, Items.of(Material.ANVIL)
                    .name(t.text("kit admin", t.secondary()))
                    .lore(t.text("create, edit and delete kits", t.muted()))
                    .build(), e -> new KitAdminGui(plugin, p, () -> new HubGui(plugin, p).open(p)).open(p));
        }
    }
}
