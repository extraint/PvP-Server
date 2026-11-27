package dev.mintpvp.arenagen;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** three-island lobby */
public final class LobbyDesign {

    /** spawn island radius */
    public static final int R = 50;
    public static final int BARRIER_TOP = 40;
    /** spawn offset */
    public static final int SPAWN_DZ = 22;
    /** nether grove offset */
    public static final int NX = 115;
    /** waterfall island offset */
    public static final int WX = -120;
    /** lobby bounds */
    public static final int MIN_X = -185, MAX_X = 175, MIN_Z = -75, MAX_Z = 80;

    private static final String[] FONT_KEYS = {"M", "I", "N", "T", "P", "V"};
    private static final String[][] FONT = {
            {"X...X", "XX.XX", "X.X.X", "X...X", "X...X"},
            {"XXXXX", "..X..", "..X..", "..X..", "XXXXX"},
            {"X...X", "XX..X", "X.X.X", "X..XX", "X...X"},
            {"XXXXX", "..X..", "..X..", "..X..", "..X.."},
            {"XXXX.", "X...X", "XXXX.", "X....", "X...."},
            {"X...X", "X...X", "X...X", ".X.X.", "..X.."}};
    private static final Material[] FLOWERS = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.ALLIUM, Material.PINK_TULIP, Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY};

    private final Map<Long, Material> w = new HashMap<>(400_000);
    private final Map<Long, Integer> heights = new HashMap<>();

    private LobbyDesign() {}

    // helpers

    static double h(int x, int z, int s) {
        int n = x * 374761393 + z * 668265263 + s * 2147483647;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xffffff) / (double) 0xffffff;
    }

    static double noise(double x, double z, int s, double sc) {
        double gx = x / sc, gz = z / sc;
        int x0 = (int) Math.floor(gx), z0 = (int) Math.floor(gz);
        double tx = gx - x0, tz = gz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = h(x0, z0, s), b = h(x0 + 1, z0, s), c = h(x0, z0 + 1, s), d = h(x0 + 1, z0 + 1, s);
        double top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    private static long k(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private void put(int x, int y, int z, Material m) {
        w.put(Pack.key(x, y, z), m);
    }

    private Material get(int x, int y, int z) {
        return w.get(Pack.key(x, y, z));
    }

    private void remove(int x, int y, int z) {
        w.remove(Pack.key(x, y, z));
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    private static boolean isFlora(Material m) {
        if (m == null) return false;
        for (Material f : FLOWERS) if (f == m) return true;
        return m == Material.SHORT_GRASS || m == Material.FERN;
    }

    /** island outline radius */
    private static double outline(double x, double z, double cx, double cz, double r, int seed) {
        double a = Math.atan2(z - cz, x - cx);
        return r + 2.2 * Math.sin(3 * a + seed) + 1.6 * Math.sin(5 * a + seed * 2)
                + 2 * noise(Math.cos(a) * 20, Math.sin(a) * 20, seed, 6) - 1;
    }

    private void underside(int x, int z, double t, int seed) {
        int depth = (int) (Math.pow(1 - t, 0.8) * 24 + noise(x, z, seed, 4) * 5);
        for (int k = 2; k < depth + 2; k++) {
            Material m = k > depth * 0.65 ? Material.DEEPSLATE : k > 3 ? Material.STONE : Material.DIRT;
            if (h(x, z * 31 + k, seed) < 0.01) m = Material.MOSSY_COBBLESTONE;
            else if (h(x, k, z + seed) < 0.04 && k > 5) m = Material.ANDESITE;
            put(x, -k, z, m);
        }
        if (depth > 5 && h(x, z, seed + 9) < 0.05) {
            int len = (int) (h(x, z, seed + 8) * 5) + 1;
            for (int k = depth + 2; k < depth + 2 + len; k++) put(x, -k, z, Material.HANGING_ROOTS);
        }
    }

    private void tree(int x, int y, int z, String kind, int hgt) {
        Material log = switch (kind) { case "birch" -> Material.BIRCH_LOG; case "cherry" -> Material.CHERRY_LOG; default -> Material.OAK_LOG; };
        Material leaf = switch (kind) { case "birch" -> Material.BIRCH_LEAVES; case "cherry" -> Material.CHERRY_LEAVES; default -> Material.OAK_LEAVES; };
        for (int i = 1; i <= hgt; i++) put(x, y + i, z, log);
        int r = kind.equals("cherry") ? 4 : 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + (dy * 1.6) * (dy * 1.6));
                    double lim = r + 0.3 - (h(x + dx, z + dz, dy + 5) < 0.3 ? 0.7 : 0);
                    if (d <= lim && get(x + dx, y + hgt + dy, z + dz) == null) put(x + dx, y + hgt + dy, z + dz, leaf);
                }
            }
        }
        if (kind.equals("cherry")) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (h(dx, dz, x) < 0.25 && get(x + dx, y + 1, z + dz) == null) put(x + dx, y + 1, z + dz, Material.PINK_PETALS);
                }
            }
        }
    }

    private void text(String s, int cx, int yb, int z, int depthStep) {
        int width = s.length() * 11 - 1;
        int x = cx - width / 2;
        for (char ch : s.toCharArray()) {
            String[] rows = FONT[java.util.Arrays.asList(FONT_KEYS).indexOf(String.valueOf(ch))];
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (rows[r].charAt(c) != 'X') continue;
                    for (int px = 0; px < 2; px++) {
                        for (int py = 0; py < 2; py++) {
                            int bx = x + c * 2 + px, by = yb + (4 - r) * 2 + py;
                            put(bx + 1, by - 1, z + 1, Material.GREEN_CONCRETE);
                            put(bx, by, z + 1 + depthStep, Material.LIME_CONCRETE);
                        }
                    }
                }
            }
            x += 11;
        }
    }

    // spawn meadow

    private void spawnMeadow() {
        for (int x = -R - 6; x <= R + 6; x++) {
            for (int z = -R - 6; z <= R + 6; z++) {
                double d = Math.hypot(x, z), e = outline(x, z, 0, 0, R, 3);
                if (d > e) continue;
                put(x, 0, z, Material.GRASS_BLOCK);
                put(x, -1, z, Material.DIRT);
                heights.put(k(x, z), 0);
                underside(x, z, d / e, 21);
            }
        }
        // plaza
        for (int x = -17; x <= 17; x++) {
            for (int z = -17; z <= 17; z++) {
                double d = Math.hypot(x, z);
                if (d > 16.5) continue;
                double ang = Math.atan2(z, x);
                Material m;
                if (d <= 4.5) m = Material.WATER;
                else if (d <= 5.6) m = Material.QUARTZ_BRICKS;
                else if (d > 15.5) m = Material.PRISMARINE_BRICKS;
                else if (d > 9.5 && d <= 10.5) m = Material.LIME_CONCRETE;
                else if (d * Math.abs(Math.sin(4 * ang)) / 4 < 0.6) m = Material.PRISMARINE_BRICKS;
                else m = ((int) d) % 2 == 1 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
                put(x, 0, z, m);
                if (m == Material.WATER) {
                    put(x, -1, z, Material.WATER);
                    put(x, -2, z, h(x, z, 3) < 0.35 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS);
                }
                if (d > 4.5 && d <= 5.6) put(x, 1, z, Material.QUARTZ_SLAB);
            }
        }
        for (int y = -1; y <= 4; y++) put(0, y, 0, Material.QUARTZ_PILLAR);
        put(0, 5, 0, Material.SEA_LANTERN);
        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) put(o[0], 4, o[1], Material.WATER);
        // paths
        for (int x = -R - 6; x <= R + 6; x++) {
            for (int z = -R - 6; z <= R + 6; z++) {
                if (get(x, 0, z) != Material.GRASS_BLOCK) continue;
                boolean ew = Math.abs(z) <= 2 && Math.abs(x) > 14, ns = Math.abs(x) <= 2 && Math.abs(z) > 14;
                if (!ew && !ns) continue;
                boolean edge = ew ? Math.abs(z) == 2 : Math.abs(x) == 2;
                put(x, 0, z, edge ? Material.PRISMARINE_BRICKS : Material.SMOOTH_QUARTZ);
            }
        }
        // sign
        int sz = -38;
        for (int x = -23; x <= 23; x++) {
            for (int y = 1; y <= 30; y++) {
                int ax = Math.abs(x);
                Material m;
                if (ax >= 23) m = Material.QUARTZ_PILLAR;
                else if (y <= 2) m = Material.QUARTZ_BRICKS;
                else if (ax == 22 || y == 3 || y == 30) m = (x + y) % 4 == 0 ? Material.SEA_LANTERN : Material.LIME_CONCRETE;
                else if (y == 16) m = Material.LIME_CONCRETE;
                else m = Material.DARK_PRISMARINE;
                put(x, y, sz, m);
                put(x, y, sz - 1, m);
            }
        }
        text("MINT", 0, 18, sz, 1);
        text("PVP", 0, 5, sz, 1);
        for (int x = -5; x <= 5; x++) put(x, 31, sz, Material.LIME_CONCRETE);
        for (int x = -2; x <= 2; x++) put(x, 32, sz, Material.SEA_LANTERN);
        // trees
        java.util.List<int[]> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double a = h(i, 1, 90) * 2 * Math.PI, r = 20 + h(i, 2, 90) * 28;
            int x = round(r * Math.cos(a)), z = round(r * Math.sin(a));
            if (get(x, 0, z) != Material.GRASS_BLOCK || Math.abs(z) < 6 || Math.abs(x) < 6 || (z < sz + 4 && Math.abs(x) < 26)) continue;
            boolean near = false;
            for (int[] s : spots) if (Math.hypot(x - s[0], z - s[1]) < 8) near = true;
            if (near) continue;
            spots.add(new int[] {x, z});
            String kind = new String[] {"oak", "birch", "cherry"}[(int) (h(i, 3, 90) * 3)];
            tree(x, 0, z, kind, 4 + (int) (h(i, 4, 90) * 3));
        }
        for (int x = -R - 6; x <= R + 6; x++) {
            for (int z = -R - 6; z <= R + 6; z++) {
                if (get(x, 0, z) != Material.GRASS_BLOCK || get(x, 1, z) != null) continue;
                double r = h(x, z, 95);
                if (r < 0.04) put(x, 1, z, FLOWERS[(int) (h(x, z, 96) * FLOWERS.length) % FLOWERS.length]);
                else if (r < 0.2) put(x, 1, z, Material.SHORT_GRASS);
            }
        }
        // lamps
        for (int ang = 0; ang < 360; ang += 30) {
            double a = Math.toRadians(ang + 15);
            int x = round(18.5 * Math.cos(a)), z = round(18.5 * Math.sin(a));
            for (int y = 1; y <= 3; y++) put(x, y, z, Material.QUARTZ_PILLAR);
            put(x, 4, z, Material.SEA_LANTERN);
        }
        for (int s = 20; s < 48; s += 7) {
            for (int[] p : new int[][] {{s, 3}, {s, -3}, {-s, 3}, {-s, -3}, {3, s}, {-3, s}}) {
                if (get(p[0], 0, p[1]) == null) continue;
                for (int y = 1; y <= 2; y++) put(p[0], y, p[1], Material.QUARTZ_PILLAR);
                put(p[0], 3, p[1], Material.SEA_LANTERN);
            }
        }
        // spawn dais
        for (int x = -3; x <= 3; x++) {
            for (int z = 19; z <= 25; z++) {
                double d = Math.hypot(x, z - SPAWN_DZ);
                if (d > 3.3) continue;
                put(x, 0, z, d > 2.3 ? Material.LIME_CONCRETE : (x == 0 && z == SPAWN_DZ) ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ);
                for (int y = 1; y <= 2; y++) remove(x, y, z);
            }
        }
    }

    // nether grove

    private static double netherEdge(double ang) {
        return 40 - 3 + 3.5 * Math.sin(3 * ang + 0.7) * 0.5 + 2.2 * Math.sin(5 * ang + 2.1) * 0.5
                + 1.5 * noise(Math.cos(ang) * 20, Math.sin(ang) * 20, 7, 6);
    }

    private static boolean netherInside(int x, int z, double m) {
        return Math.hypot(x, z) <= netherEdge(Math.atan2(z, x)) - m;
    }

    private void nether() {
        Map<Long, Material> saved = new HashMap<>(w);
        w.clear();
        int nr = 40;
        for (int x = -nr - 4; x <= nr + 4; x++) {
            for (int z = -nr - 4; z <= nr + 4; z++) {
                double d = Math.hypot(x, z), e = netherEdge(Math.atan2(z, x));
                if (d > e) continue;
                double t = d / e;
                double n = noise(x, z, 11, 7);
                Material top = n < 0.33 ? (noise(x, z, 12, 13) < 0.38 ? Material.CRIMSON_NYLIUM : Material.MOSS_BLOCK) : Material.WARPED_NYLIUM;
                put(x, 0, z, top);
                put(x, -1, z, top == Material.CRIMSON_NYLIUM ? Material.NETHERRACK : Material.DIRT);
                int depth = (int) (Math.pow(1 - t, 0.8) * 22 + noise(x, z, 3, 4) * 5);
                for (int k = 2; k < depth + 2; k++) {
                    Material m = k > depth * 0.6 ? Material.DEEPSLATE : k > 3 ? Material.STONE : Material.DIRT;
                    if (h(x, z * 31 + k, 5) < 0.012) m = Material.SHROOMLIGHT;
                    else if (h(x, k, z) < 0.05 && k > 4) m = Material.AMETHYST_BLOCK;
                    put(x, -k, z, m);
                }
                if (depth > 6 && h(x, z, 77) < 0.06) {
                    int len = (int) (h(x, z, 78) * 5) + 1;
                    for (int k = depth + 2; k < depth + 2 + len; k++) put(x, -k, z, Material.POINTED_DRIPSTONE);
                }
            }
        }
        // walkways
        for (int x = -nr; x <= nr; x++) {
            for (int z = -nr; z <= nr; z++) {
                double d = Math.hypot(x, z);
                if (d >= 13 && d <= 18) put(x, 0, z, d < 14 || d > 17 ? Material.CRIMSON_PLANKS : Material.WARPED_PLANKS);
            }
        }
        for (int ang : new int[] {90, 0, 180, 270}) {
            double a = Math.toRadians(ang);
            for (int s = 0; s < 40; s++) {
                for (int wd = -2; wd <= 2; wd++) {
                    int x = round(s * Math.cos(a) - wd * Math.sin(a)), z = round(s * Math.sin(a) + wd * Math.cos(a));
                    if (!netherInside(x, z, 1) || Math.hypot(x, z) < 6) continue;
                    put(x, 0, z, Math.abs(wd) == 2 ? Material.CRIMSON_PLANKS : Material.WARPED_PLANKS);
                }
            }
        }
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = Math.hypot(x, z);
                if (d <= 12.5) put(x, 0, z, d > 11.5 ? Material.POLISHED_DEEPSLATE : ((int) d) % 3 != 0 ? Material.WARPED_PLANKS : Material.CRIMSON_PLANKS);
            }
        }
        // world tree
        int trunk = 20;
        for (int y = 1; y <= trunk; y++) {
            double rad = y > 3 ? 3.2 - y * 0.06 : 3.2 + (4 - y) * 0.9;
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    double d = Math.hypot(x + 0.3 * Math.sin(y * 0.4), z);
                    if (d <= rad) put(x, y, z, d > rad - 1.2 ? Material.WARPED_HYPHAE : Material.STRIPPED_WARPED_HYPHAE);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + 0.3;
            for (int s = 3; s < 11; s++) {
                int x = round(s * Math.cos(a)), z = round(s * Math.sin(a)), y = Math.max(1, 3 - s / 3);
                put(x, y, z, Material.WARPED_HYPHAE);
                if (s < 6) put(x, y + 1, z, Material.WARPED_HYPHAE);
            }
        }
        for (int i = 0; i < 5; i++) {
            double a = i * 2 * Math.PI / 5 + 0.5;
            for (int s = 2; s < 9; s++) put(round(s * Math.cos(a)), trunk - 6 + s / 2, round(s * Math.sin(a)), Material.WARPED_HYPHAE);
        }
        int cy = trunk + 1;
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                double d = Math.hypot(x, z);
                double capr = 13 + 1.5 * noise(x, z, 21, 4);
                if (d > capr) continue;
                int top = cy + (int) (6 * Math.pow(Math.cos(Math.min(1, d / capr) * Math.PI / 2), 0.7));
                int bot = cy - 1 + (int) (Math.pow(d / capr, 2) * -4);
                for (int y = bot; y <= top; y++) {
                    boolean shell = y == bot || y == top || d > capr - 1.4;
                    if (!shell) continue;
                    Material m = Material.WARPED_WART_BLOCK;
                    if (y == bot && h(x, z, 31) < 0.18) m = Material.SHROOMLIGHT;
                    if (y == top && h(x, z, 32) < 0.02) m = Material.SHROOMLIGHT;
                    put(x, y, z, m);
                }
                if (capr - 1.5 < d && d <= capr && h(x, z, 40) < 0.35) {
                    int len = 2 + (int) (h(x, z, 41) * 7);
                    for (int i = 1; i < len; i++) {
                        if (get(x, bot - i, z) == null) put(x, bot - i, z, i < len - 1 ? Material.TWISTING_VINES_PLANT : Material.TWISTING_VINES);
                    }
                }
            }
        }
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double dd = Math.hypot(x + 2, z - 1);
                if (dd > 7.5) continue;
                int top = cy + 9 + (int) (4 * Math.cos(Math.min(1, dd / 7.5) * Math.PI / 2));
                for (int y = cy + 6; y <= top; y++) {
                    if (y == top || dd > 6.3 || y == cy + 6) {
                        put(x, y, z, y == cy + 6 && h(x, z, 33) < 0.3 ? Material.SHROOMLIGHT : Material.WARPED_WART_BLOCK);
                    }
                }
            }
        }
        for (int y = cy; y < cy + 7; y++) for (int x = -2; x <= -1; x++) for (int z = 0; z <= 1; z++) put(x, y, z, Material.WARPED_HYPHAE);
        // crimson fungi
        int[][] groves = {{-27, -12, 9, 5}, {26, -15, 8, 4}, {-24, 16, 7, 4}, {28, 13, 10, 5}, {-10, -31, 6, 3}, {12, 30, 6, 3}};
        for (int[] g : groves) if (netherInside(g[0], g[1], 4)) crimsonTree(g[0], g[1], g[2], g[3]);
        // flora
        for (int x = -nr; x <= nr; x++) {
            for (int z = -nr; z <= nr; z++) {
                if (!netherInside(x, z, 1) || get(x, 1, z) != null) continue;
                Material g = get(x, 0, z);
                double r = h(x, z, 50);
                if (g == Material.WARPED_NYLIUM) {
                    if (r < 0.09) put(x, 1, z, Material.WARPED_ROOTS);
                    else if (r < 0.12) put(x, 1, z, Material.NETHER_SPROUTS);
                    else if (r < 0.128) put(x, 1, z, Material.WARPED_FUNGUS);
                } else if (g == Material.CRIMSON_NYLIUM) {
                    if (r < 0.1) put(x, 1, z, Material.CRIMSON_ROOTS);
                    else if (r < 0.11) put(x, 1, z, Material.CRIMSON_FUNGUS);
                } else if (g == Material.MOSS_BLOCK) {
                    if (r < 0.15) put(x, 1, z, Material.MOSS_CARPET);
                    else if (r < 0.2) put(x, 1, z, Material.AZALEA);
                }
            }
        }
        // glow pond
        int px = 22, pz = 0;
        for (int x = px - 7; x <= px + 7; x++) {
            for (int z = pz - 6; z <= pz + 6; z++) {
                double d = Math.hypot((x - px) / 1.2, z - pz);
                if (d <= 5.5) {
                    put(x, 0, z, Material.WATER);
                    put(x, -1, z, Material.WATER);
                    put(x, -2, z, h(x, z, 60) < 0.3 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS);
                    for (int y = 1; y <= 2; y++) remove(x, y, z);
                    if (h(x, z, 61) < 0.08) put(x, 1, z, Material.LILY_PAD);
                } else if (d <= 6.6) {
                    put(x, 0, z, Material.DARK_PRISMARINE);
                    for (int y = 1; y <= 2; y++) remove(x, y, z);
                }
            }
        }
        // lantern posts
        for (int ang = 0; ang < 360; ang += 30) {
            double a = Math.toRadians(ang + 15);
            for (double rr : new double[] {18.5, 31}) {
                int x = round(rr * Math.cos(a)), z = round(rr * Math.sin(a));
                if (!netherInside(x, z, 2)) continue;
                for (int y = 1; y <= 3; y++) put(x, y, z, Material.WARPED_FENCE);
                put(x, 4, z, Material.CRIMSON_PLANKS);
                put(x, 5, z, Material.END_ROD);
                put(x + 1, 4, z, Material.SOUL_LANTERN);
                put(x - 1, 4, z, Material.SOUL_LANTERN);
            }
        }
        // islets and crystals on the far side
        islet(44, 8, 37, 5, true);
        islet(39, 11, -46, 4, false);
        shard(9, 18, 49);
        shard(47, 16, -8);

        Map<Long, Material> grove = new HashMap<>(w);
        w.clear();
        w.putAll(saved);
        for (var e : grove.entrySet()) {
            long key = e.getKey();
            put(Pack.x(key) + NX, Pack.y(key), Pack.z(key), e.getValue());
            if (Pack.y(key) == 0) heights.put(k(Pack.x(key) + NX, Pack.z(key)), 0);
        }
    }

    private void crimsonTree(int cx, int cz, int hgt, int cr) {
        for (int y = 1; y <= hgt; y++) for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) put(cx + x, y, cz + z, Material.CRIMSON_STEM);
        int cy = hgt + 1;
        for (int x = -cr - 1; x <= cr + 2; x++) {
            for (int z = -cr - 1; z <= cr + 2; z++) {
                double d = Math.hypot(x - 0.5, z - 0.5);
                if (d > cr + 0.5) continue;
                int top = cy + (int) (3 * Math.cos(Math.min(1, d / (cr + 0.5)) * Math.PI / 2));
                for (int y = cy - (d > cr - 1 ? 1 : 0); y <= top; y++) {
                    if (y == top || d > cr - 0.8 || y == cy - 1) {
                        put(cx + x, y, cz + z, h(cx + x, cz + z + y, 9) < 0.1 ? Material.SHROOMLIGHT : Material.NETHER_WART_BLOCK);
                    }
                }
                if (d > cr - 1 && h(cx + x, cz + z, 44) < 0.4) {
                    int len = 2 + (int) (h(x, z, cx) * 4);
                    for (int i = 1; i < len; i++) {
                        if (get(cx + x, cy - 1 - i, cz + z) == null) put(cx + x, cy - 1 - i, cz + z, Material.WEEPING_VINES_PLANT);
                    }
                }
            }
        }
    }

    private void islet(int cx, int cy, int cz, int r, boolean tree) {
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.hypot(x, z);
                if (d > r) continue;
                put(cx + x, cy, cz + z, Material.WARPED_NYLIUM);
                for (int i = 1; i < (int) ((r - d) * 1.6) + 2; i++) {
                    put(cx + x, cy - i, cz + z, i == 1 ? Material.NETHERRACK : i % 2 == 1 ? Material.BLACKSTONE : Material.DEEPSLATE);
                }
            }
        }
        if (!tree) return;
        for (int y = 1; y <= 5; y++) put(cx, cy + y, cz, Material.WARPED_STEM);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = Math.hypot(x, z);
                if (d <= 3.2) put(cx + x, cy + 6 + (d < 1.6 ? 1 : 0), cz + z, h(x, z, cx) > 0.12 ? Material.WARPED_WART_BLOCK : Material.SHROOMLIGHT);
            }
        }
    }

    private void shard(int x0, int dy, int z0) {
        for (int y = 0; y <= 6; y++) {
            int wd = y >= 2 && y <= 4 ? 1 : 0;
            for (int dx = -wd; dx <= wd; dx++) put(x0 + dx, dy + y, z0, Material.AMETHYST_BLOCK);
        }
        put(x0, dy + 7, z0, Material.AMETHYST_CLUSTER);
    }

    // waterfall grasslands

    private static final int FZ = -6;

    private static double c1(double z) { return WX - 25 + 3 * Math.sin(z / 7); }

    private static double c2(double z) { return WX - 13 + 2 * Math.sin(z / 6 + 1); }

    private static int hill(int x, int z) {
        if (x < c1(z)) return 24 + (int) (noise(x, z, 71, 6) * 4);
        if (x < c2(z)) return 12 + (int) (noise(x, z, 72, 5) * 2);
        return 0;
    }

    private void waterfall() {
        int wr = 48;
        int poolX = WX - 5, poolZ = FZ;
        double[][] river = new double[80][];
        for (int i = 0; i < 80; i++) {
            double t = i / 79.0;
            river[i] = new double[] {poolX + 4 + t * 20 + 5 * Math.sin(t * 4), poolZ + 4 + t * 48};
        }
        Map<Long, Integer> hs = new HashMap<>();
        for (int x = WX - wr - 6; x <= WX + wr + 6; x++) {
            for (int z = -wr - 6; z <= wr + 6; z++) {
                double d = Math.hypot(x - WX, z), e = outline(x, z, WX, 0, wr, 5);
                if (d > e) continue;
                int hh = hill(x, z);
                if (d > e - 6) hh = Math.min(hh, (int) ((e - d) * 4));
                hs.put(k(x, z), hh);
            }
        }
        for (var en : hs.entrySet()) {
            int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey().longValue();
            int hh = en.getValue();
            double d = Math.hypot(x - WX, z), e = outline(x, z, WX, 0, wr, 5);
            double rd = Double.MAX_VALUE;
            for (double[] p : river) rd = Math.min(rd, Math.hypot(x - p[0], z - p[1]));
            double pd = Math.hypot((x - poolX) / 1.2, z - poolZ);
            boolean water = (rd <= 2.3 || pd <= 7) && hh == 0;
            boolean stream = Math.abs(z - FZ) <= 2 && hh > 0 && x > c1(z) - 9;
            if (water) {
                put(x, 0, z, Material.WATER);
                put(x, -1, z, Material.WATER);
                put(x, -2, z, h(x, z, 7) < 0.6 ? Material.GRAVEL : Material.CLAY);
                if (h(x, z, 8) < 0.05 && pd > 3) put(x, 1, z, Material.LILY_PAD);
            } else {
                for (int y = -1; y <= hh; y++) {
                    Material m;
                    if (y == hh) m = Material.GRASS_BLOCK;
                    else if (y >= hh - 2) m = Material.DIRT;
                    else {
                        double r = h(x, y, z);
                        m = r < 0.7 ? Material.STONE : r < 0.88 ? Material.ANDESITE : Material.MOSSY_COBBLESTONE;
                    }
                    put(x, y, z, m);
                }
                if (hh > 3) {
                    boolean steep = false;
                    for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (hs.getOrDefault(k(x + o[0], z + o[1]), hh) < hh - 2) steep = true;
                    }
                    if (steep) {
                        for (int y = Math.max(0, hh - 12); y <= hh; y++) {
                            Material cur = get(x, y, z);
                            if (cur == Material.GRASS_BLOCK || cur == Material.DIRT) put(x, y, z, h(x, y, z) < 0.6 ? Material.STONE : Material.MOSSY_COBBLESTONE);
                        }
                    }
                }
                if (stream) {
                    put(x, hh, z, Material.WATER);
                    put(x, hh - 1, z, Material.GRAVEL);
                }
            }
            underside(x, z, d / e, 33);
        }
        heights.putAll(hs);
        // two-tier falls
        for (int dz = -2; dz <= 2; dz++) {
            int z = FZ + dz;
            int x1 = WX - 40, x2 = WX - 40;
            while (hs.getOrDefault(k(x1, z), 99) >= 20) x1++;
            while (hs.getOrDefault(k(x2, z), 99) != 0) x2++;
            for (int y = hs.get(k(x1, z)) + 1; y <= hs.get(k(x1 - 1, z)); y++) put(x1, y, z, Material.WATER);
            put(x1 - 1, hs.get(k(x1 - 1, z)), z, Material.WATER);
            for (int y = 1; y <= hs.get(k(x2 - 1, z)); y++) put(x2, y, z, Material.WATER);
            put(x2 - 1, hs.get(k(x2 - 1, z)), z, Material.WATER);
            for (int x = x2 + 1; x < poolX; x++) {
                put(x, 0, z, Material.WATER);
                put(x, -1, z, Material.WATER);
            }
        }
        // off-edge fall
        double[] end = river[river.length - 1];
        for (int wd = -2; wd <= 2; wd++) {
            int xx = round(end[0]) + wd;
            int zz = (int) end[1] - 10;
            while (zz < (int) end[1] + 12 && hs.containsKey(k(xx, zz))) zz++;
            for (int i = -1; i < 30; i++) put(xx, -i, zz, Material.WATER);
            for (int zc = (int) end[1] - 4; zc < zz; zc++) {
                Integer hh = hs.get(k(xx, zc));
                if (hh != null && hh == 0) {
                    put(xx, 0, zc, Material.WATER);
                    put(xx, -1, zc, Material.WATER);
                    remove(xx, 1, zc);
                }
            }
        }
        // trees
        java.util.List<int[]> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 80; i++) {
            int x = WX + round((h(i, 1, 120) - 0.5) * 2 * (wr - 6)), z = round((h(i, 2, 120) - 0.5) * 2 * (wr - 6));
            Integer hh = hs.get(k(x, z));
            if (hh == null || get(x, hh, z) != Material.GRASS_BLOCK) continue;
            double rd = Double.MAX_VALUE;
            for (double[] p : river) rd = Math.min(rd, Math.hypot(x - p[0], z - p[1]));
            if (rd < 6 || (Math.abs(z) <= 4 && x > WX + 10)) continue;
            boolean near = false;
            for (int[] s : spots) if (Math.hypot(x - s[0], z - s[1]) < 7) near = true;
            if (near) continue;
            spots.add(new int[] {x, z});
            tree(x, hh, z, h(i, 3, 120) < 0.45 ? "birch" : "oak", 5 + (int) (h(i, 4, 120) * 3));
        }
        for (var en : hs.entrySet()) {
            int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey().longValue();
            int hh = en.getValue();
            if (get(x, hh, z) != Material.GRASS_BLOCK || get(x, hh + 1, z) != null) continue;
            double r = h(x, z, 130);
            if (r < 0.05) put(x, hh + 1, z, FLOWERS[(int) (h(x, z, 131) * FLOWERS.length) % FLOWERS.length]);
            else if (r < 0.3) put(x, hh + 1, z, r < 0.24 ? Material.SHORT_GRASS : Material.FERN);
        }
        // path and wooden bridge
        for (int x = WX - 2; x <= WX + wr + 1; x++) {
            for (int z = -2; z <= 2; z++) {
                if (get(x, 0, z) == Material.GRASS_BLOCK && hs.getOrDefault(k(x, z), 9) == 0) {
                    put(x, 0, z, Math.abs(z) < 2 ? Material.DIRT_PATH : Material.COARSE_DIRT);
                }
                for (int y = 1; y <= 2; y++) if (isFlora(get(x, y, z))) remove(x, y, z);
            }
        }
        for (int x = WX - 10; x <= WX + 3; x++) {
            for (int z = -2; z <= 2; z++) {
                if (get(x, 0, z) != Material.WATER) continue;
                put(x, 1, z, Material.SPRUCE_PLANKS);
                if (Math.abs(z) == 2) put(x, 2, z, Material.SPRUCE_FENCE);
            }
        }
    }

    // bridges

    private void bridge(int x0, int x1, Material deck, Material rail, Material fence, Material light) {
        for (int x = x0; x <= x1; x++) {
            Material at = get(x, 0, 0);
            if (at != null && at != Material.WATER && get(x, -2, 0) != null) continue;
            int sag = (int) (2.5 * Math.sin(Math.PI * (x - x0) / (x1 - x0)));
            for (int z = -2; z <= 2; z++) {
                put(x, 0, z, Math.abs(z) == 2 ? rail : deck);
                put(x, -1, z, rail);
                heights.put(k(x, z), 0);
            }
            for (int z : new int[] {-2, 2}) put(x, 1, z, fence);
            if ((x - x0) % 6 == 0) for (int z : new int[] {-2, 2}) put(x, 2, z, light);
            if (Math.abs(x - (x0 + x1) / 2) < (x1 - x0) / 2 - 2) for (int i = 2; i < 4 + sag; i++) put(x, -i, 0, rail);
        }
    }

    /** invisible walls around every edge */
    private void walls() {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (var en : new java.util.ArrayList<>(heights.entrySet())) {
            int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey().longValue();
            int hh = en.getValue();
            for (int[] o : around) {
                int nx = x + o[0], nz = z + o[1];
                long nk = k(nx, nz);
                if (heights.containsKey(nk) || !done.add(nk)) continue;
                for (int y = 1; y <= hh + BARRIER_TOP; y++) if (get(nx, y, nz) == null) put(nx, y, nz, Material.BARRIER);
            }
        }
    }

    /** relative blocks, keyed by Pack */
    static Map<Long, Material> build() {
        LobbyDesign d = new LobbyDesign();
        d.spawnMeadow();
        d.nether();
        d.waterfall();
        d.bridge(40, 80, Material.WARPED_PLANKS, Material.CRIMSON_PLANKS, Material.CRIMSON_FENCE, Material.SOUL_LANTERN);
        d.bridge(-80, -40, Material.SPRUCE_PLANKS, Material.STONE_BRICKS, Material.STONE_BRICK_WALL, Material.LANTERN);
        d.walls();
        return d.w;
    }

    public static void place(Map<Long, BlockData> out, Function<Material, BlockData> data, int cx, int cz, int y0) {
        BlockData drip = null;
        for (var e : build().entrySet()) {
            long key = e.getKey();
            Material m = e.getValue();
            BlockData bd;
            if (m == Material.POINTED_DRIPSTONE) {
                if (drip == null) {
                    drip = data.apply(m).clone();
                    if (drip instanceof PointedDripstone p) p.setVerticalDirection(BlockFace.DOWN);
                }
                bd = drip;
            } else {
                bd = data.apply(m);
            }
            out.put(Pack.key(cx + Pack.x(key), y0 + Pack.y(key), cz + Pack.z(key)), bd);
        }
    }
}
