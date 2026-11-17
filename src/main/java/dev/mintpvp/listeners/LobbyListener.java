package dev.mintpvp.listeners;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.Gui;
import dev.mintpvp.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Hotbar menu clicks and lobby protection (no damage, hunger, building or item juggling). */
public final class LobbyListener implements Listener {

    private final MintPvP plugin;

    public LobbyListener(MintPvP plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Player p) {
        return plugin.lobby().protects() && plugin.lobby().inLobby(p);
    }

    private boolean canBuild(Player p) {
        return plugin.lobby().isBuilder(p); // builder mode (/build), off by default even for ops
    }

    private boolean inLobbyArea(org.bukkit.Location l) {
        return plugin.lobby().protects() && plugin.generator().inLobbyRegion(l);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = Items.tagOf(e.getItem());
        if (id == null) return;
        e.setCancelled(true); // e.g. don't place the ender chest
        plugin.lobby().handle(e.getPlayer(), id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !locked(p)) return;
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            p.teleport(plugin.spawn().get());
            p.setFallDistance(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    /** Lobby items can't be moved around in the inventory screen. GUIs handle their own clicks. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !locked(p) || canBuild(p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof Gui) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(i -> Items.tagOf(i) != null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (plugin.getConfig().getBoolean("spawn.teleport-on-respawn", true)) {
            e.setRespawnLocation(plugin.spawn().get());
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.lobby().give(p));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && locked(p) && !canBuild(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())
                && (e.getRightClicked() instanceof org.bukkit.entity.Hanging || e.getRightClicked() instanceof org.bukkit.entity.ArmorStand)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && locked(e.getPlayer()) && !canBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (inLobbyArea(e.getBlock().getLocation()) && (e.getPlayer() == null || !canBuild(e.getPlayer()))) e.setCancelled(true);
    }

    // things that change the lobby by themselves
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true); // the fountain can't spill
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true); // ice and snow don't melt
    }

    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockChangedByEntity(EntityChangeBlockEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (inLobbyArea(e.getLocation())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (inLobbyArea(e.getBlock().getLocation())) e.blockList().clear();
    }
}
