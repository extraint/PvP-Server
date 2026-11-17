package dev.mintpvp.arenagen;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;

/** lobby plaza */
public final class LobbyDesign {

    /** platform radius */
    public static final int R = 40;
    public static final int BARRIER_TOP = 60;
    /** spawn offset */
    public static final int SPAWN_DZ = 22;
    /** dais height */
    private static final int DAIS = 3;

    /** garden centres */
    private static final int[][] GARDENS = {{-24, -14}, {24, -14}, {-24, 14}, {24, 14}};
    private static final String[] GARDEN_KIND = {"poplar", "cherry", "snowy", "desert"};
    private static final double GARDEN_R = 9.5;

    private static final Material[] FLOWERS = {Material.RED_TULIP, Material.ORANGE_TULIP, Material.PINK_TULIP, Material.WHITE_TULIP,
            Material.AZURE_BLUET, Material.ALLIUM, Material.CORNFLOWER, Material.OXEYE_DAISY};

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

    /** terrace level */
    private static int level(int dx, int dz) {
        double d = Math.hypot(dx, dz);
        if (d <= 8) return 3;
        if (d <= 12) return 2;
        if (d <= 16) return 1;
        return 0;
    }

    private static double gardenDistance(int i, int dx, int dz) {
        return Math.hypot(dx - GARDENS[i][0], dz - GARDENS[i][1]);
    }

    private static boolean inAnyGarden(int dx, int dz, double margin) {
        for (int i = 0; i < GARDENS.length; i++) if (gardenDistance(i, dx, dz) <= GARDEN_R + 0.9 + margin) return true;
        return false;
    }

