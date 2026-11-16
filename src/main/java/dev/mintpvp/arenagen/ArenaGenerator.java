package dev.mintpvp.arenagen;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.ArenaManager.Arena;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the arena world. Arenas are slots on a grid in a custom-generated world (terrain is made
 * as chunks load, so there is nothing to "build"): the pool is topped up on start, more are added
 * on demand, and after every duel a generated arena is reset by dropping its chunks unsaved so they
 * regenerate fresh.
 */
public final class ArenaGenerator {

    public static final int SPACING = 500, COLS = 10;
    public static final int FLOOR_Y = ArenaModel.BASE_Y;
    /** Height of the invisible barrier above small arenas' edge. */
    public static final int CEILING = 40;
    /** Where the lobby island is, west of the arena grid. */
    public static final int LOBBY_X = -2400, LOBBY_Z = 0;

    /** One arena position on the grid. */
    public record Slot(int index, ArenaTheme theme, boolean large, int radius) {
        public int cx() { return (index % COLS) * SPACING; }

        public int cz() { return (index / COLS) * SPACING; }
    }

    private final MintPvP plugin;
    private final Map<Integer, Slot> slots = new ConcurrentHashMap<>();
    private final Map<Integer, ArenaModel> models = new ConcurrentHashMap<>();
    private volatile Map<Long, Map<Long, BlockData>> lobbyBuckets = Map.of();
    private final Random rnd = new Random();
    private World world;
    private ArenaWorldGenerator worldGen;

    public ArenaGenerator(MintPvP plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("arena-generator.enabled", true);
    }

    public World world() {
        return world;
    }

    // ---- lookups used by the chunk generator (thread-safe) ---------------

    Slot slotAt(int x, int z) {
        int col = Math.floorDiv(x + SPACING / 2, SPACING), row = Math.floorDiv(z + SPACING / 2, SPACING);
        if (col < 0 || col >= COLS || row < 0) return null;
        return slots.get(row * COLS + col);
    }

    ArenaModel model(Slot s) {
        return models.computeIfAbsent(s.index(), i -> new ArenaModel(s.theme(), s.large(), s.radius()));
    }

    Map<Long, BlockData> lobbyChunk(int cx, int cz) {
        return lobbyBuckets.get(chunkKey(cx, cz));
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Bump this whenever the arena or lobby design changes, so terrain cached in the world is thrown away once. */
    static final int DESIGN_VERSION = 3;

    /**
     * Chunks of this world can be saved when they unload, so after a design change old chunks would keep showing
     * the old arenas. When the design version changes, delete the cached chunk files (before the world loads);
     * everything is regenerated from the generator.
     */
    private void dropStaleTerrain(String worldName) {
        java.nio.file.Path marker = plugin.getDataFolder().toPath().resolve("arena-design.version");
        try {
            String have = java.nio.file.Files.exists(marker) ? java.nio.file.Files.readString(marker).trim() : "";
            if (have.equals(String.valueOf(DESIGN_VERSION))) return;
            java.nio.file.Path base = Bukkit.getWorldContainer().toPath().resolve("world/dimensions/minecraft/" + worldName);
            int removed = 0;
            for (String dir : new String[] {"region", "entities", "poi"}) {
                java.nio.file.Path d = base.resolve(dir);
                if (!java.nio.file.Files.isDirectory(d)) continue;
                try (var files = java.nio.file.Files.list(d)) {
                    for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
                        if (java.nio.file.Files.isRegularFile(f) && f.getFileName().toString().endsWith(".mca")) {
                            java.nio.file.Files.delete(f);
                            removed++;
                        }
                    }
                }
            }
            plugin.getDataFolder().mkdirs();
            java.nio.file.Files.writeString(marker, String.valueOf(DESIGN_VERSION));
            if (removed > 0) plugin.getLogger().info("Arena design changed: cleared " + removed + " cached chunk files so the new design is generated.");
        } catch (java.io.IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not clear cached arena chunks: " + e.getMessage());
        }
    }

    // ---- lifecycle ------------------------------------------------------

