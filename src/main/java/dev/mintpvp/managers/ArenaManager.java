package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Admin-defined duel arenas, stored in arenas.yml.
 *
 * Locations are stored by world *name* and resolved on use, so an arena whose world
 * isn't loaded yet doesn't break loading (and can't wipe the file). The border always
 * grows to include both spawns, so a spawn can never end up "outside" its own arena.
 */
public final class ArenaManager {

    /** A stored position; resolved to a Location only when its world is loaded. */
    public record Loc(String world, double x, double y, double z, float yaw, float pitch) {
        static Loc of(Location l) {
            return new Loc(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }

        Location resolve() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    /** A square region (centre + radius, plus a vertical range) with two spawn points. */
    public final class Arena {
        public final String name;
        private Loc s1, s2, c;
        /** Configured half-width; 0 = config default. The effective radius may be larger. */
        public int radius;
        /** Configured vertical half-range; 0 = config default. The effective height may be larger. */
        public int height;
        public boolean inUse;

        Arena(String name) {
            this.name = name;
        }

        public Location spawn1() { return s1 == null ? null : s1.resolve(); }

        public Location spawn2() { return s2 == null ? null : s2.resolve(); }

        public void setSpawn1(Location l) { s1 = Loc.of(l); }

        public void setSpawn2(Location l) { s2 = Loc.of(l); }

        public void setCenter(Location l) { c = Loc.of(l); }

        /** Why this arena can't be used right now, or null if it can. */
        public String problem() {
            if (s1 == null) return "spawn 1 isn't set (/arena setspawn1 " + name + ")";
            if (s2 == null) return "spawn 2 isn't set (/arena setspawn2 " + name + ")";
            if (spawn1() == null) return "world '" + s1.world() + "' isn't loaded";
            if (spawn2() == null) return "world '" + s2.world() + "' isn't loaded";
            if (!s1.world().equals(s2.world())) return "the two spawns are in different worlds";
            return null;
        }

        public boolean ready() {
            return problem() == null;
        }

        public Location center() {
            if (c != null) return c.resolve();
            Location a = spawn1(), b = spawn2();
            if (a == null || b == null || !a.getWorld().equals(b.getWorld())) return null;
            return new Location(a.getWorld(), (a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2, (a.getZ() + b.getZ()) / 2);
        }

        /** Effective half-width: the configured one, grown if needed to contain both spawns. */
        public int radius() {
            int base = radius > 0 ? radius : Math.max(5, plugin.getConfig().getInt("duel.default-arena-radius", 40));
            Location ctr = center();
            if (ctr == null) return base;
            double far = 0;
            for (Location s : new Location[] {spawn1(), spawn2()}) {
                if (s != null) far = Math.max(far, Math.max(Math.abs(s.getX() - ctr.getX()), Math.abs(s.getZ() - ctr.getZ())));
            }
            return Math.max(base, (int) Math.ceil(far) + 5);
        }

        /** Effective vertical half-range, grown if needed to contain both spawns. */
        public int height() {
            int base = height > 0 ? height : Math.max(3, plugin.getConfig().getInt("duel.default-arena-height", 25));
            Location ctr = center();
            if (ctr == null) return base;
            double far = 0;
            for (Location s : new Location[] {spawn1(), spawn2()}) {
                if (s != null) far = Math.max(far, Math.abs(s.getY() - ctr.getY()));
            }
            return Math.max(base, (int) Math.ceil(far) + 3);
        }

        public double minY() { return center().getY() - height(); }

        public double maxY() { return center().getY() + height(); }

        /** Is this location inside the arena border (horizontally)? */
        public boolean containsXZ(Location l) {
            Location ctr = center();
            if (ctr == null || l.getWorld() == null || !l.getWorld().equals(ctr.getWorld())) return false;
            int r = radius();
            return Math.abs(l.getX() - ctr.getX()) <= r && Math.abs(l.getZ() - ctr.getZ()) <= r;
        }

        /** Inside the arena in all three dimensions. */
        public boolean contains(Location l) {
            return containsXZ(l) && l.getY() >= minY() && l.getY() <= maxY();
        }

        /** Bounding box of the whole arena volume. */
        public BoundingBox box() {
            Location ctr = center();
            int r = radius();
            return new BoundingBox(ctr.getX() - r, minY(), ctr.getZ() - r, ctr.getX() + r, maxY(), ctr.getZ() + r);
        }

        /** One-line description of a stored spawn for /arena check. */
        public String describe(Loc l) {
            if (l == null) return "not set";
            return l.world() + " " + Math.round(l.x()) + " " + Math.round(l.y()) + " " + Math.round(l.z())
                    + (l.resolve() == null ? " (world not loaded)" : "");
        }

        public String describeSpawn1() { return describe(s1); }

        public String describeSpawn2() { return describe(s2); }
    }

    private final MintPvP plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(MintPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    // ---- persistence ----------------------------------------------------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // Don't ever overwrite a file we couldn't read: keep a copy and say so loudly.
            File backup = new File(plugin.getDataFolder(), "arenas.broken-" + System.currentTimeMillis() + ".yml");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
            plugin.getLogger().severe("Could not read arenas.yml (" + e.getMessage() + "). A copy was kept as "
                    + backup.getName() + ". Arenas will be empty until this is fixed.");
            return;
        }
        ConfigurationSection sec = y.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            Arena a = new Arena(name);
            a.s1 = readLoc(sec, name + ".spawn1");
            a.s2 = readLoc(sec, name + ".spawn2");
            a.c = readLoc(sec, name + ".center");
            a.radius = sec.getInt(name + ".radius");
            a.height = sec.getInt(name + ".height");
            arenas.put(name.toLowerCase(Locale.ROOT), a);
        }
    }

    private static Loc readLoc(ConfigurationSection sec, String key) {
        Object o = sec.get(key);
        if (o instanceof Location l && l.getWorld() != null) return Loc.of(l); // older arenas.yml format
        ConfigurationSection s = sec.getConfigurationSection(key);
        if (s == null || !s.isString("world")) return null;
        return new Loc(s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static void writeLoc(YamlConfiguration y, String path, Loc l) {
        if (l == null) return;
        y.set(path + ".world", l.world());
        y.set(path + ".x", l.x());
        y.set(path + ".y", l.y());
        y.set(path + ".z", l.z());
        y.set(path + ".yaw", (double) l.yaw());
        y.set(path + ".pitch", (double) l.pitch());
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.name;
            writeLoc(y, p + ".spawn1", a.s1);
            writeLoc(y, p + ".spawn2", a.s2);
            writeLoc(y, p + ".center", a.c);
            y.set(p + ".radius", a.radius);
            y.set(p + ".height", a.height);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arenas.yml: " + e.getMessage());
        }
    }

    // ---- access ---------------------------------------------------------

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
