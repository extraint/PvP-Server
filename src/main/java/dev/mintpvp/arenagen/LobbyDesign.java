package dev.mintpvp.arenagen;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;

/**
 * The mint lobby: a large floating quartz plaza with a fountain monument in the middle, lime
 * spokes and rings, four small themed gardens (poplar, cherry, snowy, desert), a big
 * "MINT PVP" sign, lamp posts, a railed edge, and an invisible barrier above it.
 * The spawn is just south of the fountain, facing it and the sign.
 */
public final class LobbyDesign {

    /** Platform radius (the rail sits on the outer 1.5 blocks). */
    public static final int R = 40;
    public static final int BARRIER_TOP = 60;
    /** The spawn is this many blocks south of the centre. */
    public static final int SPAWN_DZ = 17;

    /** Garden centres (dx, dz) and what grows in them. */
    private static final int[][] GARDENS = {{-24, -20}, {24, -20}, {-24, 20}, {24, 20}};
    private static final String[] GARDEN_KIND = {"poplar", "cherry", "snowy", "desert"};

    private static final Map<Character, String[]> FONT = new HashMap<>();

    static {
        FONT.put('M', new String[] {"X...X", "XX.XX", "X.X.X", "X...X", "X...X"});
        FONT.put('I', new String[] {"XXXXX", "..X..", "..X..", "..X..", "XXXXX"});
        FONT.put('N', new String[] {"X...X", "XX..X", "X.X.X", "X..XX", "X...X"});
        FONT.put('T', new String[] {"XXXXX", "..X..", "..X..", "..X..", "..X.."});
        FONT.put('P', new String[] {"XXXX.", "X...X", "XXXX.", "X....", "X...."});
        FONT.put('V', new String[] {"X...X", "X...X", "X...X", ".X.X.", "..X.."});
    }

    private LobbyDesign() {}

    private static double gardenDistance(int i, int dx, int dz) {
        return Math.hypot(dx - GARDENS[i][0], dz - GARDENS[i][1]);
    }

