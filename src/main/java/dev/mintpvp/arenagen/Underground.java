package dev.mintpvp.arenagen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** underground terrain */
final class Underground {

    private static final BlockData BEDROCK = Blocks.data(Material.BEDROCK);
    private static final BlockData STONE = Blocks.data(Material.STONE);
    private static final BlockData DEEPSLATE = Blocks.data(Material.DEEPSLATE);
    private static final BlockData GRANITE = Blocks.data(Material.GRANITE);
    private static final BlockData DIORITE = Blocks.data(Material.DIORITE);
    private static final BlockData ANDESITE = Blocks.data(Material.ANDESITE);
    private static final BlockData TUFF = Blocks.data(Material.TUFF);

    /** ore table */
    private static final Object[][] ORES = {
            {Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, -16, 100, 0.030},
            {Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, -60, 56, 0.020},
            {Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, -16, 48, 0.014},
            {Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, -64, 30, 0.005},
            {Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, -64, 16, 0.008},
            {Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, -60, 30, 0.004},
            {Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, -64, 16, 0.0016},
    };
    private static final BlockData[][] ORE_DATA = new BlockData[ORES.length][2];

    static {
        for (int i = 0; i < ORES.length; i++) {
            ORE_DATA[i][0] = Blocks.data((Material) ORES[i][0]);
            ORE_DATA[i][1] = Blocks.data((Material) ORES[i][1]);
        }
    }

    private Underground() {}

    private static double hash3(int x, int y, int z, int s) {
        int h = x * 374761393 + y * 1274126177 + z * 668265263 + s * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** 3D noise */
    private static double n3(double x, double y, double z, int s) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double tx = x - x0, ty = y - y0, tz = z - z0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        tz = tz * tz * (3 - 2 * tz);
        double c000 = hash3(x0, y0, z0, s), c100 = hash3(x0 + 1, y0, z0, s);
        double c010 = hash3(x0, y0 + 1, z0, s), c110 = hash3(x0 + 1, y0 + 1, z0, s);
        double c001 = hash3(x0, y0, z0 + 1, s), c101 = hash3(x0 + 1, y0, z0 + 1, s);
        double c011 = hash3(x0, y0 + 1, z0 + 1, s), c111 = hash3(x0 + 1, y0 + 1, z0 + 1, s);
        double x00 = c000 + (c100 - c000) * tx, x10 = c010 + (c110 - c010) * tx;
        double x01 = c001 + (c101 - c001) * tx, x11 = c011 + (c111 - c011) * tx;
        double y0v = x00 + (x10 - x00) * ty, y1v = x01 + (x11 - x01) * ty;
        return y0v + (y1v - y0v) * tz;
    }

    private static double fbm(double x, double y, double z, int s) {
        return 0.65 * n3(x, y, z, s) + 0.35 * n3(x * 2.1, y * 2.1, z * 2.1, s + 1);
    }

    /** entrance shafts */
    static boolean sinkhole(int ax, int az, double d, double spawnDist) {
        if (d < 14 || spawnDist < 12) return false;
        return ArenaLayout.smooth(ax, az, 77, 13) > 0.86 && ArenaLayout.hash(ax / 3, az / 3, 78) < 0.7;
    }

    static boolean cave(int ax, int y, int az, int surface, boolean sink, int minY) {
        if (y <= minY + 5) return false;
        int depth = surface - y;
        if (sink && depth <= 26) return true;
        if (depth < 5) return false;
        double thr = 0.69 - Math.min(0.07, depth / 1800.0);
        if (fbm(ax * 0.032, y * 0.048, az * 0.032, 11) > thr) return true;
        double a = n3(ax * 0.019, y * 0.03, az * 0.019, 21);
        double b = n3(ax * 0.019, y * 0.03, az * 0.019, 22);
        return Math.abs(a - 0.5) < 0.038 && Math.abs(b - 0.5) < 0.038;
    }

    private static boolean deep(int ax, int y, int az) {
        if (y < 0) return true;
        if (y >= 8) return false;
        return hash3(ax, y, az, 31) < (8 - y) / 8.0 * 0.9;
    }

    /** underground block */
    static BlockData block(int ax, int y, int az, int surface, boolean sink, boolean edge, int minY) {
        if (y == minY) return BEDROCK;
        if (y < minY + 5 && hash3(ax, y, az, 61) < (minY + 5 - y) / 5.0) return BEDROCK;
        if (!edge && cave(ax, y, az, surface, sink, minY)) return null;

        boolean deep = deep(ax, y, az);
        if (y < surface - 6) {
            double r = hash3(ax >> 1, y >> 1, az >> 1, 51);
            if (hash3(ax, y, az, 52) < 0.72) {
                double acc = 0;
                for (int i = 0; i < ORES.length; i++) {
                    if (y < (int) ORES[i][2] || y > (int) ORES[i][3]) continue;
                    acc += (double) ORES[i][4];
                    if (r < acc) return ORE_DATA[i][deep ? 1 : 0];
                }
            }
        }
        if (deep) return n3(ax * 0.09, y * 0.09, az * 0.09, 44) > 0.82 ? TUFF : DEEPSLATE;
        if (n3(ax * 0.09, y * 0.09, az * 0.09, 41) > 0.84) return GRANITE;
        if (n3(ax * 0.09, y * 0.09, az * 0.09, 42) > 0.84) return DIORITE;
        if (n3(ax * 0.09, y * 0.09, az * 0.09, 43) > 0.84) return ANDESITE;
        return STONE;
    }
}
