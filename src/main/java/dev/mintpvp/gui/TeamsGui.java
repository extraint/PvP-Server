package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
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

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    /** true = blue */
    private final Map<UUID, Boolean> blue = new LinkedHashMap<>();

    public TeamsGui(MintPvP plugin, Player leader, Map<UUID, Boolean> existing, Runnable back) {
        super(plugin, 5, plugin.theme().title("custom teams"));
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

        set(28, Items.of(Material.SUGAR).name(t.text("✦ shuffle", t.primary()))
                .lore(t.text("mix the teams up evenly", t.accent())).build(), e -> {
            List<UUID> ids = new ArrayList<>(blue.keySet());
            Collections.shuffle(ids);
            Map<UUID, Boolean> next = new LinkedHashMap<>();
            for (int k = 0; k < ids.size(); k++) next.put(ids.get(k), k % 2 == 1);
            new TeamsGui(plugin, leader, next, back).open(leader);
        });

        boolean ok = reds > 0 && blues > 0;
        set(32, Items.of(ok ? Material.EMERALD : Material.BARRIER)
                .name(t.text(ok ? "✦ pick a kit and start" : "each team needs a player", ok ? t.primary() : t.muted()))
                .lore(t.text(reds + " vs " + blues, t.accent())).glow().build(), ok ? e ->
                Guis.pickKit(plugin, leader, "choose kit", () -> new TeamsGui(plugin, leader, blue, back).open(leader),
                        kit -> List.of(t.text("click to start " + reds + "v" + blues, t.secondary())),
                        kit -> {
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
                            plugin.duels().startScrim(leader, red, blu, kit.id);
                        }) : null);

        if (back != null) {
            set(40, Items.of(Material.SPECTRAL_ARROW).name(t.text("« back", t.secondary())).build(), e -> back.run());
        } else {
            set(40, Items.of(Material.BARRIER).name(t.text("close", t.secondary())).build(), e -> leader.closeInventory());
        }
    }
}
