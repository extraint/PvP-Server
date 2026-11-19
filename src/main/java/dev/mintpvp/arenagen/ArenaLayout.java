package dev.mintpvp.arenagen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** normal arena plan */
public final class ArenaLayout {

    /** ground decorations */
    static final boolean DECORATIONS = false;

    public static final int N = 105, C = 52, SPAWN_X = 40;
    /** play and wall radius */
    public static final double R_PLAY = 50, R_WALL = 52;
    /** plan scale */
    static final double SCALE = R_PLAY / 37.5;
    /** border radius */
    public static final int RADIUS = 50;

    /** plan coordinate */
    static int sc2(int v) {
        return (int) Math.round(v / SCALE);
    }

    /** scaled coordinate */
    static int sc(int v) {
        return (int) Math.round(v * SCALE);
    }

    public static final class Cell {
        /** top-down block */
        public String b;
        /** feature height */
        public int h;
        public String dec, under, floor;
        public boolean wall;

        Cell(String b, int h) {
            this.b = b;
            this.h = h;
            this.floor = b;
        }
    }

    public record Feature(String k, int x, int z, int hw, int hd, int h, String b, String trunk, String leaves, double r) {
        static Feature box(int x, int z, int hw, int hd, int h, String b) {
            return new Feature("box", x, z, hw, hd, h, b, null, null, 0);
        }

        static Feature mound(int x, int z, double r, int h, String b) {
            return new Feature("mound", x, z, 0, 0, h, b, null, null, r);
        }

        static Feature tree(int x, int z, String trunk, String leaves, int r, int h) {
            return new Feature("tree", x, z, 0, 0, h, null, trunk, leaves, r);
        }
    }

    interface Floor { String at(int ax, int az, double d); }

    interface Decor { String at(int ax, int az, String b); }

    public record Spec(Floor floor, Decor decor, String wallBlock, String wallPost, boolean noInlay, List<Feature> features) {}

    private ArenaLayout() {}

