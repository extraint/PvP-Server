package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Routes inventory events to Mint GUIs and drives the border animation. */
public final class GuiManager implements Listener {

    private final Set<Gui> open = new HashSet<>();
    private final BukkitTask ticker;

    public GuiManager(MintPvP plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> open.forEach(Gui::animate), 3L, 3L);
    }

    public void shutdown() {
        ticker.cancel();
        for (Gui g : new ArrayList<>(open)) {
            new ArrayList<>(g.getInventory().getViewers()).forEach(HumanEntity::closeInventory);
        }
        open.clear();
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g) open.add(g);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui g)) return;
        e.setCancelled(true); // nothing moves unless the GUI explicitly allows it
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        g.handleClick(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui)) return;
        int size = e.getView().getTopInventory().getSize();
        if (e.getRawSlots().stream().anyMatch(s -> s < size)) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui g)) return;
        if (e.getPlayer() instanceof Player p) g.onClose(p);
        if (g.getInventory().getViewers().stream().allMatch(v -> v.equals(e.getPlayer()))) open.remove(g);
    }
}
