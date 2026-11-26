package dev.mintpvp.arenagen;

import dev.mintpvp.arenagen.ArenaLayout.Cell;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/** arena shape data */
final class ArenaModel {

    static final int BASE_Y = 64;
    /** column features */
    private static final Set<String> COLUMNS = Set.of("hay", "cobble", "cut", "pillar", "dune", "fallen", "drift", "spike", "gardenwall");

    final ArenaTheme theme;
    final boolean large;
    /** play and wall radius */
    final double rPlay, rWall;
    final int spawnX;
    /** barrier top */
    final int barrierTop;

    /** makes every arena copy different */
    final int seed;
    private final Cell[] cells;
    private final Map<Long, BlockData> overlay;

    ArenaModel(ArenaTheme theme, boolean large, int radius, int seed) {
        this.theme = theme;
        this.seed = seed;
        this.large = large;
        if (large) {
            this.rPlay = radius - 1.5;
            this.rWall = radius + 0.5;
            this.spawnX = (int) Math.round(radius * 0.8);
            this.barrierTop = BASE_Y + 70;
            this.cells = null;
            this.overlay = buildLargeOverlay(radius);
        } else if (theme == ArenaTheme.STADIUM) {
            this.rPlay = ArenaLayout.R_PLAY;
            this.rWall = ArenaLayout.R_WALL;
            this.spawnX = ArenaLayout.SPAWN_X;
            this.barrierTop = BASE_Y + ArenaGenerator.CEILING;
            this.cells = null;
            this.overlay = buildStadium();
        } else {
            this.rPlay = ArenaLayout.R_PLAY;
            this.rWall = ArenaLayout.R_WALL;
            this.spawnX = ArenaLayout.SPAWN_X;
            this.barrierTop = BASE_Y + ArenaGenerator.CEILING;
            this.cells = ArenaLayout.build(theme);
            this.overlay = buildSmallOverlay();
        }
    }

    /** inside arena? */
    boolean inside(int dx, int dz) {
        return Math.hypot(dx, dz) <= (theme == ArenaTheme.STADIUM ? STADIUM_OUTER : rWall);
    }

    boolean ring(int dx, int dz) {
        double d = Math.hypot(dx, dz);
        return d > rPlay && d <= rWall;
    }

    int maxOverlayHeight() {
        return 40;
    }

    BlockData overlayAt(int dx, int y, int dz) {
        return overlay.get(Pack.key(dx, y, dz));
    }

    /** smooth noise at a real */
    private double sm(int x, int z, int s, double scale) {
        return ArenaLayout.smooth(x + seed * 53, z + seed * 71, s + seed, scale);
    }

    /** spawn distance */
    double spawnDistance(int dx, int dz) {
        return Math.hypot(Math.abs(dx) - spawnX, dz);
    }

    /** ground height */
    int surface(int dx, int dz) {
        if (isPond(dx, dz)) return BASE_Y - 1;
        return rawSurface(dx, dz);
    }

    private int rawSurface(int dx, int dz) {
        if (!large) return BASE_Y;
        int ax = Math.abs(dx), az = Math.abs(dz);
        double amp = switch (theme) {
            case PLAINS -> 4.5;
            case DESERT -> 7.0;
            case POPLAR -> 4.0;
            case SNOWY -> 6.0;
            case CHERRY -> 5.0;
            case STADIUM -> 0.0;
            case BADLANDS -> 8.0;
            case JUNGLE -> 5.0;
            case SAVANNA -> 5.0;
            case CRIMSON -> 6.0;
            case PEAKS -> 10.0;
        };
        double n = (sm(dx, dz, 101, 38) - .5) * 2 * amp + (sm(dx, dz, 102, 15) - .5) * amp * 0.7;
        double flatSpawn = clamp((Math.hypot(ax - spawnX, az) - 7) / 12.0);
        double flatCentre = clamp((Math.hypot(ax, az) - 5) / 12.0);
        double flatEdge = 1 - clamp((Math.hypot(ax, az) - (rPlay - 10)) / 8.0);
        n *= flatSpawn * flatCentre * flatEdge;
        return BASE_Y + (int) Math.round(n);
    }