    static double hash(int x, int z, int s) {
        int h = x * 374761393 + z * 668265263 + s * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    static double smooth(int x, int z, int s, double scale) {
        double fx = x / scale, fz = z / scale;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        double a = hash(x0, z0, s), b = hash(x0 + 1, z0, s), c = hash(x0, z0 + 1, s), d = hash(x0 + 1, z0 + 1, s);
        double top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    private static boolean ring(double d, double r0, double r1) {
        return d > r0 && d <= r1;
    }

    public static Spec spec(ArenaTheme t) {
        return switch (t) {
            case PLAINS -> new Spec(
                    (ax, az, d) -> ring(d, 17.6, 18.8) ? "path" : (smooth(ax, az, 11, 6) > .72 ? "coarse" : "grass"),
                    (ax, az, b) -> b.equals("grass") ? (hash(ax, az, 12) < .035 ? "dandelion" : hash(ax, az, 13) < .03 ? "poppy" : null) : null,
                    "mossycobble", "oaklog", false, List.of(
                    Feature.tree(14, 16, "oaklog", "oakleaves", 2, 5),
                    Feature.tree(8, 4, "oaklog", "oakleaves", 2, 5)));
            case DESERT -> new Spec(
                    (ax, az, d) -> ring(d, 17.6, 18.8) ? "smooth" : (smooth(ax, az, 21, 7) > .78 ? "smooth" : "sand"),
                    (ax, az, b) -> b.equals("sand") && hash(ax, az, 22) < .025 ? "deadbush" : null,
                    "smooth", "chiseled", false, List.of(
                    Feature.mound(9, 0, 4.5, 2, "dune"),
                    Feature.mound(19, 25, 4.5, 2, "dune")));
            case POPLAR -> new Spec(
                    (ax, az, d) -> {
                        double n = smooth(ax, az, 31, 5);
                        return n > .74 ? "podzol" : n < .22 ? "coarse" : "grass";
                    },
                    (ax, az, b) -> !b.equals("coarse") && hash(ax, az, 32) < .14 ? "leaflitter"
                            : (b.equals("grass") && hash(ax, az, 33) < .03 ? "fern" : null),
                    "strippedpoplar", "poplarlog", false, List.of(
                    Feature.box(9, 0, 0, 2, 1, "fallen"),
                    Feature.tree(8, 10, "poplarlog", "poplaryellow", 1, 8),
                    Feature.tree(17, 6, "poplarlog", "poplarorange", 1, 8),
                    Feature.tree(14, 18, "poplarlog", "poplarred", 1, 8),
                    Feature.tree(24, 13, "poplarlog", "poplarred", 1, 8),
                    Feature.tree(22, 26, "poplarlog", "poplaryellow", 1, 8),
                    Feature.tree(6, 28, "poplarlog", "poplarorange", 1, 8),
                    Feature.tree(29, 20, "poplarlog", "poplaryellow", 1, 8)));
            case SNOWY -> new Spec(
                    (ax, az, d) -> d <= 6.5 ? "ice" : ring(d, 6.5, 7.6) ? "packedice" : "snow",
                    (ax, az, b) -> b.equals("snow") && hash(ax, az, 42) < .05 ? "snowlayer" : null,
                    "packedice", "sprucelog", true, List.of(
                    Feature.box(11, 0, 0, 2, 2, "drift"),
                    Feature.tree(13, 18, "sprucelog", "spruce", 2, 7),
                    Feature.tree(26, 20, "sprucelog", "spruce", 2, 7),
                    Feature.mound(22, 8, 3.2, 2, "drift")));
            case CHERRY -> new Spec(
                    (ax, az, d) -> ring(d, 17.6, 18.8) ? "stonebrick" : "grass",
                    (ax, az, b) -> b.equals("grass") && smooth(ax, az, 52, 4) > .45 && hash(ax, az, 53) < .55 ? "petals" : null,
                    "cherryplanks", "cherrylog", false, List.of(
                    Feature.tree(12, 14, "cherrylog", "cherry", 3, 5),
                    Feature.tree(25, 20, "cherrylog", "cherry", 3, 5),
                    Feature.tree(5, 27, "cherrylog", "cherry", 2, 5)));
            case STADIUM, BADLANDS, JUNGLE, SAVANNA, CRIMSON, PEAKS -> throw new IllegalStateException(t + " has no hand-made plan");
        };
    }

    static boolean inside(int dx, int dz) {
        return Math.hypot(dx, dz) <= R_PLAY;
    }

    static boolean nearSpawn(int dx, int dz) {
        return Math.hypot(Math.abs(dx) - SPAWN_X, dz) < 3.6;
    }

    /** build grid */
    public static Cell[] build(ArenaTheme theme) {
        Spec t = spec(theme);
        Cell[] cells = new Cell[N * N];

        for (int dz = -C; dz <= C; dz++) {
            for (int dx = -C; dx <= C; dx++) {
                double d = Math.hypot(dx, dz);
                if (d > R_WALL) continue;
                Cell cell;
                if (d > R_PLAY) {
                    // invisible barrier edge
                    cell = new Cell(t.floor().at(sc2(Math.abs(dx)), sc2(Math.abs(dz)), d / SCALE), 0);
                    cell.wall = true;
                } else {
                    int ax = Math.abs(dx), az = Math.abs(dz);
                    String b = t.floor().at(sc2(ax), sc2(az), d / SCALE);
                    cell = new Cell(b, 0);
                    String dec = t.decor().at(ax, az, b);
                    if (DECORATIONS && dec != null) cell.dec = dec;
                    if (!t.noInlay() && ax + az <= 3 && (ax + az == 3 || ax + az == 0 || (ax == 1 && az == 1))) {
                        cell = new Cell("inlay", 0);
                    }
                }
                cells[(dz + C) * N + (dx + C)] = cell;
            }
        }

        Set<String> placed = new HashSet<>();
        for (Feature f : t.features()) {
            for (int sx : new int[] {1, -1}) {
                for (int sz : new int[] {1, -1}) {
                    int cx = sx * sc(f.x()), cz = sz * sc(f.z());
                    String key = f.k() + cx + "," + cz + f.b() + f.leaves();
                    if (!placed.add(key)) continue;
                    switch (f.k()) {
                        case "box" -> {
                            for (int x = cx - f.hw(); x <= cx + f.hw(); x++)
                                for (int z = cz - f.hd(); z <= cz + f.hd(); z++) put(cells, x, z, f.b(), f.h(), null, false);
                        }
                        case "mound" -> {
                            int r = (int) Math.ceil(f.r());
                            for (int x = cx - r; x <= cx + r; x++) {
                                for (int z = cz - r; z <= cz + r; z++) {
                                    double dd = Math.hypot(x - cx, z - cz);
                                    if (dd <= f.r()) put(cells, x, z, f.b(), (int) Math.max(1, Math.round(f.h() * (1 - dd / f.r()) + .4)), null, false);
                                }
                            }
                        }
                        case "tree" -> {
                            int r = (int) f.r();
                            for (int x = cx - r; x <= cx + r; x++) {
                                for (int z = cz - r; z <= cz + r; z++) {
                                    if (Math.hypot(x - cx, z - cz) <= f.r() + .35) {
                                        boolean centre = x == cx && z == cz;
                                        put(cells, x, z, f.leaves(), f.h() + 1, centre ? f.trunk() : null, centre);
                                    }
                                }
                            }
                        }
                        default -> { }
                    }
                }
            }
        }

        for (int sx : new int[] {-1, 1}) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Cell c = cells[(z + C) * N + (sx * SPAWN_X + x + C)];
                    c.b = x == 0 && z == 0 ? "spawn" : "pad";
                    c.floor = c.b;
                    c.h = 0;
                    c.dec = null;
                    c.under = null;
                }
            }
        }
        return cells;
    }

    private static void put(Cell[] cells, int dx, int dz, String b, int h, String under, boolean setUnder) {
        if (!inside(dx, dz) || nearSpawn(dx, dz)) return;
        Cell c = cells[(dz + C) * N + (dx + C)];
        if (h >= c.h) {
            c.b = b;
            c.h = h;
            c.dec = null;
            if (setUnder) c.under = under;
        }
    }
}
