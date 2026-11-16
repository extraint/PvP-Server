package dev.mintpvp.arenagen;

import dev.mintpvp.arenagen.ArenaLayout.Cell;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * Everything the world generator needs to know about one arena: its surface, the top block of
 * each column, and the cover pieces standing on it. Built once per arena and shared.
 *
 * Small arenas (unranked) use the hand-designed 75-block layout. Large arenas (ranked,
 * radius 100-150) use the same themes with rolling terrain and cover scattered on a jittered
 * grid; both are mirror-symmetric so the two sides are always equal.
 */
final class ArenaModel {

    static final int BASE_Y = 64;
    /** Feature blocks that are plain columns straight from the small layout plan. */
    private static final Set<String> COLUMNS = Set.of("hay", "cobble", "cut", "pillar", "dune", "fallen", "drift", "spike", "gardenwall");

    final ArenaTheme theme;
    final boolean large;
    /** Playable radius and the outer edge of the invisible barrier ring. */
    final double rPlay, rWall;
    final int spawnX;
    /** Highest block the barrier ring reaches. */
    final int barrierTop;

    private final Cell[] cells; // small arenas only
    private final Map<Long, BlockData> overlay;

    ArenaModel(ArenaTheme theme, boolean large, int radius) {
        this.theme = theme;
        this.large = large;
        if (large) {
            this.rPlay = radius - 1.5;
            this.rWall = radius + 0.5;
            this.spawnX = (int) Math.round(radius * 0.8);
            this.barrierTop = BASE_Y + 70;
            this.cells = null;
            this.overlay = buildLargeOverlay(radius);
        } else {
            this.rPlay = ArenaLayout.R_PLAY;
            this.rWall = ArenaLayout.R_WALL;
            this.spawnX = ArenaLayout.SPAWN_X;
            this.barrierTop = BASE_Y + ArenaGenerator.CEILING;
            this.cells = ArenaLayout.build(theme);
            this.overlay = buildSmallOverlay();
        }
    }

    // ---- queries used by the chunk generator -----------------------------

    /** Is this column part of the arena (playable floor or edge ring)? */
    boolean inside(int dx, int dz) {
        return Math.hypot(dx, dz) <= rWall;
    }

    boolean ring(int dx, int dz) {
        return Math.hypot(dx, dz) > rPlay;
    }

    int maxOverlayHeight() {
        return 24;
    }

    BlockData overlayAt(int dx, int y, int dz) {
        return overlay.get(Pack.key(dx, y, dz));
    }

    /** Distance from this column to the nearest spawn pad. */
    double spawnDistance(int dx, int dz) {
        return Math.hypot(Math.abs(dx) - spawnX, dz);
    }