    /** plaza top block */
    private static Material top(int dx, int dz) {
        double d = Math.hypot(dx, dz), ang = Math.atan2(dz, dx);
        for (int i = 0; i < GARDENS.length; i++) {
            double dg = gardenDistance(i, dx, dz);
            if (dg <= GARDEN_R) {
                double r = ArenaLayout.hash(dx, dz, 210 + i);
                return switch (GARDEN_KIND[i]) {
                    case "poplar" -> r < .3 ? Material.PODZOL : Material.GRASS_BLOCK;
                    case "cherry" -> Material.GRASS_BLOCK;
                    case "snowy" -> dg <= 3.5 ? Material.ICE : Material.SNOW_BLOCK;
                    default -> r < .18 ? Material.SMOOTH_SANDSTONE : Material.SAND;
                };
            }
            if (dg <= GARDEN_R + 0.9) return Material.LIME_CONCRETE;
        }
        if (d <= 4.5) return null;
        if (d <= 6) return Material.QUARTZ_BRICKS;
        if (d <= 16) {
            boolean spoke = d * Math.abs(Math.sin(4 * ang)) / 4 < 0.9;
            boolean ringLine = (d > 6 && d <= 7) || (d > 10 && d <= 11) || (d > 14 && d <= 15);
            return ringLine || spoke ? Material.LIME_CONCRETE : Material.SMOOTH_QUARTZ;
        }
        if (d <= 35) {
            boolean mintRing = (d > 20 && d <= 21) || (d > 26 && d <= 27) || (d > 31 && d <= 32);
            if (mintRing) return Material.PRISMARINE_BRICKS;
            int sector = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * 24);
            return sector % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
        }
        if (d <= 36.5) return Material.LIME_CONCRETE;
        if (d <= 38.5) return Material.SMOOTH_QUARTZ;
        return Material.LIME_CONCRETE;
    }

    private static BlockData stairs(Function<Material, BlockData> data, BlockFace facing) {
        BlockData d = data.apply(Material.QUARTZ_STAIRS).clone();
        if (d instanceof Stairs s) s.setFacing(facing);
        return d;
    }

    public static void place(Map<Long, BlockData> out, Function<Material, BlockData> data, int cx, int cz, int y0) {
        Map<Long, BlockData> rel = new HashMap<>();
        BlockData pillar = Blocks.axis(Material.QUARTZ_PILLAR, Axis.Y);
        int yc = y0 + DAIS;

        for (int dx = -R - 1; dx <= R + 1; dx++) {
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > R) continue;
                int x = cx + dx, z = cz + dz;
                int lvl = level(dx, dz);
                int ytop = y0 + lvl;
                Material top = top(dx, dz);
                double ang = Math.atan2(dz, dx);

                if (top == null) { // fountain pool
                    out.put(Pack.key(x, ytop, z), data.apply(Material.WATER));
                    out.put(Pack.key(x, ytop - 1, z), data.apply(Material.WATER));
                    boolean light = d > 2.6 && d < 3.8 && Math.abs(Math.sin(2 * ang)) < 0.25;
                    out.put(Pack.key(x, ytop - 2, z), data.apply(light ? Material.SEA_LANTERN : Material.QUARTZ_BLOCK));
                    for (int y = y0 - 2; y < ytop - 2; y++) out.put(Pack.key(x, y, z), data.apply(Material.QUARTZ_BLOCK));
                } else {
                    // stair at step edge
                    BlockData topBlock = data.apply(top);
                    if (lvl > 0) {
                        // face the centre
                        int ox = Math.abs(dx) >= Math.abs(dz) ? Integer.signum(dx) : 0;
                        int oz = ox == 0 ? Integer.signum(dz) : 0;
                        BlockFace face = null;
                        if ((ox != 0 || oz != 0) && level(dx + ox, dz + oz) == lvl - 1) {
                            face = ox > 0 ? BlockFace.WEST : ox < 0 ? BlockFace.EAST : oz > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
                        } else {
                            int[][] n = {{0, -1, 0}, {0, 1, 1}, {-1, 0, 2}, {1, 0, 3}};
                            BlockFace[] up = {BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST};
                            for (int[] o : n) {
                                if (level(dx + o[0], dz + o[1]) == lvl - 1) {
                                    face = up[o[2]];
                                    break;
                                }
                            }
                        }
                        if (face != null) topBlock = stairs(data, face);
                    }
                    out.put(Pack.key(x, ytop, z), topBlock);
                    boolean garden = inAnyGarden(dx, dz, 0) && lvl == 0;
                    Material body = garden && top != Material.LIME_CONCRETE ? Material.DIRT : Material.QUARTZ_BLOCK;
                    for (int y = y0 - 1; y < ytop; y++) out.put(Pack.key(x, y, z), data.apply(y == y0 - 1 || lvl == 0 ? body : Material.QUARTZ_BLOCK));
                    out.put(Pack.key(x, y0 - 2, z), data.apply(Material.SMOOTH_QUARTZ));
                    if (d > 4.5 && d <= 6) out.put(Pack.key(x, ytop + 1, z), data.apply(Material.QUARTZ_SLAB));
                }

                // floating underside
                for (int k = 1; k <= 16; k++) {
                    if (d <= R - 2.4 * k) {
                        Material m = ArenaLayout.hash(dx, dz + k * 7, 230) < 0.015 ? Material.SEA_LANTERN
                                : k % 3 == 0 ? Material.QUARTZ_BRICKS : k % 3 == 1 ? Material.WHITE_CONCRETE : Material.SMOOTH_QUARTZ;
                        out.put(Pack.key(x, y0 - 2 - k, z), data.apply(m));
                    }
                }

                // rail and lanterns
                if (d > 38.5) {
                    out.put(Pack.key(x, y0 + 1, z), data.apply(Material.QUARTZ_BRICKS));
                    out.put(Pack.key(x, y0 + 2, z), data.apply(Material.QUARTZ_BRICKS));
                    double deg = Math.toDegrees(ang);
                    boolean lantern = d > 39 && Math.abs(((deg % 30) + 30) % 30) < 1.5;
                    out.put(Pack.key(x, y0 + 3, z), data.apply(lantern ? Material.SEA_LANTERN : Material.BARRIER));
                    for (int y = y0 + 4; y <= y0 + BARRIER_TOP; y++) out.put(Pack.key(x, y, z), data.apply(Material.BARRIER));
                }

                // hedge ring
                if (d > 16.2 && d <= 17.4 && d * Math.abs(Math.sin(4 * ang)) / 4 >= 1.7) {
                    out.put(Pack.key(x, y0 + 1, z), data.apply(Material.FLOWERING_AZALEA_LEAVES));
                    out.put(Pack.key(x, y0 + 2, z), data.apply(Material.FLOWERING_AZALEA_LEAVES));
                }
            }
        }

        // centre beacon
        for (int px = -1; px <= 1; px++) {
            for (int pz = -1; pz <= 1; pz++) {
                out.put(Pack.key(cx + px, yc - 1, cz + pz), data.apply(Material.EMERALD_BLOCK));
                out.put(Pack.key(cx + px, yc, cz + pz), data.apply(Material.EMERALD_BLOCK));
            }
        }
        out.put(Pack.key(cx, yc + 1, cz), data.apply(Material.BEACON));
        out.put(Pack.key(cx, yc + 2, cz), data.apply(Material.LIME_STAINED_GLASS));
        for (int[] c : new int[][] {{5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
            for (int y = yc + 1; y <= yc + 9; y++) out.put(Pack.key(cx + c[0], y, cz + c[1]), pillar);
            out.put(Pack.key(cx + c[0], yc + 10, cz + c[1]), data.apply(Material.SEA_LANTERN));
        }
        for (int[] j : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            for (int y = yc - 1; y <= yc + 1; y++) out.put(Pack.key(cx + j[0], y, cz + j[1]), pillar);
            out.put(Pack.key(cx + j[0], yc + 2, cz + j[1]), data.apply(Material.SEA_LANTERN));
        }

        // lamp posts
        for (double ring : new double[] {20.5, 31}) {
            for (int k = 0; k < 12; k++) {
                double a = Math.toRadians(15 + k * 30);
                int lx = (int) Math.round(ring * Math.cos(a)), lz = (int) Math.round(ring * Math.sin(a));
                if (inAnyGarden(lx, lz, 2) || (Math.abs(lx) <= 25 && lz < -22)) continue;
                for (int y = 1; y <= 3; y++) out.put(Pack.key(cx + lx, y0 + y, cz + lz), pillar);
                out.put(Pack.key(cx + lx, y0 + 4, cz + lz), data.apply(Material.SEA_LANTERN));
            }
        }

        // planters
        int[][] planters = {{22, 0}, {-22, 0}, {12, 23}, {-12, 23}, {20, 12}, {-20, 12}, {0, -22}};
        for (int k = 0; k < planters.length; k++) {
            int px = planters[k][0], pz = planters[k][1];
            if (inAnyGarden(px, pz, 2)) continue;
            planter(out, data, cx + px - 1, cz + pz - 1, y0, k);
        }

        // benches
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + k * 45);
            int bx = (int) Math.round(18.9 * Math.cos(a)), bz = (int) Math.round(18.9 * Math.sin(a));
            if (inAnyGarden(bx, bz, 1)) continue;
            boolean horizontal = Math.abs(bx) > Math.abs(bz);
            BlockFace outward = horizontal ? (bx > 0 ? BlockFace.EAST : BlockFace.WEST) : (bz > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
            out.put(Pack.key(cx + bx, y0 + 1, cz + bz), stairs(data, outward));
            out.put(Pack.key(cx + bx + (horizontal ? 0 : 1), y0 + 1, cz + bz + (horizontal ? 1 : 0)), stairs(data, outward));
        }

        // crystal spires
        for (int[] s : new int[][] {{31, 0}, {-31, 0}, {0, 31}}) spire(out, data, cx + s[0], cz + s[1], y0);

        sign(out, data, cx, cz, y0);
        gardens(rel, y0);
        islets(rel, data, y0);
        for (var e : rel.entrySet()) {
            long k = e.getKey();
            out.put(Pack.key(cx + Pack.x(k), Pack.y(k), cz + Pack.z(k)), e.getValue());
        }
    }

    private static void planter(Map<Long, BlockData> out, Function<Material, BlockData> data, int x0, int z0, int y0, int seed) {
        for (int ix = 0; ix < 4; ix++) {
            for (int iz = 0; iz < 4; iz++) {
                boolean border = ix == 0 || iz == 0 || ix == 3 || iz == 3;
                out.put(Pack.key(x0 + ix, y0 + 1, z0 + iz), data.apply(border ? Material.QUARTZ_BRICKS : Material.GRASS_BLOCK));
                if (!border) {
                    Material f = FLOWERS[(int) (ArenaLayout.hash(ix, iz, 300 + seed) * FLOWERS.length) % FLOWERS.length];
                    out.put(Pack.key(x0 + ix, y0 + 2, z0 + iz), data.apply(f));
                }
            }
        }
    }

    private static void spire(Map<Long, BlockData> out, Function<Material, BlockData> data, int x, int z, int y0) {
        BlockData pillar = Blocks.axis(Material.QUARTZ_PILLAR, Axis.Y);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.put(Pack.key(x + dx, y0 + 1, z + dz), data.apply(Material.QUARTZ_BRICKS));
                if (dx == 0 || dz == 0) out.put(Pack.key(x + dx, y0 + 2, z + dz), data.apply(Material.QUARTZ_BRICKS));
            }
        }
        for (int y = 2; y <= 9; y++) out.put(Pack.key(x, y0 + y, z), pillar);
        for (int y = 10; y <= 13; y++) out.put(Pack.key(x, y0 + y, z), data.apply(Material.LIME_STAINED_GLASS));
        out.put(Pack.key(x, y0 + 14, z), data.apply(Material.SEA_LANTERN));
        out.put(Pack.key(x + 1, y0 + 11, z), data.apply(Material.LIME_STAINED_GLASS));
        out.put(Pack.key(x - 1, y0 + 11, z), data.apply(Material.LIME_STAINED_GLASS));
        out.put(Pack.key(x, y0 + 11, z + 1), data.apply(Material.LIME_STAINED_GLASS));
        out.put(Pack.key(x, y0 + 11, z - 1), data.apply(Material.LIME_STAINED_GLASS));
    }

    private static void sign(Map<Long, BlockData> out, Function<Material, BlockData> data, int cx, int cz, int y0) {
        int wz = cz - 27;
        // sign panel
        for (int x = -23; x <= 23; x++) {
            for (int y = 1; y <= 29; y++) {
                Material m;
                if (y <= 2) m = Material.QUARTZ_BRICKS;
                else if (Math.abs(x) == 23 || y == 3 || y == 29) m = Material.SEA_LANTERN;
                else if (Math.abs(x) == 22 || y == 4 || y == 28) m = Material.LIME_CONCRETE;
                else if (y == 15 && Math.abs(x) <= 20) m = Material.LIME_CONCRETE;
                else m = Material.DARK_PRISMARINE;
                out.put(Pack.key(cx + x, y0 + y, wz), data.apply(m));
                out.put(Pack.key(cx + x, y0 + y, wz - 1), data.apply(m));
            }
        }
        // crest
        for (int x = -8; x <= 8; x++) out.put(Pack.key(cx + x, y0 + 30, wz), data.apply(Material.QUARTZ_BRICKS));
        for (int x = -5; x <= 5; x++) out.put(Pack.key(cx + x, y0 + 31, wz), data.apply(Material.LIME_CONCRETE));
        for (int x = -2; x <= 2; x++) out.put(Pack.key(cx + x, y0 + 32, wz), data.apply(Material.SEA_LANTERN));
        BlockData pillar = Blocks.axis(Material.QUARTZ_PILLAR, Axis.Y);
        for (int side : new int[] {-24, 24}) {
            for (int y = 1; y <= 30; y++) {
                out.put(Pack.key(cx + side, y0 + y, wz), pillar);
                out.put(Pack.key(cx + side, y0 + y, wz - 1), pillar);
            }
            out.put(Pack.key(cx + side, y0 + 31, wz), data.apply(Material.SEA_LANTERN));
            out.put(Pack.key(cx + side, y0 + 31, wz - 1), data.apply(Material.SEA_LANTERN));
        }
        // flower bed
        for (int x = -21; x <= 21; x++) {
            out.put(Pack.key(cx + x, y0 + 1, wz + 1), data.apply(Material.GRASS_BLOCK));
            out.put(Pack.key(cx + x, y0 + 1, wz + 2), data.apply(Material.GRASS_BLOCK));
            out.put(Pack.key(cx + x, y0 + 2, wz + 1), data.apply(x % 7 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.OXEYE_DAISY));
            Material f = FLOWERS[(int) (ArenaLayout.hash(x, 5, 310) * FLOWERS.length) % FLOWERS.length];
            out.put(Pack.key(cx + x, y0 + 2, wz + 2), data.apply(f));
        }
        text(out, data, "MINT", cx, wz + 1, y0 + 17);
        text(out, data, "PVP", cx, wz + 1, y0 + 5);
    }

    /** draw letters */
    private static void text(Map<Long, BlockData> out, Function<Material, BlockData> data, String s, int cx, int z, int yBottom) {
        int width = s.length() * 11 - 1;
        int x = cx - width / 2;
        BlockData lime = data.apply(Material.LIME_CONCRETE);
        BlockData shade = data.apply(Material.GREEN_CONCRETE);
        for (char ch : s.toCharArray()) {
            String[] rows = FONT.get(ch);
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (rows[r].charAt(c) != 'X') continue;
                    for (int px = 0; px < 2; px++) {
                        for (int py = 0; py < 2; py++) {
                            int bx = x + c * 2 + px, by = yBottom + (4 - r) * 2 + py;
                            out.put(Pack.key(bx + 1, by - 1, z - 1), shade);
                            out.put(Pack.key(bx, by, z), lime);
                        }
                    }
                }
            }
            x += 11;
        }
    }

    private static void gardens(Map<Long, BlockData> rel, int y0) {
        BiPredicate<Integer, Integer> any = (x, z) -> true;
        IntBinaryOperator flat = (x, z) -> y0;
        for (int i = 0; i < GARDENS.length; i++) {
            int gx = GARDENS[i][0], gz = GARDENS[i][1];
            switch (GARDEN_KIND[i]) {
                case "poplar" -> {
                    Shapes.tree(rel, any, flat, "poplarlog", "poplaryellow", 1, 9, gx - 4, gz - 3);
                    Shapes.tree(rel, any, flat, "poplarlog", "poplarorange", 1, 8, gx + 4, gz + 3);
                    Shapes.tree(rel, any, flat, "poplarlog", "poplarred", 1, 10, gx + 1, gz - 6);
                    Shapes.tree(rel, any, flat, "poplarlog", "poplaryellow", 1, 7, gx - 5, gz + 4);
                    Shapes.tree(rel, any, flat, "poplarlog", "poplarorange", 1, 9, gx + 6, gz - 2);
                    Shapes.fallenLog(rel, any, flat, gx + 1, gz + 1, 4, true, "fallen");
                }
                case "cherry" -> {
                    Shapes.tree(rel, any, flat, "cherrylog", "cherry", 3, 6, gx - 3, gz + 3);
                    Shapes.tree(rel, any, flat, "cherrylog", "cherry", 3, 5, gx + 4, gz - 3);
                    Shapes.tree(rel, any, flat, "cherrylog", "cherry", 2, 5, gx - 5, gz - 4);
                    for (int k = 0; k < 40; k++) {
                        int px = gx + (int) (ArenaLayout.hash(k, 1, 220) * 18 - 9), pz = gz + (int) (ArenaLayout.hash(k, 2, 220) * 18 - 9);
                        if (Math.hypot(px - gx, pz - gz) >= GARDEN_R - 1) continue;
                        Material m = k % 3 == 0 ? Material.PINK_TULIP : k % 3 == 1 ? Material.PINK_PETALS : Material.WHITE_TULIP;
                        Shapes.put(rel, any, px, y0 + 1, pz, Blocks.data(m));
                    }
                }
                case "snowy" -> {
                    Shapes.tree(rel, any, flat, "sprucelog", "spruce", 2, 8, gx - 5, gz - 3);
                    Shapes.tree(rel, any, flat, "sprucelog", "spruce", 2, 7, gx + 4, gz + 5);
                    Shapes.tree(rel, any, flat, "sprucelog", "spruce", 2, 6, gx + 5, gz - 4);
                    Shapes.column(rel, any, flat, gx + 6, gz + 1, 6, Blocks.data("spike"));
                    Shapes.column(rel, any, flat, gx - 1, gz + 6, 4, Blocks.data("spike"));
                    Shapes.mound(rel, any, flat, gx - 3, gz + 3, 2.8, 2, Blocks.data("drift"));
                    Shapes.mound(rel, any, flat, gx + 2, gz - 6, 2.6, 2, Blocks.data("drift"));
                }
                default -> {
                    Shapes.column(rel, any, flat, gx - 5, gz - 2, 5, Blocks.data("pillar"));
                    Shapes.put(rel, any, gx - 5, y0 + 6, gz - 2, Blocks.data("chiseled"));
                    Shapes.column(rel, any, flat, gx + 4, gz + 4, 4, Blocks.data("pillar"));
                    Shapes.put(rel, any, gx + 4, y0 + 5, gz + 4, Blocks.data("chiseled"));
                    Shapes.column(rel, any, flat, gx + 5, gz - 4, 3, Blocks.data("pillar"));
                    Shapes.put(rel, any, gx + 5, y0 + 4, gz - 4, Blocks.data("chiseled"));
                    Shapes.wall(rel, any, flat, gx, gz - 6, 7, true, 2, 4, Blocks.data("cut"));
                    Shapes.mound(rel, any, flat, gx - 3, gz + 4, 3.6, 3, Blocks.data("dune"));
                    Shapes.mound(rel, any, flat, gx + 2, gz + 1, 2.6, 2, Blocks.data("dune"));
                }
            }
        }
    }

    /** floating islets */
    private static void islets(Map<Long, BlockData> rel, Function<Material, BlockData> data, int y0) {
        BiPredicate<Integer, Integer> any = (x, z) -> true;
        // islet data
        double[][] spec = {{25, 62, 6, 6, 0}, {115, 66, 12, 7, 1}, {205, 60, 3, 5, 2}, {295, 68, 9, 6, 0}, {160, 74, 17, 4, 1}};
        for (double[] s : spec) {
            double a = Math.toRadians(s[0]);
            int ix = (int) Math.round(s[1] * Math.cos(a)), iz = (int) Math.round(s[1] * Math.sin(a));
            int iy = y0 + (int) s[2];
            double r = s[3];
            for (int dx = -(int) r - 1; dx <= (int) r + 1; dx++) {
                for (int dz = -(int) r - 1; dz <= (int) r + 1; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r) continue;
                    rel.put(Pack.key(ix + dx, iy, iz + dz), data.apply(Material.GRASS_BLOCK));
                    rel.put(Pack.key(ix + dx, iy - 1, iz + dz), data.apply(Material.DIRT));
                    for (int k = 2; k <= 7; k++) {
                        if (d <= r - (k - 1) * 0.85) rel.put(Pack.key(ix + dx, iy - k, iz + dz), data.apply(k % 2 == 0 ? Material.STONE : Material.COBBLESTONE));
                    }
                }
            }
            IntBinaryOperator surf = (x, z) -> iy;
            switch ((int) s[4]) {
                case 0 -> Shapes.tree(rel, any, surf, "cherrylog", "cherry", 2, 5, ix, iz);
                case 1 -> Shapes.tree(rel, any, surf, "poplarlog", "poplarorange", 1, 7, ix, iz);
                default -> Shapes.tree(rel, any, surf, "oaklog", "oakleaves", 2, 5, ix, iz);
            }
        }
    }
}
