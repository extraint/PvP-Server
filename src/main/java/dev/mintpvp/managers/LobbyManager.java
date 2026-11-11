package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.Guis;
import dev.mintpvp.gui.HubGui;
import dev.mintpvp.gui.LeaderboardGui;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The hotbar menu given to players in the lobby, and what each item does. */
public final class LobbyManager {

    private final MintPvP plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public LobbyManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public boolean givesItems() {
        return plugin.getConfig().getBoolean("lobby.give-items", true);
    }

    public boolean protects() {
        return plugin.getConfig().getBoolean("lobby.protect", true);
    }

    /** In the lobby = not currently fighting or spectating a duel. */
    public boolean inLobby(Player p) {
        return !plugin.duels().inDuel(p.getUniqueId()) && !plugin.duels().isSpectating(p.getUniqueId());
    }

    /** Replaces the inventory with the lobby hotbar. */
    public void give(Player p) {
        if (!givesItems()) return;
        Theme t = plugin.theme();
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, queueItem(p, QueueManager.Type.UNRANKED));
        inv.setItem(1, queueItem(p, QueueManager.Type.RANKED));
        inv.setItem(2, Items.of(Material.BOW).name(t.text("✦ challenge a player", t.primary()))
                .lore(t.text("right-click to duel a friend", t.accent())).tag("challenge").build());
        inv.setItem(4, Items.of(Material.NETHER_STAR).name(t.text("✦ menu", t.primary()))
                .lore(t.text("right-click for everything", t.accent())).glow().tag("menu").build());
        inv.setItem(5, Items.of(Material.BOOK).name(t.text("✦ your stats", t.primary()))
                .lore(t.text("right-click to view", t.accent())).tag("stats").build());
        inv.setItem(6, Items.of(Material.ENDER_CHEST).name(t.text("✦ edit kit layout", t.primary()))
                .lore(t.text("right-click to arrange your kits", t.accent())).tag("layout").build());
        inv.setItem(7, Items.of(Material.GOLD_INGOT).name(t.text("✦ leaderboard", t.primary()))
                .lore(t.text("right-click to see the top players", t.accent())).tag("leaderboard").build());
        inv.setItem(8, Items.of(Material.ENDER_EYE).name(t.text("✦ spectate", t.primary()))
                .lore(t.text("right-click to watch a duel", t.accent())).tag("spectate").build());
        inv.setHeldItemSlot(0);
        p.setFoodLevel(20);
    }

    private ItemStack queueItem(Player p, QueueManager.Type type) {
        Theme t = plugin.theme();
        boolean ranked = type == QueueManager.Type.RANKED;
        var q = plugin.queue().entryOf(p.getUniqueId());
        if (q != null && q.type() == type) {
            return Items.of(Material.REDSTONE).name(t.text("✦ leave queue", t.secondary()))
                    .lore(t.text("queued: " + (ranked ? "ranked " : "") + q.kit().display, t.muted()),
                            t.text("right-click to leave", t.accent()))
                    .glow().tag("leave_queue").build();
        }
        return Items.of(ranked ? Material.DIAMOND_SWORD : Material.IRON_SWORD)
                .name(t.text("✦ queue - " + (ranked ? "ranked" : "unranked"), t.primary()))
                .lore(t.text("right-click to find a match", t.accent()))
                .tag(ranked ? "queue_ranked" : "queue_unranked").build();
    }

    /** Re-draws the two queue items (only if the slots still hold lobby items). */
    public void refresh(Player p) {
        if (!givesItems()) return;
        PlayerInventory inv = p.getInventory();
        if (Items.tagOf(inv.getItem(0)) != null) inv.setItem(0, queueItem(p, QueueManager.Type.UNRANKED));
        if (Items.tagOf(inv.getItem(1)) != null) inv.setItem(1, queueItem(p, QueueManager.Type.RANKED));
    }

    /** Runs the action behind a right-clicked lobby item. */
    public void handle(Player p, String id) {
        long now = System.currentTimeMillis();
        if (now - cooldown.getOrDefault(p.getUniqueId(), 0L) < 250) return;
        cooldown.put(p.getUniqueId(), now);

        Theme t = plugin.theme();
        switch (id) {
            case "queue_unranked" -> Guis.queueKits(plugin, p, QueueManager.Type.UNRANKED, null);
            case "queue_ranked" -> Guis.queueKits(plugin, p, QueueManager.Type.RANKED, null);
            case "leave_queue" -> {
                plugin.queue().leave(p.getUniqueId());
                p.sendMessage(t.info("you left the queue."));
            }
            case "challenge" -> Guis.challenge(plugin, p, null);
            case "menu" -> new HubGui(plugin, p).open(p);
            case "layout" -> Guis.layoutKits(plugin, p, null);
            case "leaderboard" -> new LeaderboardGui(plugin, p, "elo", null).open(p);
            case "spectate" -> Guis.spectateList(plugin, p, null);
            case "stats" -> p.performCommand("stats");
            default -> { }
        }
    }
}
