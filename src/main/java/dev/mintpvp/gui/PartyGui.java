package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.PartyManager.Party;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The party menu: members, invite, challenge another party, leave / disband. */
public final class PartyGui extends Gui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public PartyGui(MintPvP plugin, Player p) {
        super(plugin, 5, plugin.theme().title("party"));
        Theme t = plugin.theme();
        frame(true);
        Party party = plugin.parties().partyOf(p.getUniqueId());

        if (party == null) {
            set(22, Items.of(Material.FIREWORK_ROCKET).name(t.text("✦ create a party", t.primary()))
                    .lore(t.text("team up and duel other parties", t.accent()),
                            t.text("up to " + plugin.parties().maxSize() + " players", t.muted()),
                            Component.empty(), t.text("click to create", t.secondary())).glow().build(), e -> {
                plugin.parties().create(p);
                new PartyGui(plugin, p).open(p);
            });
            set(40, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> p.closeInventory());
            return;
        }

        boolean leader = party.leader.equals(p.getUniqueId());
        set(4, Items.of(Material.NETHER_STAR).name(t.text("your party", t.primary()))
                .lore(t.text(party.members.size() + " / " + plugin.parties().maxSize() + " members", t.accent())).glow().build());

        int i = 0;
        for (UUID id : party.members) {
            if (i >= SLOTS.length) break;
            Player m = Bukkit.getPlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            String name = m != null ? m.getName() : plugin.stats().nameOf(id);
            meta.displayName(t.raw((id.equals(party.leader) ? "★ " : "") + name, t.primary()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (id.equals(party.leader)) lore.add(t.text("party leader", t.secondary()).decoration(TextDecoration.ITALIC, false));
            if (m == null) lore.add(t.text("offline", t.muted()).decoration(TextDecoration.ITALIC, false));
            if (leader && !id.equals(p.getUniqueId())) lore.add(t.text("right-click to kick", t.muted()).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            head.setItemMeta(meta);
            final String kickName = name;
            set(SLOTS[i++], head, e -> {
                if (leader && !id.equals(p.getUniqueId()) && e.getClick() == ClickType.RIGHT) {
                    plugin.parties().kick(p, kickName);
                    new PartyGui(plugin, p).open(p);
                }
            });
        }

        if (leader) {
            set(28, Items.of(Material.PAPER).name(t.text("✦ invite a player", t.primary()))
                    .lore(t.text("pick someone online", t.accent())).build(),
                    e -> Guis.pickPlayer(plugin, p, "invite", () -> new PartyGui(plugin, p).open(p), target -> {
                        plugin.parties().invite(p, target);
                        new PartyGui(plugin, p).open(p);
                    }));
            set(30, Items.of(Material.CROSSBOW).name(t.text("✦ challenge a party", t.primary()))
                    .lore(t.text("duel another party", t.accent()), Component.empty(), t.text("click to choose", t.secondary())).glow().build(),
                    e -> challengeParty(plugin, p, () -> new PartyGui(plugin, p).open(p)));
        }
        if (leader && party.members.size() >= 2) {
            set(38, Items.of(Material.WHITE_BANNER).name(t.text("✦ custom teams", t.primary()))
                    .lore(t.text("split your party into two teams", t.accent()),
                            t.text("of any size (2v1, 3v2...) and play", t.accent()),
                            Component.empty(), t.text("click to set up", t.secondary())).glow().build(),
                    e -> new TeamsGui(plugin, p, null, () -> new PartyGui(plugin, p).open(p)).open(p));
        }
        if (leader && party.members.size() >= Math.max(3, plugin.getConfig().getInt("party.ffa-min-players", 3))) {
            set(42, Items.of(Material.TNT).name(t.text("✦ party free-for-all", t.primary()))
                    .lore(t.text("everyone in the party fights", t.accent()),
                            t.text("each other - last one standing wins", t.accent()),
                            Component.empty(), t.text("click to pick a kit", t.secondary())).glow().build(),
                    e -> ffaKit(plugin, p, () -> new PartyGui(plugin, p).open(p)));
        }
        set(32, Items.of(Material.OAK_DOOR).name(t.text("leave party", t.secondary())).build(), e -> {
            plugin.parties().leave(p, false);
            new PartyGui(plugin, p).open(p);
        });
        if (leader) {
            set(34, Items.of(Material.TNT).name(t.text("disband party", t.secondary()))
                    .lore(t.text("removes everyone", t.muted())).build(), e -> {
                plugin.parties().disband(p);
                new PartyGui(plugin, p).open(p);
            });
        }
        set(40, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> p.closeInventory());
    }

    /** Pick a kit, then start a free-for-all among the party. */
    public static void ffaKit(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        Guis.pickKit(plugin, p, "party free-for-all", back,
                kit -> List.of(t.text("click to start", t.secondary())),
                kit -> {
                    p.closeInventory();
                    plugin.duels().startPartyFfa(p, kit.id);
                });
    }

    /** Pick another party (shown by its leader), then a kit, then send the challenge. */
    public static void challengeParty(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        Party mine = plugin.parties().partyOf(p.getUniqueId());
        if (mine == null || !mine.leader.equals(p.getUniqueId())) {
            p.sendMessage(t.error("only a party leader can challenge another party."));
            return;
        }
        List<PagedGui.Entry> entries = new ArrayList<>();
        for (Party other : plugin.parties().all()) {
            if (other == mine) continue;
            Player lead = Bukkit.getPlayer(other.leader);
            if (lead == null) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(lead);
            meta.displayName(t.raw(lead.getName() + "'s party", t.primary()).decoration(TextDecoration.ITALIC, false));
            boolean sizeOk = plugin.getConfig().getBoolean("party.allow-uneven-teams", true) || other.members.size() == mine.members.size();
            meta.lore(List.of(
                    t.text(other.members.size() + " members", t.accent()).decoration(TextDecoration.ITALIC, false),
                    t.text(sizeOk ? "click to challenge" : "needs " + mine.members.size() + " members", sizeOk ? t.secondary() : t.muted()).decoration(TextDecoration.ITALIC, false)));
            head.setItemMeta(meta);
            entries.add(new PagedGui.Entry(head, e -> Guis.pickKit(plugin, p, "choose kit", () -> challengeParty(plugin, p, back),
                    kit -> List.of(t.text("click to send the challenge", t.secondary())),
                    kit -> {
                        p.closeInventory();
                        plugin.duels().requestParty(p, lead, kit.id);
                    })));
        }
        new PagedGui(plugin, t.title("challenge a party"), entries, back, "no other parties yet").open(p);
    }
}