    /** a frozen pond */
    boolean isPond(int dx, int dz) {
        if (theme != ArenaTheme.SNOWY || !large) return false;
        double d = Math.hypot(dx, dz);
        if (d < 14 || d > rPlay - 10 || spawnDistance(dx, dz) < 16) return false;
        if (sm(dx, dz, 230, 12) < 0.7) return false;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int h = rawSurface(dx + ox, dz + oz);
                if (h < BASE_Y - 2 || h > BASE_Y + 2) return false; // not lower than the water
            }
        }
        return true;
    }

    /** within two blocks of a */
    boolean nearPond(int dx, int dz) {
        if (theme != ArenaTheme.SNOWY || !large || sm(dx, dz, 230, 12) < 0.55) return false;
        for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) if (isPond(dx + ox, dz + oz)) return true;
        return false;
    }

    /** blocks of water under the */
    int waterDepth(int dx, int dz) {
        return isPond(dx, dz) ? 2 : 0;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** top block */
    BlockData top(int dx, int dz) {
        return Blocks.data(floorKey(dx, dz));
    }

    String floorKey(int dx, int dz) {
        if (theme == ArenaTheme.STADIUM) return stadiumFloor(dx, dz);
        if (!large) return cells[(dz + ArenaLayout.C) * ArenaLayout.N + (dx + ArenaLayout.C)].floor;
        int ax = Math.abs(dx), az = Math.abs(dz);
        double d = Math.hypot(dx, dz);
        double ds = Math.hypot(ax - spawnX, az);
        if (ds < 1.5 && Math.abs(az) <= 1 && Math.abs(ax - spawnX) <= 1) return az == 0 && ax == spawnX ? "spawn" : "pad";
        if (ax + az <= 3 && (ax + az == 3 || ax + az == 0 || (ax == 1 && az == 1)) && theme != ArenaTheme.SNOWY) return "inlay";
        double ringR = rPlay * 0.5;
        boolean onRing = d > ringR && d <= ringR + 1.3;
        return switch (theme) {
            case PLAINS -> onRing ? "path" : (sm(dx, dz, 11, 9) > .72 ? "coarse" : "grass");
            case DESERT -> sm(dx, dz, 21, 10) > .76 ? "smooth" : "sand";
            case POPLAR -> {
                double n = sm(dx, dz, 31, 8);
                yield n > .74 ? "podzol" : n < .22 ? "coarse" : "grass";
            }
            case SNOWY -> {
                if (isPond(dx, dz)) yield "ice";
                if (sm(dx, dz, 230, 12) > 0.62) { // a gravel shore around the
                    for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) if (isPond(dx + ox, dz + oz)) yield "gravel";
                }
                double n = sm(dx, dz, 41, 10);
                yield n > .82 ? "gravel" : n < .16 ? "snowpodzol" : "snowgrass";
            }
            case CHERRY -> onRing ? "stonebrick" : "grass";
            case STADIUM -> "mud";
            case BADLANDS -> sm(dx, dz, 61, 10) > .7 ? "orangeterra" : sm(dx, dz, 62, 7) > .74 ? "yellowterra" : "redsand";
            case JUNGLE -> {
                double n = sm(dx, dz, 71, 8);
                yield n > .72 ? "podzol" : n < .25 ? "moss" : "grass";
            }
            case SAVANNA -> sm(dx, dz, 81, 10) > .6 ? "coarse" : "grass";
            case CRIMSON -> sm(dx, dz, 91, 9) > .7 ? "netherrack" : "nylium";
            case PEAKS -> {
                int h = surface(dx, dz) - BASE_Y;
                yield h >= 5 ? "snow" : h >= 2 ? "stone" : sm(dx, dz, 111, 9) > .5 ? "gravel" : "grass";
            }
        };
    }

    /** subsurface block */
    BlockData sub() {
        return Blocks.data(theme.sub);
    }

    /** feature block */
    BlockData featureAt(int dx, int dz, int rel) {
        if (large || cells == null || rel < 1) return null;
        Cell c = cells[(dz + ArenaLayout.C) * ArenaLayout.N + (dx + ArenaLayout.C)];
        if (c == null || c.h < rel || !COLUMNS.contains(c.b)) return null;
        if (c.b.equals("fallen")) return Blocks.log("fallen", org.bukkit.Axis.Z);
        if (c.b.equals("pillar") && rel == c.h) return Blocks.data(Material.CHISELED_SANDSTONE);
        return Blocks.data(c.b);
    }

    /** outer radius of the stands */
    static final double STADIUM_OUTER = 74;

    /** flat mud floor with a */
    private String stadiumFloor(int dx, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        double d = Math.hypot(dx, dz);
        double ds = Math.hypot(ax - spawnX, az);
        if (ds < 1.5 && az <= 1 && Math.abs(ax - spawnX) <= 1) return az == 0 && ax == spawnX ? "spawn" : "pad";
        if (d <= 3) return "packedmud";
        if (d <= 5) return "mudbricks";
        if (d <= 7) return "mud";
        if (d <= 9) return "packedmud";
        double step = Math.PI / 4;
        double diff = Math.abs(((Math.atan2(dz, dx) % step) + step) % step);
        if (d <= 47 && d * Math.sin(Math.min(diff, step - diff)) < 0.75) return "packedmud";
        if ((d > 22 && d <= 23.5) || (d > 36 && d <= 37.5)) return "mudbricks";
        if (d > 46 && d <= 47.5) return "packedmud";
        if (d > 47.5) return "mudbricks";
        return "mud";
    }

    /** seating tiers */
    private Map<Long, BlockData> buildStadium() {
        Map<Long, BlockData> out = new HashMap<>();
        BlockData fill = Blocks.data(Material.STONE_BRICKS);
        BlockData seatA = Blocks.data(Material.ORANGE_TERRACOTTA), seatB = Blocks.data(Material.BROWN_TERRACOTTA);
        BlockData aisle = Blocks.data(Material.POLISHED_ANDESITE);
        BlockData wall = Blocks.data(Material.DEEPSLATE_BRICKS);
        int r = (int) Math.ceil(STADIUM_OUTER);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= rWall || d > STADIUM_OUTER) continue;
                int h;
                BlockData top, body = fill;
                if (d > 70) {
                    h = 14;
                    top = wall;
                    body = wall;
                } else {
                    h = 3 + (int) ((d - rWall) / 2);
                    double ang = Math.toDegrees(Math.atan2(dz, dx));
                    if (ang < 0) ang += 360;
                    double m = (((ang - 22.5) % 45) + 45) % 45;
                    boolean isAisle = d * Math.toRadians(Math.min(m, 45 - m)) < 0.8;
                    top = isAisle ? aisle : ((int) (ang / 7.5) / 3) % 2 == 0 ? seatA : seatB;
                }
                for (int rel = 1; rel <= h; rel++) out.put(Pack.key(dx, BASE_Y + rel, dz), rel == h ? top : body);
            }
        }
        BlockData pole = Blocks.data(Material.POLISHED_BLACKSTONE_BRICKS), lamp = Blocks.data(Material.SEA_LANTERN);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 + 15);
            int px = (int) Math.round(Math.cos(a) * 71), pz = (int) Math.round(Math.sin(a) * 71);
            for (int rel = 15; rel <= 26; rel++) out.put(Pack.key(px, BASE_Y + rel, pz), pole);
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) out.put(Pack.key(px + ox, BASE_Y + 27, pz + oz), lamp);
        }
        return out;
    }

    private Map<Long, BlockData> buildSmallOverlay() {
        Map<Long, BlockData> out = new HashMap<>();
        BiPredicate<Integer, Integer> allow = (x, z) -> ArenaLayout.inside(x, z) && !ArenaLayout.nearSpawn(x, z);
        IntBinaryOperator flat = (x, z) -> BASE_Y;
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (ArenaLayout.Feature f : ArenaLayout.spec(theme).features()) {
            if (!f.k().equals("tree")) continue;
            for (int sx : new int[] {1, -1}) {
                for (int sz : new int[] {1, -1}) {
                    int tx = sx * ArenaLayout.sc(f.x()), tz = sz * ArenaLayout.sc(f.z());
                    if (!placed.add(tx + "," + tz + f.leaves())) continue;
                    Shapes.tree(out, allow, flat, f.trunk(), f.leaves(), (int) f.r(), f.h(), tx, tz);
                }
            }
        }
        return out;
    }

    /** a blanket of snow layers */
    private void snowCover(Map<Long, BlockData> out, int radius) {
        int r = (int) Math.ceil(rPlay);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rPlay - 0.5 || d < 4 || spawnDistance(dx, dz) < 3.5 || isPond(dx, dz)) continue;
                double n = sm(dx, dz, 210, 7);
                if (n < 0.1) continue; // bare patches where the wind
                int layers = n > 0.82 ? 3 : n > 0.62 ? 2 : 1;
                out.put(Pack.key(dx, surface(dx, dz) + 1, dz), Blocks.snow(layers));
            }
        }
    }

    private Map<Long, BlockData> buildLargeOverlay(int radius) {
        Map<Long, BlockData> out = new HashMap<>();
        BiPredicate<Integer, Integer> allow = (x, z) -> Math.hypot(x, z) <= rPlay - 1 && spawnDistance(x, z) > 5 && !isPond(x, z);
        IntBinaryOperator surf = this::surface;
        int cell = radius < 80 ? 8 : 13, hs = 500 + theme.ordinal() * 17 + seed * 131; // normal-size arenas get a tighter
        double density = switch (theme) {
            case PLAINS -> .30;
            case DESERT -> .26;
            case POPLAR -> .55;
            case SNOWY -> .42;
            case CHERRY -> .30;
            case STADIUM -> 0;
            case BADLANDS -> .22;
            case JUNGLE -> .65;
            case SAVANNA -> .30;
            case CRIMSON -> .45;
            case PEAKS -> .25;
        };
        if (theme == ArenaTheme.SNOWY) snowCover(out, radius);
        int max = radius / cell + 1;
        for (int gx = -max; gx <= max; gx++) {
            for (int gz = -max; gz <= max; gz++) {
                int fx = gx * cell + (int) (ArenaLayout.hash(gx, gz, hs + 1) * cell);
                int fz = gz * cell + (int) (ArenaLayout.hash(gx, gz, hs + 2) * cell);
                double dens = density;
                if (theme == ArenaTheme.SNOWY) dens *= 0.12 + 2.1 * clamp((sm(fx, fz, 260, 18) - 0.3) / 0.4); // spruce groves with open snow
                if (ArenaLayout.hash(gx, gz, hs) > dens) continue;
                double d = Math.hypot(fx, fz);
                if (d > rPlay - 6 || d < 8 || Math.hypot(Math.abs(fx) - spawnX, fz) < 14) continue;
                double t = ArenaLayout.hash(gx, gz, hs + 3), u = ArenaLayout.hash(gx, gz, hs + 4);
                place(out, allow, surf, fx, fz, t, u); // one piece, no mirror copy
            }
        }
        return out;
    }

    private void place(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                       int x, int z, double t, double u) {
        boolean alongX = u > .5;
        switch (theme) {
            case PLAINS -> {
                Shapes.tree(out, allow, surf, "oaklog", "oakleaves", 2, 4 + (int) (u * 3), x, z);
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
                if (t < .8) Shapes.tree(out, allow, surf, "snowspruce", "spruce", 2, 6, x, z);
                else Shapes.mound(out, allow, surf, x, z, 3 + u * 2, 2, Blocks.data("drift"));
            }
            case STADIUM -> { }
            case BADLANDS -> {
                if (t < .6) Shapes.mound(out, allow, surf, x, z, 3.5 + u * 2, 2 + (int) (u * 2), Blocks.data("redsand"));
                else Shapes.mound(out, allow, surf, x, z, 3 + u, 3, Blocks.data("orangeterra"));
            }
            case JUNGLE -> {
                if (t < .85) Shapes.tree(out, allow, surf, "junglelog", "jungleleaves", 2, 8 + (int) (u * 4), x, z);
                else Shapes.mound(out, allow, surf, x, z, 3 + u, 2, Blocks.data("moss"));
            }
            case SAVANNA -> Shapes.tree(out, allow, surf, "acacialog", "acacialeaves", 3, 5 + (int) (u * 2), x, z);
            case CRIMSON -> {
                if (t < .7) Shapes.tree(out, allow, surf, "crimsonstem", "wartblock", 2, 6 + (int) (u * 3), x, z);
                else Shapes.mound(out, allow, surf, x, z, 3 + u * 2, 2, Blocks.data("netherrack"));
            }
            case PEAKS -> {
                if (t < .5) Shapes.tree(out, allow, surf, "sprucelog", "spruce", 2, 6 + (int) (u * 3), x, z);
                else Shapes.mound(out, allow, surf, x, z, 3 + u * 2, 2 + (int) (u * 2), Blocks.data(u > .5 ? "gravel" : "snow"));
            }
            case CHERRY -> {
                Shapes.tree(out, allow, surf, "cherrylog", "cherry", 2 + (int) (u * 2), 4 + (int) (u * 2), x, z);
            }
        }
    }
}