    /** Ground height (the top block's y). */
    int surface(int dx, int dz) {
        if (!large) return BASE_Y;
        int ax = Math.abs(dx), az = Math.abs(dz);
        double amp = switch (theme) {
            case PLAINS -> 4.5;
            case DESERT -> 7.0;
            case POPLAR -> 4.0;
            case SNOWY -> 6.0;
            case CHERRY -> 5.0;
        };
        double n = (ArenaLayout.smooth(ax, az, 101, 38) - .5) * 2 * amp + (ArenaLayout.smooth(ax, az, 102, 15) - .5) * amp * 0.7;
        double flatSpawn = clamp((Math.hypot(ax - spawnX, az) - 7) / 12.0);
        double flatCentre = clamp((Math.hypot(ax, az) - 5) / 12.0);
        double flatEdge = 1 - clamp((Math.hypot(ax, az) - (rPlay - 10)) / 8.0); // ease down to flat at the rim
        n *= flatSpawn * flatCentre * flatEdge;
        return BASE_Y + (int) Math.round(n);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** The block on top of the ground at this column. */
    BlockData top(int dx, int dz) {
        return Blocks.data(floorKey(dx, dz));
    }

    String floorKey(int dx, int dz) {
        if (!large) return cells[(dz + ArenaLayout.C) * ArenaLayout.N + (dx + ArenaLayout.C)].floor;
        int ax = Math.abs(dx), az = Math.abs(dz);
        double d = Math.hypot(dx, dz);
        double ds = Math.hypot(ax - spawnX, az);
        if (ds < 1.5 && Math.abs(az) <= 1 && Math.abs(ax - spawnX) <= 1) return az == 0 && ax == spawnX ? "spawn" : "pad";
        if (ax + az <= 3 && (ax + az == 3 || ax + az == 0 || (ax == 1 && az == 1)) && theme != ArenaTheme.SNOWY) return "inlay";
        double ringR = rPlay * 0.5;
        boolean onRing = d > ringR && d <= ringR + 1.3;
        return switch (theme) {
            case PLAINS -> onRing ? "path" : (ArenaLayout.smooth(ax, az, 11, 9) > .72 ? "coarse" : "grass");
            case DESERT -> ArenaLayout.smooth(ax, az, 21, 10) > .76 ? "smooth" : "sand";
            case POPLAR -> {
                double n = ArenaLayout.smooth(ax, az, 31, 8);
                yield n > .74 ? "podzol" : n < .22 ? "coarse" : "grass";
            }
            case SNOWY -> ArenaLayout.smooth(ax, az, 41, 12) > .86 ? "ice" : "snow";
            case CHERRY -> onRing ? "stonebrick" : "grass";
        };
    }

    /** Material of the 3 layers under the top block. */
    BlockData sub() {
        return Blocks.data(theme.sub);
    }

    /** A column-type cover piece from the small plan at this height above the ground, or null. */
    BlockData featureAt(int dx, int dz, int rel) {
        if (large || rel < 1) return null;
        Cell c = cells[(dz + ArenaLayout.C) * ArenaLayout.N + (dx + ArenaLayout.C)];
        if (c == null || c.h < rel || !COLUMNS.contains(c.b)) return null;
        if (c.b.equals("fallen")) return Blocks.log("fallen", org.bukkit.Axis.Z);
        if (c.b.equals("pillar") && rel == c.h) return Blocks.data(Material.CHISELED_SANDSTONE);
        return Blocks.data(c.b);
    }

    // ---- cover: small arenas ---------------------------------------------

    private Map<Long, BlockData> buildSmallOverlay() {
        Map<Long, BlockData> out = new HashMap<>();
        BiPredicate<Integer, Integer> allow = (x, z) -> ArenaLayout.inside(x, z) && !ArenaLayout.nearSpawn(x, z);
        IntBinaryOperator flat = (x, z) -> BASE_Y;
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (ArenaLayout.Feature f : ArenaLayout.spec(theme).features()) {
            if (!f.k().equals("tree")) continue;
            for (int sx : new int[] {1, -1}) {
                for (int sz : new int[] {1, -1}) {
                    int tx = sx * f.x(), tz = sz * f.z();
                    if (!placed.add(tx + "," + tz + f.leaves())) continue;
                    Shapes.tree(out, allow, flat, f.trunk(), f.leaves(), (int) f.r(), f.h(), tx, tz);
                }
            }
        }
        return out;
    }

    // ---- cover: large arenas ---------------------------------------------

    private Map<Long, BlockData> buildLargeOverlay(int radius) {
        Map<Long, BlockData> out = new HashMap<>();
        BiPredicate<Integer, Integer> allow = (x, z) -> Math.hypot(x, z) <= rPlay - 1 && spawnDistance(x, z) > 5;
        IntBinaryOperator surf = this::surface;
        int cell = 13, seed = 500 + theme.ordinal() * 17;
        double density = switch (theme) {
            case PLAINS -> .30;
            case DESERT -> .26;
            case POPLAR -> .55;
            case SNOWY -> .30;
            case CHERRY -> .30;
        };
        int max = radius / cell + 1;
        for (int gx = 0; gx <= max; gx++) {
            for (int gz = 0; gz <= max; gz++) {
                if (ArenaLayout.hash(gx, gz, seed) > density) continue;
                int fx = gx * cell + (int) (ArenaLayout.hash(gx, gz, seed + 1) * cell);
                int fz = gz * cell + (int) (ArenaLayout.hash(gx, gz, seed + 2) * cell);
                if (fx < 5 || fz < 5) continue; // keep lanes along both axes open
                double d = Math.hypot(fx, fz);
                if (d > rPlay - 6 || d < 8 || Math.hypot(fx - spawnX, fz) < 14) continue;
                double t = ArenaLayout.hash(gx, gz, seed + 3), u = ArenaLayout.hash(gx, gz, seed + 4);
                for (int sx : new int[] {1, -1}) {
                    for (int sz : new int[] {1, -1}) place(out, allow, surf, sx * fx, sz * fz, t, u);
                }
            }
        }
        return out;
    }

    private void place(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                       int x, int z, double t, double u) {
        boolean alongX = u > .5;
        switch (theme) {
            case PLAINS -> {
                if (t < .7) Shapes.tree(out, allow, surf, "oaklog", "oakleaves", 2, 4 + (int) (u * 3), x, z);
                else Shapes.box(out, allow, surf, x, z, (int) (u * 2), (int) ((1 - u) * 2), 1 + (int) (u * 2), Blocks.data("hay"));
            }
            case DESERT -> {
                Shapes.mound(out, allow, surf, x, z, 3.5 + u * 2, 2 + (int) (u * 2), Blocks.data("dune"));
            }
            case POPLAR -> {
                if (t < .82) {
                    String[] leaves = {"poplaryellow", "poplarorange", "poplarred"};
                    Shapes.tree(out, allow, surf, "poplarlog", leaves[(int) (u * 3) % 3], 1, 7 + (int) (u * 4), x, z);
                } else {
                    Shapes.fallenLog(out, allow, surf, x, z, 5, alongX, "fallen");
                }
            }
            case SNOWY -> {
                if (t < .6) Shapes.tree(out, allow, surf, "sprucelog", "spruce", 2, 6 + (int) (u * 3), x, z);
                else Shapes.mound(out, allow, surf, x, z, 3 + u * 2, 2, Blocks.data("drift"));
            }
            case CHERRY -> {
                Shapes.tree(out, allow, surf, "cherrylog", "cherry", 2 + (int) (u * 2), 4 + (int) (u * 2), x, z);
            }
        }
    }
}