    public void start() {
        if (!enabled()) return;
        String name = plugin.getConfig().getString("arena-generator.world-name", "mint_arenas_2");
        dropStaleTerrain(name);

        // arenas from an older generator (other world) can't be used any more: forget them
        int dropped = plugin.arenas().removeGeneratedOutside(name);
        if (dropped > 0) {
            plugin.getLogger().info("Removed " + dropped + " generated arenas from the old arena world; new ones are being made in '" + name + "'. You can delete the old world folder.");
        }
        for (Arena a : plugin.arenas().all()) {
            ArenaTheme t = ArenaTheme.byId(a.theme);
            if (a.theme == null || t == null || a.slot < 0) continue;
            register(new Slot(a.slot, t, a.large, a.radius > 0 ? a.radius : 38));
            a.pending = false;
        }
        if (lobbyEnabled()) registerLobby();

        try {
            worldGen = new ArenaWorldGenerator(this);
            world = new WorldCreator(name).generator(worldGen).environment(World.Environment.NORMAL)
                    .generateStructures(false).createWorld();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Could not create the arena world '" + name + "': " + e.getMessage());
            return;
        }
        if (world == null) {
            plugin.getLogger().severe("Could not create the arena world '" + name + "'.");
            return;
        }
        // The terrain is a pure function of the generator, so it never needs saving. Not saving is also
        // what lets a reset (dropping the chunks) bring an arena back to its original state.
        world.setAutoSave(false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.LOCATOR_BAR, true); // players can see each other on the locator bar
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);

