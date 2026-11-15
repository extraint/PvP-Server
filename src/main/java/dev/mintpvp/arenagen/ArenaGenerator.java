package dev.mintpvp.arenagen;

import dev.mintpvp.MintPvP;
import dev.mintpvp.arenagen.ArenaLayout.Cell;
import dev.mintpvp.arenagen.ArenaLayout.Feature;
import dev.mintpvp.managers.ArenaManager.Arena;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Builds the pre-made arenas into a dedicated void world.
 *
 * On start it tops up a pool (config: per-theme), and when every arena is busy it
 * builds one more on demand (up to max-total). Builds run one at a time and are
 * spread over ticks so the server never freezes.
 */
public final class ArenaGenerator {

    /** Floor height in the arena world; players stand one block above. */
    public static final int FLOOR_Y = 64;
    /** Barrier ring above the wall reaches this many blocks above the floor. */
    public static final int CEILING = 20;
    /** Distance between arena centres. */
    public static final int SPACING = 300;

    private static final Map<String, Material> MAT = new HashMap<>();
    static {
        MAT.put("grass", Material.GRASS_BLOCK);
        MAT.put("coarse", Material.COARSE_DIRT);
        MAT.put("path", Material.DIRT_PATH);
        MAT.put("podzol", Material.PODZOL);
        MAT.put("mossycobble", Material.MOSSY_COBBLESTONE);
        MAT.put("oaklog", Material.OAK_LOG);
        MAT.put("oakleaves", Material.OAK_LEAVES);
        MAT.put("hay", Material.HAY_BLOCK);
        MAT.put("cobble", Material.MOSSY_COBBLESTONE);
        MAT.put("sand", Material.SAND);
        MAT.put("smooth", Material.SMOOTH_SANDSTONE);
        MAT.put("cut", Material.CUT_SANDSTONE);
        MAT.put("chiseled", Material.CHISELED_SANDSTONE);
        MAT.put("pillar", Material.SANDSTONE);
        MAT.put("dune", Material.SAND);
        MAT.put("poplarlog", Material.POPLAR_LOG);
        MAT.put("strippedpoplar", Material.STRIPPED_POPLAR_LOG);
        MAT.put("poplaryellow", Material.YELLOW_POPLAR_LEAVES);
        MAT.put("poplarorange", Material.ORANGE_POPLAR_LEAVES);
        MAT.put("poplarred", Material.RED_POPLAR_LEAVES);
        MAT.put("fallen", Material.POPLAR_LOG);
        MAT.put("snow", Material.SNOW_BLOCK);
        MAT.put("packedice", Material.PACKED_ICE);
        MAT.put("ice", Material.ICE);
        MAT.put("sprucelog", Material.SPRUCE_LOG);
        MAT.put("spruce", Material.SPRUCE_LEAVES);
        MAT.put("drift", Material.SNOW_BLOCK);
        MAT.put("spike", Material.PACKED_ICE);
        MAT.put("cherrylog", Material.CHERRY_LOG);
        MAT.put("cherry", Material.CHERRY_LEAVES);
        MAT.put("cherryplanks", Material.CHERRY_PLANKS);
        MAT.put("stonebrick", Material.MOSSY_STONE_BRICKS);
        MAT.put("gardenwall", Material.STONE_BRICKS);
        MAT.put("pad", Material.LIME_CONCRETE);
        MAT.put("spawn", Material.EMERALD_BLOCK);
        MAT.put("inlay", Material.LIME_TERRACOTTA);
        // decorations
        MAT.put("dandelion", Material.DANDELION);
        MAT.put("poppy", Material.POPPY);
        MAT.put("petals", Material.PINK_PETALS);
        MAT.put("deadbush", Material.DEAD_BUSH);
        MAT.put("fern", Material.FERN);
        MAT.put("snowlayer", Material.SNOW);
        MAT.put("leaflitter", Material.LEAF_LITTER);
    }

    /** Feature blocks that are built as plain columns straight from the plan. */
    private static final Set<String> COLUMNS = Set.of("hay", "cobble", "cut", "pillar", "dune", "fallen", "drift", "spike", "gardenwall");

    private record Job(ArenaTheme theme, int slot, String name, Arena existing) {}

    private final MintPvP plugin;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private final Map<Material, BlockData> dataCache = new EnumMap<>(Material.class);
    private World world;
    private BukkitTask task;

    // the build in progress
    private Job active;
    private int[] px, py, pz;
    private BlockData[] pd;
    private int cursor, count;

