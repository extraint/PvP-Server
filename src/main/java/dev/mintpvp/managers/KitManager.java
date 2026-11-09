package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/** Builds and equips the config-driven PvP kit. */
public final class KitManager {

    private final MintPvP plugin;

    public KitManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    private ItemStack armor(String path) {
        FileConfiguration c = plugin.getConfig();
        Material m = Material.matchMaterial(c.getString(path, "AIR"));
        return m == null ? null : new ItemStack(m);
    }

    private List<ItemStack> items() {
        List<ItemStack> out = new ArrayList<>();
        for (String entry : plugin.getConfig().getStringList("kit.items")) {
            String[] parts = entry.split(":");
            Material m = Material.matchMaterial(parts[0].trim());
            if (m == null || m.isAir()) {
                plugin.getLogger().warning("Unknown kit material: " + parts[0]);
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {
                    // default to 1
                }
            }
            out.add(new ItemStack(m, amount));
        }
        return out;
    }

    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(armor("kit.helmet"));
        inv.setChestplate(armor("kit.chestplate"));
        inv.setLeggings(armor("kit.leggings"));
        inv.setBoots(armor("kit.boots"));
        for (ItemStack item : items()) {
            inv.addItem(item);
        }
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }
}
