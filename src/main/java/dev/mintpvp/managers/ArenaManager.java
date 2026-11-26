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

/** arena storage */
public final class ArenaManager {

    /** stored position */
    public record Loc(String world, double x, double y, double z, float yaw, float pitch) {
        static Loc of(Location l) {
            return new Loc(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }

        Location resolve() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    /** arena region */
    public final class Arena {
        public final String name;
        private Loc s1, s2, c;
        /** configured radius */
        public int radius;
        /** configured height */
        public int height;
        public boolean inUse;
        /** generated arena info */
        public String theme;
        public int slot = -1;
        /** ranked-size arena */
        public boolean large;
        /** still generating */
        public boolean pending;

        Arena(String name) {
            this.name = name;
        }

        public Location spawn1() { return s1 == null ? null : s1.resolve(); }

        public Location spawn2() { return s2 == null ? null : s2.resolve(); }

        public void setSpawn1(Location l) { s1 = Loc.of(l); }

        public void setSpawn2(Location l) { s2 = Loc.of(l); }

        public void setCenter(Location l) { c = Loc.of(l); }

        /** problem or null */
        public String problem() {
            if (pending) return "still being generated";
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

        /** effective radius */
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

        /** effective height */
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

        /** height limit off */
        public boolean heightLimited() {
            return plugin.getConfig().getBoolean("duel.height-limit", false);
        }

        public double minY() {
            Location ctr = center();
            return heightLimited() ? ctr.getY() - height() : ctr.getWorld().getMinHeight();
        }

        public double maxY() {
            Location ctr = center();
            return heightLimited() ? ctr.getY() + height() : ctr.getWorld().getMaxHeight();
        }

        /** buildable here? */
        public boolean innerContains(Location l) {
            Location ctr = center();
            if (ctr == null || l.getWorld() == null || !l.getWorld().equals(ctr.getWorld())) return false;
            double r = radius(), dx = l.getX() - ctr.getX(), dz = l.getZ() - ctr.getZ();
            if (circular()) return dx * dx + dz * dz <= r * r;
            return Math.abs(dx) <= r && Math.abs(dz) <= r;
        }

        /** inside border? */
        public boolean containsXZ(Location l) {
            Location ctr = center();
            if (ctr == null || l.getWorld() == null || !l.getWorld().equals(ctr.getWorld())) return false;
            int r = radius();
            double dx = l.getX() - ctr.getX(), dz = l.getZ() - ctr.getZ();
            if (circular()) return dx * dx + dz * dz <= (double) r * r;
            return Math.abs(dx) <= r && Math.abs(dz) <= r;
        }

        /** round or square */
        public boolean circular() {
            return theme != null;
        }

        /** inside arena? */
        public boolean contains(Location l) {
            return containsXZ(l) && l.getY() >= minY() && l.getY() <= maxY();
        }

        /** arena bounds */
        public BoundingBox box() {
            Location ctr = center();
            int r = radius();
            return new BoundingBox(ctr.getX() - r, minY(), ctr.getZ() - r, ctr.getX() + r, maxY(), ctr.getZ() + r);
        }

        /** spawn description */
        public String describe(Loc l) {
            if (l == null) return "not set";
            return l.world() + " " + Math.round(l.x()) + " " + Math.round(l.y()) + " " + Math.round(l.z())
                    + (l.resolve() == null ? " (world not loaded)" : "");
        }

        public String worldName() { return s1 == null ? null : s1.world(); }

        public String describeSpawn1() { return describe(s1); }

        public String describeSpawn2() { return describe(s2); }
    }

    private final MintPvP plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private int nextSlot;

    public ArenaManager(MintPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // keep unreadable file
            File backup = new File(plugin.getDataFolder(), "arenas.broken-" + System.currentTimeMillis() + ".yml");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            plugin.getLogger().severe("Could not read arenas.yml (" + e.getMessage() + "). A copy was kept as "
                    + backup.getName() + ". Arenas will be empty until this is fixed.");
            return;
        }
        nextSlot = Math.max(0, y.getInt("next-slot", 0));
        ConfigurationSection sec = y.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            Arena a = new Arena(name);
            a.s1 = readLoc(sec, name + ".spawn1");
            a.s2 = readLoc(sec, name + ".spawn2");
            a.c = readLoc(sec, name + ".center");
            a.radius = sec.getInt(name + ".radius");
            a.height = sec.getInt(name + ".height");
            a.theme = sec.getString(name + ".theme");
            a.slot = sec.getInt(name + ".slot", -1);
            a.large = sec.getBoolean(name + ".large");
            arenas.put(name.toLowerCase(Locale.ROOT), a);
        }
    }

