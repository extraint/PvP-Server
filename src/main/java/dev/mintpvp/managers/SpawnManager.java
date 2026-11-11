package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** The lobby / world spawn, stored in spawn.yml and mirrored to the world's spawn point. */
public final class SpawnManager {

    private final MintPvP plugin;
    private final File file;
    private Location spawn;

    public SpawnManager(MintPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        if (file.exists()) spawn = YamlConfiguration.loadConfiguration(file).getLocation("spawn");
    }

    public boolean isSet() {
        return spawn != null && spawn.getWorld() != null;
    }

    /** The saved spawn, or the main world's spawn if none was set. */
    public Location get() {
        if (isSet()) return spawn.clone();
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void set(Location l) {
        spawn = l.clone();
        l.getWorld().setSpawnLocation(l);
        YamlConfiguration y = new YamlConfiguration();
        y.set("spawn", spawn);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save spawn.yml: " + e.getMessage());
        }
    }
}
