package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.QueueManager;
import dev.mintpvp.managers.StatsManager;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import dev.mintpvp.util.Tier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** main menu */
public final class HubGui extends Gui {

    public HubGui(MintPvP plugin, Player p) {
        super(plugin, 5, plugin.theme().title("duels"));
        Theme t = plugin.theme();
        frame(true);

        QueueManager q = plugin.queue();
        var mine = q.entryOf(p.getUniqueId());

        set(19, Items.of(Material.IRON_SWORD)
                .name(t.text("✦ queue - unranked", t.primary()))
                .lore(t.text("jump into a random match", t.accent()),
                        t.text(q.total() + " waiting in queues", t.muted()),
                        Component.empty(),
                        t.text("click to pick a kit", t.secondary()))
                .build(), e -> Guis.queueKits(plugin, p, QueueManager.Type.UNRANKED, () -> new HubGui(plugin, p).open(p)));

        StatsManager.Stats mineStats = plugin.stats().get(p.getUniqueId());
        set(21, Items.of(Material.DIAMOND_SWORD)
                .name(t.text("✦ queue - ranked", t.primary()))
                .lore(t.text("kit tiers: lt5 → ht1", t.accent()),
                        t.line("your tier", Tier.overallLabel(mineStats.score())),
                        t.line("ranked", mineStats.rankedWins + "w / " + mineStats.rankedLosses + "l"),
                        Component.empty(),
                        t.text(plugin.queue().rankedEnabled() ? "click to pick a kit" : "currently disabled", t.secondary()))
                .glow().build(), e -> Guis.queueKits(plugin, p, QueueManager.Type.RANKED, () -> new HubGui(plugin, p).open(p)));

        set(23, Items.of(Material.BOW)
                .name(t.text("✦ challenge a player", t.primary()))
                .lore(t.text("pick a player and a kit", t.accent()),
                        Component.empty(),
                        t.text("click to choose", t.secondary()))
                .build(), e -> Guis.challenge(plugin, p, () -> new HubGui(plugin, p).open(p)));

        set(25, Items.of(Material.CROSSBOW)
                .name(t.text("✦ ranked challenge", t.primary()))
                .lore(t.text("duel a specific player for tiers", t.accent()),
                        Component.empty(),
                        t.text("click to choose", t.secondary()))
                .glow().build(), e -> Guis.challenge(plugin, p, () -> new HubGui(plugin, p).open(p), true));

        set(28, Items.of(Material.ENDER_CHEST)
                .name(t.text("✦ kit layout", t.primary()))
                .lore(t.text("arrange your kit slots how", t.accent()),
                        t.text("you like them", t.accent()),
                        Component.empty(),
                        t.text("click to edit", t.secondary()))
                .build(), e -> Guis.layoutKits(plugin, p, () -> new HubGui(plugin, p).open(p)));

        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());
        set(30, Items.of(Material.GOLD_INGOT)
                .name(t.text("✦ leaderboard", t.primary()))
                .lore(t.text("top players by tier, wins,", t.accent()),
                        t.text("kills and streak", t.accent()),
                        Component.empty(),
                        t.text("click to view", t.secondary()))
                .build(), e -> new LeaderboardGui(plugin, p, "tier", () -> new HubGui(plugin, p).open(p)).open(p));

        set(32, Items.of(Material.BOOK)
                .name(t.text("✦ your stats", t.primary()))
                .lore(t.line("tier", Tier.overallLabel(s.score())),
                        t.line("duels", s.duelWins + "w / " + s.duelLosses + "l"),
                        t.line("kills", String.valueOf(s.kills)),
                        t.line("deaths", String.valueOf(s.deaths)),
                        t.line("k/d", String.format(Locale.ROOT, "%.2f", s.kd())),
                        t.line("best streak", String.valueOf(s.bestStreak)))
                .build());

        set(34, Items.of(Material.ENDER_EYE)
                .name(t.text("✦ spectate", t.primary()))
                .lore(t.text(plugin.duels().activeDuels().size() + " duels running", t.accent()),
                        Component.empty(),
                        t.text("click to watch", t.secondary()))
                .build(), e -> Guis.spectateList(plugin, p, () -> new HubGui(plugin, p).open(p)));

        var party = plugin.parties().partyOf(p.getUniqueId());
        set(11, Items.of(Material.FIREWORK_ROCKET)
                .name(t.text("✦ party", t.primary()))
                .lore(t.text(party == null ? "team up and duel other parties" : "your party: " + party.members.size() + " members", t.accent()),
                        Component.empty(),
                        t.text("click to open", t.secondary()))
                .build(), e -> new PartyGui(plugin, p).open(p));
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
