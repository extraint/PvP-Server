package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
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

/** party menu */
public final class PartyGui extends Gui {

    private static final int[] SLOTS = java.util.stream.IntStream.rangeClosed(9, 44).toArray();

    public PartyGui(MintPvP plugin, Player p) {
        super(plugin, 6, plugin.theme().title("party"));
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
            set(49, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> p.closeInventory());
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
            set(46, Items.of(Material.PAPER).name(t.text("✦ invite a player", t.primary()))
                    .lore(t.text("pick someone online", t.accent())).build(),
                    e -> Guis.pickPlayer(plugin, p, "invite", () -> new PartyGui(plugin, p).open(p), target -> {
                        plugin.parties().invite(p, target);
                        new PartyGui(plugin, p).open(p);
                    }));
            set(47, Items.of(Material.CROSSBOW).name(t.text("✦ challenge a party", t.primary()))
                    .lore(t.text("duel another party", t.accent()), Component.empty(), t.text("click to choose", t.secondary())).glow().build(),
                    e -> challengeParty(plugin, p, () -> new PartyGui(plugin, p).open(p)));
        }
        if (leader && party.members.size() >= 2) {
            set(48, Items.of(Material.WHITE_BANNER).name(t.text("✦ custom teams", t.primary()))
                    .lore(t.text("split your party into two teams", t.accent()),
                            t.text("of any size (2v1, 3v2...) and play", t.accent()),
                            Component.empty(), t.text("click to set up", t.secondary())).glow().build(),
                    e -> new TeamsGui(plugin, p, null, () -> new PartyGui(plugin, p).open(p)).open(p));
        }
        if (leader && party.members.size() >= Math.max(3, plugin.getConfig().getInt("party.ffa-min-players", 3))) {
            set(50, Items.of(Material.TNT).name(t.text("✦ party free-for-all", t.primary()))
                    .lore(t.text("everyone in the party fights", t.accent()),
                            t.text("each other - last one standing wins", t.accent()),
                            Component.empty(), t.text("click to pick a kit", t.secondary())).glow().build(),
                    e -> ffaKit(plugin, p, () -> new PartyGui(plugin, p).open(p)));
        }
        set(51, Items.of(Material.OAK_DOOR).name(t.text("leave party", t.secondary())).build(), e -> {
            plugin.parties().leave(p, false);
            new PartyGui(plugin, p).open(p);
        });
        if (leader) {
            set(52, Items.of(Material.TNT).name(t.text("disband party", t.secondary()))
                    .lore(t.text("removes everyone", t.muted())).build(), e -> {
                plugin.parties().disband(p);
                new PartyGui(plugin, p).open(p);
            });
        }
        set(49, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> p.closeInventory());
    }

    /** party free-for-all */
    public static void ffaKit(MintPvP plugin, Player p, Runnable back) {
        Theme t = plugin.theme();
        Party mine = plugin.parties().partyOf(p.getUniqueId());
        List<Kit> kits = mine == null ? List.copyOf(plugin.kits().all()) : CustomKits.partyKits(plugin, mine.members);
        Guis.pickKit(plugin, p, "party free-for-all", back,
                kit -> List.of(t.text("click to start", t.secondary())),
                kit -> {
                    p.closeInventory();
                    plugin.duels().startPartyFfa(p, kit.id);
                }, kits, List.of());
    }

    /** party challenge */
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
            java.util.Set<UUID> both = new java.util.HashSet<>(mine.members);
            both.addAll(other.members);
            List<Kit> kits = CustomKits.partyKits(plugin, both);
            Runnable again = () -> challengeParty(plugin, p, back);
            entries.add(new PagedGui.Entry(head, e -> Guis.pickKit(plugin, p, "your team's kit", again,
                    kit -> List.of(t.text("click to pick", t.secondary())),
                    mineKit -> {
                        PagedGui.Entry same = new PagedGui.Entry(Items.of(Material.LIME_DYE).name(t.text("✦ same kit for both teams", t.primary()))
                                .lore(t.text(mineKit.display, t.accent()), Component.empty(), t.text("click to send the challenge", t.secondary())).glow().build(), ev -> {
                            p.closeInventory();
                            plugin.duels().requestParty(p, lead, mineKit.id);
                        });
                        Guis.pickKit(plugin, p, "their team's kit", again,
                                kit -> List.of(t.text("click to send the challenge", t.secondary())),
                                theirKit -> {
                                    p.closeInventory();
                                    plugin.duels().requestParty(p, lead, mineKit.id, theirKit.id);
                                }, kits, List.of(same));
                    }, kits, List.of())));
        }
        new PagedGui(plugin, t.title("challenge a party"), entries, back, "no other parties yet").open(p);
    }
}
