package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.managers.PartyManager.Party;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** team split */
public final class TeamsGui extends Gui {

    private static final int[] SLOTS = java.util.stream.IntStream.rangeClosed(9, 44).toArray();

    /** true = blue */
    private final Map<UUID, Boolean> blue = new LinkedHashMap<>();

    private static void begin(MintPvP plugin, Player leader, Map<UUID, Boolean> blue, Kit redKit, Kit blueKit) {
        Theme t = plugin.theme();
        leader.closeInventory();
        List<Player> red = new ArrayList<>(), blu = new ArrayList<>();
        for (var en : blue.entrySet()) {
            Player pl = Bukkit.getPlayer(en.getKey());
            if (pl == null) {
                leader.sendMessage(t.error("a party member is offline."));
                return;
            }
            (en.getValue() ? blu : red).add(pl);
        }
        plugin.duels().startScrim(leader, red, blu, redKit.id, blueKit == null ? null : blueKit.id);
    }

    public TeamsGui(MintPvP plugin, Player leader, Map<UUID, Boolean> existing, Runnable back) {
        super(plugin, 6, plugin.theme().title("custom teams"));
        Theme t = plugin.theme();
        frame(true);
        Party party = plugin.parties().partyOf(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            set(22, Items.of(Material.BARRIER).name(t.text("only the party leader can do this", t.muted())).build());
            return;
        }

        int n = 0;
        for (UUID id : party.members) {
            boolean b = existing != null && existing.containsKey(id) ? existing.get(id) : n % 2 == 1;
            blue.put(id, b);
            n++;
        }
        long reds = blue.values().stream().filter(b -> !b).count(), blues = blue.size() - reds;

        set(4, Items.of(Material.PAPER).name(t.text("red " + reds + "  ·  blue " + blues, t.primary()))
                .lore(t.text("click a player to switch their team", t.accent())).build());

        int i = 0;
        for (var entry : blue.entrySet()) {
            if (i >= SLOTS.length) break;
            UUID id = entry.getKey();
            boolean isBlue = entry.getValue();
            Player m = Bukkit.getPlayer(id);
            String name = m != null ? m.getName() : plugin.stats().nameOf(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            meta.displayName(Component.text(name, isBlue ? NamedTextColor.AQUA : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    t.text(isBlue ? "blue team" : "red team", isBlue ? NamedTextColor.AQUA : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    t.text("click to switch", t.muted()).decoration(TextDecoration.ITALIC, false)));
            meta.setEnchantmentGlintOverride(isBlue);
            head.setItemMeta(meta);
            set(SLOTS[i++], head, e -> {
                Map<UUID, Boolean> next = new LinkedHashMap<>(blue);
                next.put(id, !isBlue);
                new TeamsGui(plugin, leader, next, back).open(leader);
            });
        }

        set(46, Items.of(Material.SUGAR).name(t.text("✦ shuffle", t.primary()))
                .lore(t.text("mix the teams up evenly", t.accent())).build(), e -> {
            List<UUID> ids = new ArrayList<>(blue.keySet());
            Collections.shuffle(ids);
            Map<UUID, Boolean> next = new LinkedHashMap<>();
            for (int k = 0; k < ids.size(); k++) next.put(ids.get(k), k % 2 == 1);
            new TeamsGui(plugin, leader, next, back).open(leader);
        });

        boolean ok = reds > 0 && blues > 0;
        List<Kit> kits = CustomKits.partyKits(plugin, party.members);
        Runnable reopen = () -> new TeamsGui(plugin, leader, blue, back).open(leader);
        set(49, Items.of(ok ? Material.EMERALD : Material.BARRIER)
                .name(t.text(ok ? "✦ pick a kit and start" : "each team needs a player", ok ? t.primary() : t.muted()))
                .lore(t.text(reds + " vs " + blues, t.accent()), t.text("both teams use the same kit", t.muted())).glow().build(), ok ? e ->
                Guis.pickKit(plugin, leader, "choose kit", reopen,
                        kit -> List.of(t.text("click to start " + reds + "v" + blues, t.secondary())),
                        kit -> begin(plugin, leader, blue, kit, null), kits, List.of()) : null);
        set(50, Items.of(ok ? Material.CRAFTING_TABLE : Material.BARRIER)
                .name(t.text(ok ? "✦ a kit for each team" : "each team needs a player", ok ? t.primary() : t.muted()))
                .lore(t.text("pick one for red, then one for blue", t.accent()), t.text("custom kits from your party work too", t.muted())).build(), ok ? e ->
                Guis.pickKit(plugin, leader, "red team kit", reopen,
                        kit -> List.of(t.text("click to pick for red", t.secondary())),
                        red -> Guis.pickKit(plugin, leader, "blue team kit", reopen,
                                kit -> List.of(t.text("click to start", t.secondary())),
                                blueKit -> begin(plugin, leader, blue, red, blueKit), kits, List.of()), kits, List.of()) : null);

        if (back != null) {
            set(52, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(52, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> leader.closeInventory());
        }
    }
}
