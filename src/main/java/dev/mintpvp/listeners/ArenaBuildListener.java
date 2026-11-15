package dev.mintpvp.listeners;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.ArenaManager.Arena;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * Lets duelists build and break inside an arena, and records every change so the
 * arena is put back exactly as it was when the duel ends. Nothing can be built or
 * broken on or beyond the border (that keeps walls and the outside intact).
 */
public final class ArenaBuildListener implements Listener {

    private final MintPvP plugin;

    public ArenaBuildListener(MintPvP plugin) {
        this.plugin = plugin;
    }

    private boolean anyActive() {
        return plugin.arenas().all().stream().anyMatch(a -> a.inUse);
    }

    /** True if this player is in a duel and the block is not safely inside their arena. */
    private boolean outOfBounds(Player p, Block b) {
        var duel = plugin.duels().duelOf(p.getUniqueId());
        if (duel == null) return false;
        Arena a = duel.arena;
        return !a.innerContains(b.getLocation().add(0.5, 0.5, 0.5));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.duels().inDuel(e.getPlayer().getUniqueId())) return;
        if (outOfBounds(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        plugin.duels().recordBlock(e.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.duels().inDuel(e.getPlayer().getUniqueId())) return;
        if (outOfBounds(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        plugin.duels().recordBlock(e.getBlock().getState());
        e.setDropItems(false); // no loot from the arena, and nothing to clean up
        e.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!plugin.duels().inDuel(e.getPlayer().getUniqueId())) return;
        if (outOfBounds(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        plugin.duels().recordBlock(e.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!plugin.duels().inDuel(e.getPlayer().getUniqueId())) return;
        if (outOfBounds(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        plugin.duels().recordBlock(e.getBlock().getState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!anyActive()) return;
        if (e.getPlayer() != null && plugin.duels().inDuel(e.getPlayer().getUniqueId()) && outOfBounds(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        plugin.duels().recordBlock(e.getBlock().getState());
    }

    // ---- things that change blocks without a player: fire, flowing liquid, explosions ----

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (anyActive()) plugin.duels().recordBlock(e.getBlock().getState());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (anyActive()) plugin.duels().recordBlock(e.getBlock().getState());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (anyActive()) plugin.duels().recordBlock(e.getToBlock().getState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!anyActive()) return;
        // keep the border and walls intact, record the rest so it's put back
        e.blockList().removeIf(b -> plugin.arenas().activeAt(b.getLocation()).map(a -> !a.innerContains(b.getLocation().add(0.5, 0.5, 0.5))).orElse(false));
        e.blockList().forEach(b -> plugin.duels().recordBlock(b.getState()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!anyActive()) return;
        e.blockList().removeIf(b -> plugin.arenas().activeAt(b.getLocation()).map(a -> !a.innerContains(b.getLocation().add(0.5, 0.5, 0.5))).orElse(false));
        e.blockList().forEach(b -> plugin.duels().recordBlock(b.getState()));
    }
}
