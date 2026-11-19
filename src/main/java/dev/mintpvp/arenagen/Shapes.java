package dev.mintpvp.arenagen;

import org.bukkit.Axis;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/** cover pieces */
final class Shapes {

    private Shapes() {}

    static void put(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, int x, int y, int z, BlockData d) {
        if (allow.test(x, z)) out.put(Pack.key(x, y, z), d);
    }

    /** column */
    static void column(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                       int x, int z, int h, BlockData d) {
        int y0 = surf.applyAsInt(x, z);
        for (int y = 1; y <= h; y++) put(out, allow, x, y0 + y, z, d);
    }

    static void box(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                    int cx, int cz, int hw, int hd, int h, BlockData d) {
        for (int x = cx - hw; x <= cx + hw; x++) for (int z = cz - hd; z <= cz + hd; z++) column(out, allow, surf, x, z, h, d);
    }

    static void mound(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                      int cx, int cz, double r, int h, BlockData d) {
        int ri = (int) Math.ceil(r);
        for (int x = cx - ri; x <= cx + ri; x++) {
            for (int z = cz - ri; z <= cz + ri; z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd <= r) column(out, allow, surf, x, z, (int) Math.max(1, Math.round(h * (1 - dd / r) + .4)), d);
            }
        }
    }

    /** boulder */
    static void boulder(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                        int cx, int cz, double r, BlockData d) {
        int y0 = surf.applyAsInt(cx, cz);
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                for (int dy = 0; dy <= ri; dy++) {
                    if (dx * dx + dz * dz + dy * dy * 1.6 <= r * r) put(out, allow, cx + dx, y0 + dy, cz + dz, d);
                }
            }
        }
    }

    /** wall run */
    static void wall(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                     int cx, int cz, int len, boolean alongX, int h, int gapEvery, BlockData d) {
        for (int i = 0; i < len; i++) {
            if (gapEvery > 0 && i % gapEvery == gapEvery - 1) continue;
            int x = alongX ? cx + i - len / 2 : cx, z = alongX ? cz : cz + i - len / 2;
            column(out, allow, surf, x, z, h, d);
        }
    }

    /** fallen log */
    static void fallenLog(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                          int cx, int cz, int len, boolean alongX, String logKey) {
        BlockData d = Blocks.log(logKey, alongX ? Axis.X : Axis.Z);
        for (int i = 0; i < len; i++) {
            int x = alongX ? cx + i - len / 2 : cx, z = alongX ? cz : cz + i - len / 2;
            put(out, allow, x, surf.applyAsInt(x, z) + 1, z, d);
        }
    }

    /** tree: shaped by its kind, with its own random height and bends (r and h are no longer used) */
    static void tree(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                     String trunk, String leaves, int r, int h, int tx, int tz) {
        VanillaTrees.grow(out, allow, surf, trunk, leaves, tx, tz);
    }
}
