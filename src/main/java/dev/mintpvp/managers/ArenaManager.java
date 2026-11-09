package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Admin-defined duel arenas, stored in arenas.yml. Each has two spawn points. */
public final class ArenaManager {

    /** An arena is a square region (centre + radius) with two spawn points. */
    public final class Arena {
        public final String name;
        public Location spawn1, spawn2;
        /** Optional explicit centre; defaults to the midpoint of the spawns. */
        public Location center;
        /** Half-width of the square border in blocks; 0 = use config default. */
        public int radius;
        /** Vertical half-range around the centre's Y in blocks; 0 = use config default. */
        public int height;
        public boolean inUse;

        Arena(String name) {
            this.name = name;
        }

        public boolean spawnsSet() {
            return spawn1 != null && spawn2 != null && spawn1.getWorld() != null
                    && spawn1.getWorld().equals(spawn2.getWorld());
        }

        public Location center() {
            if (center != null) return center;
            if (!spawnsSet()) return null;
            return new Location(spawn1.getWorld(),
                    (spawn1.getX() + spawn2.getX()) / 2, (spawn1.getY() + spawn2.getY()) / 2,
                    (spawn1.getZ() + spawn2.getZ()) / 2);
        }

        public int radius() {
            return radius > 0 ? radius : Math.max(5, plugin.getConfig().getInt("duel.default-arena-radius", 40));
        }

        public int height() {
            return height > 0 ? height : Math.max(3, plugin.getConfig().getInt("duel.default-arena-height", 25));
        }

        public double minY() { return center().getY() - height(); }

        public double maxY() { return center().getY() + height(); }

        /** Is this location inside the arena border (horizontally)? */
        public boolean containsXZ(Location l) {
            Location c = center();
            if (c == null || l.getWorld() == null || !l.getWorld().equals(c.getWorld())) return false;
            int r = radius();
            return Math.abs(l.getX() - c.getX()) <= r && Math.abs(l.getZ() - c.getZ()) <= r;
        }

        /** Inside the arena in all three dimensions. */
        public boolean contains(Location l) {
            return containsXZ(l) && l.getY() >= minY() && l.getY() <= maxY();
        }

        /** Bounding box of the whole arena volume. */
        public org.bukkit.util.BoundingBox box() {
            Location c = center();
            return new org.bukkit.util.BoundingBox(c.getX() - radius(), minY(), c.getZ() - radius(),
                    c.getX() + radius(), maxY(), c.getZ() + radius());
        }

        /** Both spawns set and inside the border. */
        public boolean ready() {
            return spawnsSet() && contains(spawn1) && contains(spawn2);
        }
    }

    private final MintPvP plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(MintPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            Arena a = new Arena(name);
            a.spawn1 = sec.getLocation(name + ".spawn1");
            a.spawn2 = sec.getLocation(name + ".spawn2");
            a.center = sec.getLocation(name + ".center");
            a.radius = sec.getInt(name + ".radius");
            a.height = sec.getInt(name + ".height");
            arenas.put(name.toLowerCase(Locale.ROOT), a);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            y.set("arenas." + a.name + ".spawn1", a.spawn1);
            y.set("arenas." + a.name + ".spawn2", a.spawn2);
            y.set("arenas." + a.name + ".center", a.center);
            y.set("arenas." + a.name + ".radius", a.radius);
            y.set("arenas." + a.name + ".height", a.height);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arenas.yml: " + e.getMessage());
        }
    }

    public Arena get(String name) {
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return the new arena, or null if the name is taken. */
    public Arena create(String name) {
        if (get(name) != null) return null;
        Arena a = new Arena(name);
        arenas.put(name.toLowerCase(Locale.ROOT), a);
        save();
        return a;
    }

    public boolean delete(String name) {
        Arena a = get(name);
        if (a == null || a.inUse) return false;
        arenas.remove(name.toLowerCase(Locale.ROOT));
        save();
        return true;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    /** The in-use arena whose volume contains this location, if any. */
    public Optional<Arena> activeAt(Location l) {
        return arenas.values().stream().filter(a -> a.inUse && a.ready() && a.contains(l)).findFirst();
    }

    public Optional<Arena> randomFree() {
        List<Arena> free = arenas.values().stream().filter(a -> a.ready() && !a.inUse).toList();
        if (free.isEmpty()) return Optional.empty();
        return Optional.of(free.get(new Random().nextInt(free.size())));
    }
}