    private static Loc readLoc(ConfigurationSection sec, String key) {
        Object o = sec.get(key);
        if (o instanceof Location l && l.getWorld() != null) return Loc.of(l);
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
        y.set("next-slot", nextSlot);
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.name;
            writeLoc(y, p + ".spawn1", a.s1);
            writeLoc(y, p + ".spawn2", a.s2);
            writeLoc(y, p + ".center", a.c);
            y.set(p + ".radius", a.radius);
            y.set(p + ".height", a.height);
            if (a.theme != null) {
                y.set(p + ".theme", a.theme);
                y.set(p + ".slot", a.slot);
                y.set(p + ".large", a.large);
            }
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

    /** new arena or null */
    public Arena create(String name) {
        if (get(name) != null) return null;
        Arena a = new Arena(name);
        arenas.put(name.toLowerCase(Locale.ROOT), a);
        save();
        return a;
    }

    /** next slot */
    public int nextSlot() {
        int n = nextSlot++;
        save();
        return n;
    }

    /** register generated arena */
    public Arena addGenerated(String name, String theme, int slot, Location spawn1, Location spawn2, boolean large, int radius) {
        Arena a = new Arena(name);
        a.setSpawn1(spawn1);
        a.setSpawn2(spawn2);
        a.theme = theme;
        a.slot = slot;
        a.large = large;
        a.radius = radius;
        a.pending = true;
        arenas.put(name.toLowerCase(Locale.ROOT), a);
        save();
        return a;
    }

    /** drop other-world arenas */
    public int removeGeneratedOutside(String worldName) {
        int before = arenas.size();
        arenas.values().removeIf(a -> a.theme != null && (a.worldName() == null || !a.worldName().equals(worldName)));
        int removed = before - arenas.size();
        if (removed > 0) save();
        return removed;
    }

    /** drop old-size arenas */
    public int removeSmallNotRadius(int radius) {
        int before = arenas.size();
        arenas.values().removeIf(a -> a.theme != null && !a.large && a.radius != radius);
        int removed = before - arenas.size();
        if (removed > 0) save();
        return removed;
    }

    public boolean delete(String name) {
        Arena a = get(name);
        if (a == null || a.inUse) return false;
        arenas.remove(name.toLowerCase(Locale.ROOT));
        if (a.theme != null && plugin.generator() != null) plugin.generator().forget(a);
        save();
        return true;
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    /** active arena at */
    public Optional<Arena> activeAt(Location l) {
        return arenas.values().stream().filter(a -> a.inUse && a.ready() && a.contains(l)).findFirst();
    }

    private static boolean stadium(Arena a) {
        return "stadium".equals(a.theme);
    }

    /** kits that always play in */
    public boolean isStadiumKit(String kitId) {
        return kitId != null && plugin.getConfig().getStringList("arena-generator.stadium-kits").contains(kitId.toLowerCase(Locale.ROOT));
    }

    /** free arena */
    public Optional<Arena> randomFree(boolean ranked) {
        return randomFree(ranked, null);
    }

    /** free arena for a kit */
    public Optional<Arena> randomFree(boolean ranked, String kitId) {
        List<Arena> free;
        if (isStadiumKit(kitId) && arenas.values().stream().anyMatch(ArenaManager::stadium)) {
            free = arenas.values().stream().filter(a -> a.ready() && !a.inUse && stadium(a)).toList();
        } else {
            free = arenas.values().stream().filter(a -> a.ready() && !a.inUse && !stadium(a) && (ranked ? a.large : !a.large)).toList();
            if (free.isEmpty() && ranked && arenas.values().stream().noneMatch(a -> a.large)) {
                free = arenas.values().stream().filter(a -> a.ready() && !a.inUse && !stadium(a)).toList();
            }
        }
        if (free.isEmpty()) return Optional.empty();
        return Optional.of(free.get(new Random().nextInt(free.size())));
    }
}
