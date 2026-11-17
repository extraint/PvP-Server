package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** spawn storage */
public final class SpawnManager {

    private final MintPvP plugin;
    private final File file;
    private String world;
    private double x, y, z;
    private float yaw, pitch;
    private boolean set;

    public SpawnManager(MintPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Could not read spawn.yml (" + e.getMessage() + "); /setspawn again to fix it.");
            return;
        }
        ConfigurationSection s = y.getConfigurationSection("spawn");
        if (s != null && s.isString("name")) {
            world = s.getString("name");
            x = s.getDouble("x");
            y2(s);
            set = true;
        } else if (y.get("spawn") instanceof Location l && l.getWorld() != null) {
            remember(l);
        }
    }

    private void y2(ConfigurationSection s) {
        y = s.getDouble("y");
        z = s.getDouble("z");
        yaw = (float) s.getDouble("yaw");
        pitch = (float) s.getDouble("pitch");
    }

    private void remember(Location l) {
        world = l.getWorld().getName();
        x = l.getX();
        y = l.getY();
        z = l.getZ();
        yaw = l.getYaw();
        pitch = l.getPitch();
        set = true;
    }

    private Location resolve() {
        World w = world == null ? null : Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public boolean isSet() {
        return set && resolve() != null;
    }

    /** saved spawn */
    public Location get() {
        Location l = set ? resolve() : null;
        return l != null ? l : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void set(Location l) {
        remember(l);
        l.getWorld().setSpawnLocation(l);
        YamlConfiguration out = new YamlConfiguration();
        out.set("spawn.name", world);
        out.set("spawn.x", x);
        out.set("spawn.y", y);
        out.set("spawn.z", z);
        out.set("spawn.yaw", (double) yaw);
        out.set("spawn.pitch", (double) pitch);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save spawn.yml: " + e.getMessage());
        }
    }
}