    public ArenaGenerator(MintPvP plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("arena-generator.enabled", true);
    }

    public World world() {
        return world;
    }

    // ---- lifecycle ------------------------------------------------------

    public void start() {
        if (!enabled()) return;
        String name = plugin.getConfig().getString("arena-generator.world", "mint_arenas");
        try {
            world = new WorldCreator(name).generator(new VoidGenerator()).environment(World.Environment.NORMAL)
                    .generateStructures(false).createWorld();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Could not create the arena world '" + name + "': " + e.getMessage());
            return;
        }
        if (world == null) {
            plugin.getLogger().severe("Could not create the arena world '" + name + "'.");
            return;
        }
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);

        fillPool();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (active != null && active.existing() != null) active.existing().inUse = false;
        jobs.clear();
        active = null;
    }

    private int perTheme() {
        return Math.max(0, plugin.getConfig().getInt("arena-generator.per-theme", 2));
    }

    private int maxTotal() {
        return Math.max(1, plugin.getConfig().getInt("arena-generator.max-total", 30));
    }

    /** Generated arenas plus the ones queued or being built. */
    public int total() {
        return (int) plugin.arenas().all().stream().filter(a -> a.theme != null).count()
                + jobs.size() + (active != null && active.existing() == null ? 1 : 0);
    }

    public boolean building() {
        return active != null || !jobs.isEmpty();
    }

    public String status() {
        if (active == null) return jobs.isEmpty() ? "idle" : jobs.size() + " queued";
        int pct = count == 0 ? 0 : (int) (100L * cursor / count);
        return "building " + active.name() + " (" + pct + "%)" + (jobs.isEmpty() ? "" : ", " + jobs.size() + " queued");
    }

    private void fillPool() {
        for (ArenaTheme t : ArenaTheme.values()) {
            long have = plugin.arenas().all().stream().filter(a -> t.id.equals(a.theme)).count();
            for (long i = have; i < perTheme() && total() < maxTotal(); i++) enqueue(t);
        }
        if (!jobs.isEmpty()) plugin.getLogger().info("Building " + jobs.size() + " pre-made arenas in the background...");
    }

    private int nextSlot() {
        int max = -1;
        for (Arena a : plugin.arenas().all()) max = Math.max(max, a.slot);
        for (Job j : jobs) max = Math.max(max, j.slot());
        if (active != null) max = Math.max(max, active.slot());
        return max + 1;
    }

    private String nextName(ArenaTheme t) {
        Set<String> taken = new HashSet<>();
        plugin.arenas().all().forEach(a -> taken.add(a.name.toLowerCase(Locale.ROOT)));
        jobs.forEach(j -> taken.add(j.name()));
        if (active != null) taken.add(active.name());
        for (int i = 1; ; i++) {
            String n = t.id + "-" + i;
            if (!taken.contains(n)) return n;
        }
    }

    private void enqueue(ArenaTheme t) {
        jobs.add(new Job(t, nextSlot(), nextName(t), null));
    }

    /** Queues new arenas (admin command). @return how many were queued. */
    public int generate(ArenaTheme theme, int amount) {
        if (world == null) return 0;
        int queued = 0;
        Random rnd = new Random();
        for (int i = 0; i < amount && total() < maxTotal(); i++) {
            enqueue(theme != null ? theme : ArenaTheme.values()[rnd.nextInt(ArenaTheme.values().length)]);
            queued++;
        }
        return queued;
    }

    /** Builds one extra random arena when all are busy. @return true if one is (now) on its way. */
    public boolean requestExtra() {
        if (!enabled() || world == null) return false;
        if (building()) return true;
        if (total() >= maxTotal()) return false;
        enqueue(ArenaTheme.values()[new Random().nextInt(ArenaTheme.values().length)]);
        return true;
    }

    /** Re-places every block of a generated arena (e.g. after someone edited it). */
    public boolean rebuild(Arena a) {
        ArenaTheme t = ArenaTheme.byId(a.theme);
        if (world == null || t == null || a.slot < 0 || a.inUse) return false;
        a.inUse = true; // nobody gets sent there mid-rebuild
        jobs.add(new Job(t, a.slot, a.name, a));
        return true;
    }

    // ---- building -------------------------------------------------------

    private static int centerX(int slot) {
        return (slot % 10) * SPACING;
    }

    private static int centerZ(int slot) {
        return (slot / 10) * SPACING;
    }

    private void tick() {
        if (active == null) {
            active = jobs.poll();
            if (active == null) return;
            try {
                prepare(active);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not plan arena " + active.name(), e);
                if (active.existing() != null) active.existing().inUse = false;
                active = null;
                return;
            }
        }
        int budget = Math.max(500, plugin.getConfig().getInt("arena-generator.blocks-per-tick", 5000));
        try {
            int end = Math.min(count, cursor + budget);
            for (; cursor < end; cursor++) {
                world.getBlockAt(px[cursor], py[cursor], pz[cursor]).setBlockData(pd[cursor], false);
            }
            if (cursor >= count) finish(active);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Arena build failed for " + active.name(), e);
            if (active.existing() != null) active.existing().inUse = false;
            active = null;
        }
    }

    private BlockData data(Material m) {
        return dataCache.computeIfAbsent(m, k -> {
            BlockData d = k.createBlockData();
            // fuller patches of petals / litter, if this version uses these properties
            try {
                if (k == Material.PINK_PETALS) d = Bukkit.createBlockData("minecraft:pink_petals[flower_amount=4]");
                if (k == Material.LEAF_LITTER) d = Bukkit.createBlockData("minecraft:leaf_litter[segment_amount=3]");
            } catch (IllegalArgumentException ignored) {
                // keep the default state
            }
            if (d instanceof Leaves l) l.setPersistent(true); // never decay
            return d;
        });
    }

    private BlockData data(String key) {
        Material m = MAT.get(key);
        if (m == null) throw new IllegalStateException("No block for '" + key + "'");
        return data(m);
    }

    private BlockData log(String key, Axis axis) {
        BlockData d = data(key).clone();
        if (d instanceof Orientable o) o.setAxis(axis);
        return d;
    }

    /** Turns the plan into a list of block placements. */
    private void prepare(Job job) {
        ArenaTheme theme = job.theme();
        ArenaLayout.Spec spec = ArenaLayout.spec(theme);
        Cell[] cells = ArenaLayout.build(theme);
        int cx = centerX(job.slot()), cz = centerZ(job.slot()), y0 = FLOOR_Y;
        int n = ArenaLayout.N, c = ArenaLayout.C;

        Map<Long, BlockData> out = new LinkedHashMap<>(220_000);
        BlockData air = data(Material.AIR);
        BlockData sub = data(theme.sub);
        BlockData barrier = data(Material.BARRIER);

        // 1. clear the whole slot (makes rebuilds and half-finished builds safe)
        for (int dx = -c; dx <= c; dx++)
            for (int dz = -c; dz <= c; dz++)
                for (int y = y0 - 2; y <= y0 + CEILING + 2; y++) out.put(key(cx + dx, y, cz + dz), air);

        // 2. floor, walls, barrier ring, decorations and column features
        for (int dz = -c; dz <= c; dz++) {
            for (int dx = -c; dx <= c; dx++) {
                Cell cell = cells[(dz + c) * n + (dx + c)];
                if (cell == null) continue;
                int x = cx + dx, z = cz + dz;
                out.put(key(x, y0 - 2, z), sub);
                out.put(key(x, y0 - 1, z), sub);
                if (cell.wall) {
                    out.put(key(x, y0, z), sub);
                    BlockData w = data(cell.b);
                    for (int y = 1; y <= cell.h; y++) out.put(key(x, y0 + y, z), w);
                    for (int y = cell.h + 1; y <= CEILING; y++) out.put(key(x, y0 + y, z), barrier);
                    continue;
                }
                out.put(key(x, y0, z), data(cell.floor));
                if (cell.dec != null && cell.h == 0) out.put(key(x, y0 + 1, z), data(cell.dec));
                if (cell.h > 0 && COLUMNS.contains(cell.b)) {
                    for (int y = 1; y <= cell.h; y++) {
                        BlockData d;
                        if (cell.b.equals("fallen")) d = log("fallen", Axis.Z);
                        else if (cell.b.equals("pillar") && y == cell.h) d = data(Material.CHISELED_SANDSTONE);
                        else d = data(cell.b);
                        out.put(key(x, y0 + y, z), d);
                    }
                }
            }
        }

        // 3. trees, in 3D, mirrored exactly like the plan
        Set<String> placed = new HashSet<>();
        for (Feature f : spec.features()) {
            if (!f.k().equals("tree")) continue;
            for (int sx : new int[] {1, -1}) {
                for (int sz : new int[] {1, -1}) {
                    int tx = sx * f.x(), tz = sz * f.z();
                    if (!placed.add(tx + "," + tz + f.leaves())) continue;
                    tree(out, f, cx, cz, tx, tz, y0);
                }
            }
        }

        // flatten
        count = out.size();
        px = new int[count];
        py = new int[count];
        pz = new int[count];
        pd = new BlockData[count];
        int i = 0;
        for (var e : out.entrySet()) {
            long k = e.getKey();
            px[i] = (int) (k >> 38);
            py[i] = (int) ((k << 26) >> 52);
            pz[i] = (int) ((k << 38) >> 38);
            pd[i] = e.getValue();
            i++;
        }
        cursor = 0;
    }

    private void tree(Map<Long, BlockData> out, Feature f, int cx, int cz, int tx, int tz, int y0) {
        int h = f.h(), r = (int) f.r();
        BlockData leaf = data(f.leaves());
        // {height above floor, radius}
        List<int[]> layers = new ArrayList<>();
        switch (f.trunk()) {
            case "poplarlog" -> { // tall and narrow
                for (int y = 4; y <= h; y++) layers.add(new int[] {y, r});
                layers.add(new int[] {h + 1, 0});
            }
            case "sprucelog" -> { // cone
                layers.add(new int[] {3, 2});
                layers.add(new int[] {4, 1});
                layers.add(new int[] {5, 2});
                layers.add(new int[] {6, 1});
                layers.add(new int[] {h, 1});
                layers.add(new int[] {h + 1, 0});
            }
            case "cherrylog" -> { // wide umbrella
                layers.add(new int[] {h - 1, Math.max(1, r - 1)});
                layers.add(new int[] {h, r});
                layers.add(new int[] {h + 1, Math.max(1, r - 1)});
            }
            default -> { // oak
                layers.add(new int[] {h - 1, r});
                layers.add(new int[] {h, r});
                layers.add(new int[] {h + 1, Math.max(1, r - 1)});
            }
        }
        for (int[] layer : layers) {
            int ly = layer[0], lr = layer[1];
            for (int x = tx - lr; x <= tx + lr; x++) {
                for (int z = tz - lr; z <= tz + lr; z++) {
                    if (Math.hypot(x - tx, z - tz) > lr + .35) continue;
                    if (!ArenaLayout.inside(x, z) || ArenaLayout.nearSpawn(x, z)) continue;
                    if (x == tx && z == tz && ly <= h) continue; // trunk goes here
                    out.put(key(cx + x, y0 + ly, cz + z), leaf);
                }
            }
        }
        if (ArenaLayout.inside(tx, tz) && !ArenaLayout.nearSpawn(tx, tz)) {
            BlockData trunk = log(f.trunk(), Axis.Y);
            for (int y = 1; y <= h; y++) out.put(key(cx + tx, y0 + y, cz + tz), trunk);
            if (f.trunk().equals("sprucelog")) out.put(key(cx + tx, y0 + h + 2, cz + tz), data(Material.SNOW));
        }
    }

    /** Packs a block position (x, z: 26 bits signed; y: 12 bits signed) into a long. */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private void finish(Job job) {
        int cx = centerX(job.slot()), cz = centerZ(job.slot());
        // biome for grass, leaf and sky colours
        var biome = job.theme().biome();
        int c = ArenaLayout.C;
        for (int dx = -c; dx <= c; dx += 2)
            for (int dz = -c; dz <= c; dz += 2)
                for (int y = FLOOR_Y - 4; y <= FLOOR_Y + CEILING + 4; y += 4) world.setBiome(cx + dx, y, cz + dz, biome);

        Location s1 = new Location(world, cx - ArenaLayout.SPAWN_X + 0.5, FLOOR_Y + 1, cz + 0.5, -90f, 0f);
        Location s2 = new Location(world, cx + ArenaLayout.SPAWN_X + 0.5, FLOOR_Y + 1, cz + 0.5, 90f, 0f);
        if (job.existing() != null) {
            job.existing().inUse = false;
            plugin.getLogger().info("Rebuilt arena " + job.name() + ".");
        } else {
            plugin.arenas().addGenerated(job.name(), job.theme().id, job.slot(), s1, s2);
            plugin.getLogger().info("Built arena " + job.name() + " (" + job.theme().display + ").");
        }
        active = null;
        px = py = pz = null;
        pd = null;
    }
}