        if (lobbyEnabled()) {
            Location current = plugin.spawn().isSet() ? plugin.spawn().get() : null;
            Location lobby = lobbySpawn();
            // no spawn yet, or the spawn is still in an older generated lobby: use the current lobby
            boolean oldLobby = current != null && world.equals(current.getWorld()) && current.getX() < -300 && current.distance(lobby) > 100;
            if (current == null || oldLobby) {
                plugin.spawn().set(lobby);
                plugin.getLogger().info("Lobby generated and set as the server spawn.");
            }
        }
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) world.getChunkAtAsync((LOBBY_X >> 4) + dx, (LOBBY_Z >> 4) + dz);
        fillPool();
    }

    public void stop() {
        // nothing to clean up: the world is never saved
    }

    private boolean lobbyEnabled() {
        return plugin.getConfig().getBoolean("arena-generator.lobby", true);
    }

    /** Standing height (ground + 1) at a world x/z inside a generated arena, or -1 for other arenas. */
    public int standY(Arena a, int x, int z) {
        Slot s = a.slot < 0 ? null : slots.get(a.slot);
        if (s == null) return -1;
        return model(s).surface(x - s.cx(), z - s.cz()) + 1;
    }

    /** Is this location in the lobby area of the arena world? */
    public boolean inLobbyRegion(Location l) {
        return world != null && l.getWorld() != null && l.getWorld().equals(world)
                && Math.abs(l.getX() - LOBBY_X) <= 90 && Math.abs(l.getZ() - LOBBY_Z) <= 90;
    }

    public Location lobbySpawn() {
        // just south of the fountain, facing north (towards the fountain and the sign)
        return world == null ? null : new Location(world, LOBBY_X + 0.5, FLOOR_Y + 1, LOBBY_Z + LobbyDesign.SPAWN_DZ + 0.5, 180f, 0f);
    }

    private void registerLobby() {
        Map<Long, BlockData> out = new HashMap<>(120_000);
        LobbyDesign.place(out, Blocks::data, LOBBY_X, LOBBY_Z, FLOOR_Y);
        Map<Long, Map<Long, BlockData>> buckets = new HashMap<>();
        for (var e : out.entrySet()) {
            if (e.getValue().getMaterial().isAir()) continue; // the void needs no clearing
            long k = e.getKey();
            buckets.computeIfAbsent(chunkKey(Pack.x(k) >> 4, Pack.z(k) >> 4), kk -> new HashMap<>()).put(k, e.getValue());
        }
        lobbyBuckets = buckets;
    }

    // ---- pool -----------------------------------------------------------

    private void register(Slot s) {
        slots.put(s.index(), s);
        models.remove(s.index());
    }

    public void forget(Arena a) {
        if (a.slot >= 0) {
            slots.remove(a.slot);
            models.remove(a.slot);
        }
    }

    private int perTheme() { return Math.max(0, plugin.getConfig().getInt("arena-generator.per-theme", 2)); }

    private int rankedPerTheme() { return Math.max(0, plugin.getConfig().getInt("arena-generator.ranked-per-theme", 1)); }

    private int maxTotal() { return Math.max(1, plugin.getConfig().getInt("arena-generator.max-total", 30)); }

    public int total() {
        return (int) plugin.arenas().all().stream().filter(a -> a.theme != null).count();
    }

    public boolean building() {
        return plugin.arenas().all().stream().anyMatch(a -> a.pending);
    }

    public String status() {
        long n = plugin.arenas().all().stream().filter(a -> a.pending).count();
        return n == 0 ? "idle" : n + " generating";
    }

    private void fillPool() {
        int made = 0;
        for (ArenaTheme t : ArenaTheme.values()) {
            for (boolean large : new boolean[] {false, true}) {
                long have = plugin.arenas().all().stream().filter(a -> t.id.equals(a.theme) && a.large == large).count();
                int want = large ? rankedPerTheme() : perTheme();
                for (long i = have; i < want && total() < maxTotal(); i++) {
                    create(t, large);
                    made++;
                }
            }
        }
        if (made > 0) plugin.getLogger().info("Generating " + made + " pre-made arenas...");
    }

    private String nextName(ArenaTheme t, boolean large) {
        Set<String> taken = new HashSet<>();
        plugin.arenas().all().forEach(a -> taken.add(a.name.toLowerCase(Locale.ROOT)));
        String prefix = (large ? "ranked-" : "") + t.id + "-";
        for (int i = 1; ; i++) if (!taken.contains(prefix + i)) return prefix + i;
    }

    private Arena create(ArenaTheme theme, boolean large) {
        int min = Math.max(40, plugin.getConfig().getInt("arena-generator.ranked-radius-min", 100));
        int max = Math.max(min, plugin.getConfig().getInt("arena-generator.ranked-radius-max", 150));
        int radius = large ? min + rnd.nextInt(max - min + 1) : 38;
        Slot slot = new Slot(plugin.arenas().nextSlot(), theme, large, radius);
        register(slot);
        int sp = model(slot).spawnX;
        Location s1 = new Location(world, slot.cx() - sp + 0.5, FLOOR_Y + 1, slot.cz() + 0.5, -90f, 0f);
        Location s2 = new Location(world, slot.cx() + sp + 0.5, FLOOR_Y + 1, slot.cz() + 0.5, 90f, 0f);
        Arena a = plugin.arenas().addGenerated(nextName(theme, large), theme.id, slot.index(), s1, s2, large, radius);
        warm(a, slot, sp);
        return a;
    }

    /** Generates the chunks around the spawns and centre in the background, then marks the arena ready. */
    private void warm(Arena a, Slot slot, int spawnX) {
        Set<Long> chunks = new HashSet<>();
        int[][] spots = {{slot.cx() - spawnX, slot.cz()}, {slot.cx() + spawnX, slot.cz()}, {slot.cx(), slot.cz()}};
        for (int[] p : spots) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) chunks.add(chunkKey((p[0] >> 4) + dx, (p[1] >> 4) + dz));
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (long k : chunks) futures.add(world.getChunkAtAsync((int) (k >> 32), (int) k));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    a.pending = false;
                    if (err != null) plugin.getLogger().warning("Arena " + a.name + " generated with a warning: " + err.getMessage());
                    else plugin.getLogger().info("Arena " + a.name + " is ready.");
                }));
    }

    /** Queues new arenas (admin command). @return how many were made. */
    public int generate(ArenaTheme theme, int amount, boolean large) {
        if (world == null) return 0;
        int made = 0;
        for (int i = 0; i < amount && total() < maxTotal(); i++) {
            create(theme != null ? theme : ArenaTheme.values()[rnd.nextInt(ArenaTheme.values().length)], large);
            made++;
        }
        return made;
    }

    /** Makes one more arena when all are busy. @return true if one exists or is on its way. */
    public boolean requestExtra(boolean large) {
        if (!enabled() || world == null) return false;
        if (building()) return true;
        if (total() >= maxTotal()) return false;
        create(ArenaTheme.values()[rnd.nextInt(ArenaTheme.values().length)], large);
        return true;
    }

    // ---- reset ----------------------------------------------------------

    public boolean resets(Arena a) {
        return world != null && a.theme != null && a.slot >= 0 && a.center() != null && world.equals(a.center().getWorld());
    }

    private record Fix(int x, int y, int z, BlockData data) {}

    /**
     * Puts a generated arena back exactly as it was. Every loaded chunk of the arena is compared with
     * what the generator produces for it (in the background), and each block that differs is put back
     * (a few thousand per tick). This undoes anything: placed or broken blocks, fire, liquids, falling
     * blocks, explosions. {@code done} runs when everything is back.
     */
    public void reset(Arena a, Runnable done) {
        if (!resets(a) || !plugin.isEnabled()) {
            done.run();
            return;
        }
        var c = a.center();
        int r = a.radius() + 4;
        int x0 = (c.getBlockX() - r) >> 4, x1 = (c.getBlockX() + r) >> 4, z0 = (c.getBlockZ() - r) >> 4, z1 = (c.getBlockZ() + r) >> 4;

        // loose items, arrows and anything else that isn't a player
        world.getNearbyEntities(a.box(), e -> !(e instanceof org.bukkit.entity.Player)).forEach(org.bukkit.entity.Entity::remove);

        List<org.bukkit.ChunkSnapshot> snaps = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (world.isChunkLoaded(x, z)) snaps.add(world.getChunkAt(x, z).getChunkSnapshot());
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Fix> fixes = new ArrayList<>();
            try {
                for (org.bukkit.ChunkSnapshot snap : snaps) diff(snap, fixes);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Arena reset scan failed for " + a.name, e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> applyFixes(a, fixes, 0, done));
        });
    }

    /** Resets an arena right now, on the calling (main) thread. Used on shutdown, when tasks can't be scheduled. */
    public void resetSync(Arena a) {
        if (!resets(a)) return;
        var c = a.center();
        int r = a.radius() + 4;
        int x0 = (c.getBlockX() - r) >> 4, x1 = (c.getBlockX() + r) >> 4, z0 = (c.getBlockZ() - r) >> 4, z1 = (c.getBlockZ() + r) >> 4;
        List<Fix> fixes = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (world.isChunkLoaded(x, z)) diff(world.getChunkAt(x, z).getChunkSnapshot(), fixes);
            }
        }
        for (Fix f : fixes) world.getBlockAt(f.x(), f.y(), f.z()).setBlockData(f.data(), false);
    }

    private void diff(org.bukkit.ChunkSnapshot snap, List<Fix> out) {
        var expected = Bukkit.createChunkData(world);
        worldGen.generateNoise(null, null, snap.getX(), snap.getZ(), expected);
        int bx = snap.getX() << 4, bz = snap.getZ() << 4;
        int minY = world.getMinHeight(), maxY = world.getMaxHeight();
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    var have = snap.getBlockType(x, y, z);
                    var want = expected.getType(x, y, z);
                    if (have == want || (have.isAir() && want.isAir())) continue;
                    out.add(new Fix(bx + x, y, bz + z, expected.getBlockData(x, y, z)));
                }
            }
        }
    }

    private void applyFixes(Arena a, List<Fix> fixes, int from, Runnable done) {
        if (!plugin.isEnabled()) {
            done.run();
            return;
        }
        int to = Math.min(fixes.size(), from + 15000);
        for (int i = from; i < to; i++) {
            Fix f = fixes.get(i);
            world.getBlockAt(f.x(), f.y(), f.z()).setBlockData(f.data(), false);
        }
        if (to >= fixes.size()) {
            if (!fixes.isEmpty()) plugin.getLogger().info("Reset arena " + a.name + " (" + fixes.size() + " blocks put back).");
            done.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> applyFixes(a, fixes, to, done));
        }
    }
}