    /** The block on top of the plaza at this column (null = the fountain pool). */
    private static Material top(int dx, int dz) {
        double d = Math.hypot(dx, dz), ang = Math.atan2(dz, dx);
        for (int i = 0; i < GARDENS.length; i++) {
            double dg = gardenDistance(i, dx, dz);
            if (dg <= 9) {
                double r = ArenaLayout.hash(dx, dz, 210 + i);
                return switch (GARDEN_KIND[i]) {
                    case "poplar" -> r < .3 ? Material.PODZOL : Material.GRASS_BLOCK;
                    case "cherry" -> Material.GRASS_BLOCK;
                    case "snowy" -> dg <= 3 ? Material.ICE : Material.SNOW_BLOCK;
                    default -> r < .18 ? Material.SMOOTH_SANDSTONE : Material.SAND;
                };
            }
            if (dg <= 9.9) return Material.LIME_CONCRETE;
        }
        if (d <= 4.5) return null;
        if (d <= 6) return Material.QUARTZ_BRICKS;
        if (d <= 13) {
            boolean spoke = d * Math.abs(Math.sin(4 * ang)) / 4 < 0.9;
            return spoke ? Material.LIME_CONCRETE : Material.SMOOTH_QUARTZ;
        }
        if (d <= 14.5) return Material.LIME_CONCRETE;
        if (d <= 35) {
            int sector = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * 24);
            return sector % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
        }
        if (d <= 36.5) return Material.LIME_CONCRETE;
        if (d <= 38.5) return Material.SMOOTH_QUARTZ;
        return Material.LIME_CONCRETE;
    }

    private static boolean inAnyGarden(int dx, int dz, double margin) {
        for (int i = 0; i < GARDENS.length; i++) if (gardenDistance(i, dx, dz) <= 9.9 + margin) return true;
        return false;
    }

    public static void place(Map<Long, BlockData> out, Function<Material, BlockData> data, int cx, int cz, int y0) {
        Map<Long, BlockData> rel = new HashMap<>(); // things built with Shapes, in centre-relative coordinates
        int reach = R + 1;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > R) continue;
                int x = cx + dx, z = cz + dz;
                Material top = top(dx, dz);
                double ang = Math.atan2(dz, dx);

                if (top == null) { // the fountain pool: water over a quartz floor, lit from below
                    out.put(Pack.key(x, y0, z), data.apply(Material.WATER));
                    out.put(Pack.key(x, y0 - 1, z), data.apply(Material.WATER));
                    boolean light = d > 2.5 && d < 3.6 && Math.abs(Math.sin(2 * ang)) < 0.2;
                    out.put(Pack.key(x, y0 - 2, z), data.apply(light ? Material.SEA_LANTERN : Material.QUARTZ_BLOCK));
                } else {
                    out.put(Pack.key(x, y0, z), data.apply(top));
                    boolean garden = inAnyGarden(dx, dz, 0);
                    out.put(Pack.key(x, y0 - 1, z), data.apply(garden && top != Material.LIME_CONCRETE ? Material.DIRT : Material.QUARTZ_BLOCK));
                    out.put(Pack.key(x, y0 - 2, z), data.apply(Material.SMOOTH_QUARTZ));
                    if (d > 4.5 && d <= 6) out.put(Pack.key(x, y0 + 1, z), data.apply(Material.QUARTZ_SLAB)); // pool lip
                }

                // tapering underside, so it reads as a floating island
                for (int k = 1; k <= 16; k++) {
                    if (d <= R - 2.4 * k) {
                        Material m = ArenaLayout.hash(dx, dz + k * 7, 230) < 0.015 ? Material.SEA_LANTERN
                                : k % 3 == 0 ? Material.QUARTZ_BRICKS : k % 3 == 1 ? Material.WHITE_CONCRETE : Material.SMOOTH_QUARTZ;
                        out.put(Pack.key(x, y0 - 2 - k, z), data.apply(m));
                    }
                }

                // railed edge with lanterns every 30 degrees, and the barrier above it
                if (d > 38.5) {
                    out.put(Pack.key(x, y0 + 1, z), data.apply(Material.QUARTZ_BRICKS));
                    out.put(Pack.key(x, y0 + 2, z), data.apply(Material.QUARTZ_BRICKS));
                    double deg = Math.toDegrees(ang);
                    boolean lantern = d > 39 && Math.abs(((deg % 30) + 30) % 30) < 1.5;
                    out.put(Pack.key(x, y0 + 3, z), data.apply(lantern ? Material.SEA_LANTERN : Material.BARRIER));
                    for (int y = y0 + 4; y <= y0 + BARRIER_TOP; y++) out.put(Pack.key(x, y, z), data.apply(Material.BARRIER));
                }
            }
        }

        // fountain monument: a tall pillar with four short jets around it
        BlockData pillar = Blocks.axis(Material.QUARTZ_PILLAR, Axis.Y);
        for (int y = y0 - 1; y <= y0 + 10; y++) out.put(Pack.key(cx, y, cz), pillar);
        out.put(Pack.key(cx, y0 + 11, cz), data.apply(Material.SEA_LANTERN));
        out.put(Pack.key(cx, y0 + 12, cz), data.apply(Material.LIME_STAINED_GLASS));
        for (int[] j : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            for (int y = y0 - 1; y <= y0 + 2; y++) out.put(Pack.key(cx + j[0], y, cz + j[1]), pillar);
            out.put(Pack.key(cx + j[0], y0 + 3, cz + j[1]), data.apply(Material.SEA_LANTERN));
        }

        // lamp posts along two rings, skipping the gardens
        for (double ring : new double[] {20.5, 31}) {
            for (int k = 0; k < 12; k++) {
                double a = Math.toRadians(15 + k * 30);
                int lx = (int) Math.round(ring * Math.cos(a)), lz = (int) Math.round(ring * Math.sin(a));
                if (inAnyGarden(lx, lz, 2) || (Math.abs(lx) <= 15 && lz < -27)) continue;
                for (int y = 1; y <= 3; y++) out.put(Pack.key(cx + lx, y0 + y, cz + lz), pillar);
                out.put(Pack.key(cx + lx, y0 + 4, cz + lz), data.apply(Material.SEA_LANTERN));
            }
        }

        // the sign: MINT over PVP, lime letters on a white wall at the north side, facing the spawn
        sign(out, data, cx, cz, y0);

        // gardens
        gardens(rel, y0);
        for (var e : rel.entrySet()) {
            long k = e.getKey();
            out.put(Pack.key(cx + Pack.x(k), Pack.y(k), cz + Pack.z(k)), e.getValue());
        }
    }

    private static void sign(Map<Long, BlockData> out, Function<Material, BlockData> data, int cx, int cz, int y0) {
        int wz = cz - 31;
        for (int x = -13; x <= 13; x++) {
            for (int y = 1; y <= 13; y++) {
                Material m = y == 1 || x == -13 || x == 13 ? Material.QUARTZ_BRICKS
                        : y == 13 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE;
                out.put(Pack.key(cx + x, y0 + y, wz), data.apply(m));
            }
        }
        BlockData pillar = Blocks.axis(Material.QUARTZ_PILLAR, Axis.Y);
        for (int side : new int[] {-14, 14}) {
            for (int y = 1; y <= 14; y++) out.put(Pack.key(cx + side, y0 + y, wz), pillar);
            out.put(Pack.key(cx + side, y0 + 15, wz), data.apply(Material.SEA_LANTERN));
        }
        text(out, data, "MINT", cx, wz, y0 + 8);
        text(out, data, "PVP", cx, wz, y0 + 2);
    }

    /** Draws a line of 5x5 letters (rows go up from {@code yBottom}), centred on x = cx. */
    private static void text(Map<Long, BlockData> out, Function<Material, BlockData> data, String s, int cx, int z, int yBottom) {
        int width = s.length() * 6 - 1;
        int x = cx - width / 2;
        BlockData lime = data.apply(Material.LIME_CONCRETE);
        for (char ch : s.toCharArray()) {
            String[] rows = FONT.get(ch);
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (rows[r].charAt(c) == 'X') out.put(Pack.key(x + c, yBottom + (4 - r), z), lime);
                }
            }
            x += 6;
        }
    }

    private static void gardens(Map<Long, BlockData> rel, int y0) {
        java.util.function.BiPredicate<Integer, Integer> any = (x, z) -> true;
        IntBinaryOperator flat = (x, z) -> y0;
        for (int i = 0; i < GARDENS.length; i++) {
            int gx = GARDENS[i][0], gz = GARDENS[i][1];
            switch (GARDEN_KIND[i]) {
                case "poplar" -> {
                    Shapes.tree(rel, any, flat, "poplarlog", "poplaryellow", 1, 8, gx - 3, gz - 2);
                    Shapes.tree(rel, any, flat, "poplarlog", "poplarorange", 1, 7, gx + 3, gz + 3);
                    Shapes.tree(rel, any, flat, "poplarlog", "poplarred", 1, 9, gx, gz - 5);
                    Shapes.fallenLog(rel, any, flat, gx + 2, gz - 1, 4, true, "fallen");
                }
                case "cherry" -> {
                    Shapes.tree(rel, any, flat, "cherrylog", "cherry", 3, 5, gx - 3, gz + 2);
                    Shapes.tree(rel, any, flat, "cherrylog", "cherry", 2, 5, gx + 4, gz - 3);
                    for (int k = 0; k < 14; k++) { // petals on the grass
                        int px = gx + (int) (ArenaLayout.hash(k, 1, 220) * 14 - 7), pz = gz + (int) (ArenaLayout.hash(k, 2, 220) * 14 - 7);
                        if (Math.hypot(px - gx, pz - gz) < 8) Shapes.put(rel, any, px, y0 + 1, pz, Blocks.data(Material.PINK_PETALS));
                    }
                }
                case "snowy" -> {
                    Shapes.tree(rel, any, flat, "sprucelog", "spruce", 2, 7, gx - 4, gz - 3);
                    Shapes.tree(rel, any, flat, "sprucelog", "spruce", 2, 6, gx + 3, gz + 4);
                    Shapes.column(rel, any, flat, gx + 5, gz - 1, 5, Blocks.data("spike"));
                    Shapes.mound(rel, any, flat, gx - 2, gz + 4, 2.6, 2, Blocks.data("drift"));
                }
                default -> {
                    Shapes.column(rel, any, flat, gx - 4, gz - 2, 4, Blocks.data("pillar"));
                    Shapes.put(rel, any, gx - 4, y0 + 5, gz - 2, Blocks.data("chiseled"));
                    Shapes.column(rel, any, flat, gx + 3, gz + 3, 3, Blocks.data("pillar"));
                    Shapes.put(rel, any, gx + 3, y0 + 4, gz + 3, Blocks.data("chiseled"));
                    Shapes.wall(rel, any, flat, gx + 1, gz - 5, 5, true, 2, 3, Blocks.data("cut"));
                    Shapes.mound(rel, any, flat, gx - 2, gz + 4, 3.2, 2, Blocks.data("dune"));
                }
            }
        }
    }
}
