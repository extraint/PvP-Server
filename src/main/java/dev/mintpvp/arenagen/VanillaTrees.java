package dev.mintpvp.arenagen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * Trees shaped like the ones saplings grow into: each kind has its own trunk and canopy rules, and every tree
 * rolls its own height, bends and gaps. The roll comes from the tree's position (mirrored copies share it), so
 * an arena stays the same every time it is built and both halves match.
 */
final class VanillaTrees {

    private final Map<Long, BlockData> out;
    private final BiPredicate<Integer, Integer> allow;
    private final int y0;
    private final BlockData leaf;
    private final BlockData log;
    private final SplittableRandom r;
    private final int mx, mz;
    private final List<int[]> logs = new ArrayList<>();
    private final int ox, oz;

    private VanillaTrees(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, int y0, BlockData leaf, BlockData log,
                         SplittableRandom r, int mx, int mz, int ox, int oz) {
        this.out = out;
        this.allow = allow;
        this.y0 = y0;
        this.leaf = leaf;
        this.log = log;
        this.r = r;
        this.mx = mx;
        this.mz = mz;
        this.ox = ox;
        this.oz = oz;
    }

    /** grow one tree at tx, tz */
    static void grow(Map<Long, BlockData> out, BiPredicate<Integer, Integer> allow, IntBinaryOperator surf,
                     String trunkKey, String leafKey, int tx, int tz) {
        long seed = (tx * 73856093L) ^ (tz * 19349663L) ^ (trunkKey.hashCode() * 83492791L);
        var t = new VanillaTrees(out, allow, surf.applyAsInt(tx, tz), Blocks.data(leafKey), Blocks.log(trunkKey, org.bukkit.Axis.Y),
                new SplittableRandom(seed), tx < 0 ? -1 : 1, tz < 0 ? -1 : 1, tx, tz);
        switch (trunkKey) {
            case "sprucelog" -> t.spruce();
            case "snowspruce" -> {
                t.spruce();
                t.snowCaps();
            }
            case "junglelog" -> t.jungle();
            case "acacialog" -> t.acacia();
            case "cherrylog" -> t.cherry();
            case "poplarlog" -> t.poplar();
            case "crimsonstem" -> t.fungus();
            default -> t.oak();
        }
        t.finish();
    }

    // placing

    private final java.util.Map<Long, Integer> tops = new java.util.HashMap<>();

    private void leaf(int x, int y, int z) {
        Shapes.put(out, allow, ox + x, y0 + y, oz + z, leaf);
        tops.merge(((long) x << 32) ^ (z & 0xFFFFFFFFL), y, Math::max);
    }

    /** a thin layer of snow on the top of most leaf stacks, like a spruce after a snowfall */
    private void snowCaps() {
        for (var en : tops.entrySet()) {
            if (r.nextInt(100) >= 78) continue;
            int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey();
            Shapes.put(out, allow, ox + x, y0 + en.getValue() + 1, oz + z, Blocks.snow(1));
        }
    }

    private void trunk(int x, int y, int z) {
        logs.add(new int[] {x, y, z});
    }

    private void finish() {
        for (int[] l : logs) Shapes.put(out, allow, ox + l[0], y0 + l[1], oz + l[2], log);
    }

    private void stem(int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) trunk(x, y, z);
    }

    /** a square ring of leaves; each corner is kept with the given chance */
    private void square(int cx, int y, int cz, int rad, double cornerKeep) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad && rad > 0;
                if (corner && r.nextDouble() >= cornerKeep) continue;
                leaf(cx + dx, y, cz + dz);
            }
        }
    }

    /** a round disc of leaves; the rim is thinned by edgeDrop */
    private void disc(int cx, int y, int cz, double rad, double edgeDrop) {
        int ri = (int) Math.ceil(rad);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rad + 0.3) continue;
                if (d > rad - 0.8 && r.nextDouble() < edgeDrop) continue;
                leaf(cx + dx, y, cz + dz);
            }
        }
    }

    private void blob(int cx, int cy, int cz, double rad) {
        int ri = (int) Math.ceil(rad);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.15 + dz * dz);
                    if (d > rad) continue;
                    if (d > rad - 0.9 && r.nextInt(4) == 0) continue;
                    leaf(cx + dx, cy + dy, cz + dz);
                }
            }
        }
    }

    private int[] dir() {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] p = d[r.nextInt(4)];
        return new int[] {p[0] * mx, p[1] * mz}; // mirrored copies lean the mirrored way
    }

    // kinds

    /** oak: 4-6 tall with a rounded crown; now and then a big branching oak */
    private void oak() {
        if (r.nextInt(10) == 0) {
            bigOak();
            return;
        }
        int h = 4 + r.nextInt(3);
        square(0, h - 2, 0, 2, 0.5);
        square(0, h - 1, 0, 2, 0.5);
        square(0, h, 0, 1, 0.5);
        square(0, h + 1, 0, 1, 0);
        stem(0, 0, 1, h);
    }

    private void bigOak() {
        int h = 8 + r.nextInt(5);
        stem(0, 0, 1, h);
        blob(0, h, 0, 3.2 + r.nextDouble());
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int[] d = dir();
            int x = 0, z = 0, y = 4 + r.nextInt(Math.max(1, h - 5));
            int len = 2 + r.nextInt(3);
            for (int k = 0; k < len; k++) {
                x += d[0];
                z += d[1];
                if (k % 2 == 1) y++;
                trunk(x, y, z);
            }
            blob(x, y + 1, z, 2.0 + r.nextDouble());
        }
    }

    /** spruce: a narrow cone of layered rings */
    private void spruce() {
        int h = 6 + r.nextInt(5);
        int maxR = 2 + r.nextInt(2);
        int rad = r.nextInt(2), k = 1, l = 0;
        int low = 2 + r.nextInt(2);
        for (int y = h + 1; y >= low; y--) {
            square(0, y, 0, rad, 0);
            if (rad >= k) {
                rad = l;
                l = 1;
                k = Math.min(k + 1, maxR);
            } else {
                rad++;
            }
        }
        stem(0, 0, 1, h);
    }

    /** jungle: a tall trunk under a small crown, or sometimes a huge 2x2 giant */
    private void jungle() {
        if (r.nextInt(100) < 35) {
            int h = 14 + r.nextInt(9);
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) stem(dx, dz, 1, h);
            disc(0, h - 1, 0, 5.0 + r.nextDouble(), 0.3);
            disc(0, h, 0, 4.2, 0.25);
            disc(0, h + 1, 0, 3.0, 0.2);
            disc(0, h + 2, 0, 1.5, 0);
            int[] d = dir();
            int x = 1, z = 1, y = h / 2;
            for (int k = 0; k < 3; k++) {
                x += d[0];
                z += d[1];
                y++;
                trunk(x, y, z);
            }
            disc(x, y + 1, z, 2.6, 0.2);
            disc(x, y + 2, z, 1.5, 0);
            return;
        }
        int h = 4 + r.nextInt(9);
        square(0, h - 2, 0, 2, 0.5);
        square(0, h - 1, 0, 2, 0.5);
        square(0, h, 0, 1, 0.5);
        square(0, h + 1, 0, 1, 0);
        stem(0, 0, 1, h);
    }

    /** acacia: a trunk that bends, a flat umbrella on top and sometimes a second smaller one */
    private void acacia() {
        int bend = 2 + r.nextInt(3);
        stem(0, 0, 1, bend);
        int[] d = dir();
        int x = 0, z = 0, y = bend;
        int steps = 2 + r.nextInt(3);
        for (int i = 0; i < steps; i++) {
            x += d[0];
            z += d[1];
            y++;
            trunk(x, y, z);
        }
        umbrella(x, y + 1, z, 3);
        if (r.nextInt(100) < 60) {
            int[] d2 = {-d[0], -d[1]};
            if (r.nextBoolean()) d2 = new int[] {d[1], d[0]};
            int bx = 0, bz = 0, by = bend - 1;
            int n = 1 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                bx += d2[0];
                bz += d2[1];
                by++;
                trunk(bx, by, bz);
            }
            umbrella(bx, by + 1, bz, 2);
        }
    }

    private void umbrella(int cx, int y, int cz, int rad) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= rad * rad + 1) leaf(cx + dx, y, cz + dz);
                if (d2 <= (rad - 1) * (rad - 1) + 1) leaf(cx + dx, y + 1, cz + dz);
            }
        }
    }

    /** cherry: a bent trunk with branches under wide, airy crowns */
    private void cherry() {
        int h = 7 + r.nextInt(3);
        stem(0, 0, 1, h);
        crown(0, h, 0, 4.0);
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int[] d = dir();
            int x = 0, z = 0, y = h - 4 + r.nextInt(3);
            int len = 3 + r.nextInt(2);
            for (int k = 0; k < len; k++) {
                x += d[0];
                z += d[1];
                if (k != 1) y++;
                trunk(x, y, z);
            }
            crown(x, y + 1, z, 2.6 + r.nextDouble() * 0.6);
        }
    }

    private void crown(int cx, int cy, int cz, double rad) {
        disc(cx, cy - 1, cz, rad - 1, 0.35);
        disc(cx, cy, cz, rad, 0.3);
        disc(cx, cy + 1, cz, rad - 1, 0.25);
        disc(cx, cy + 2, cz, Math.max(1, rad - 3), 0);
        int ri = (int) Math.ceil(rad);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rad - 1.2 && d <= rad && r.nextInt(100) < 22) leaf(cx + dx, cy - 2, cz + dz);
            }
        }
    }

    /** poplar: a tall slim tree with an oval crown */
    private void poplar() {
        int h = 9 + r.nextInt(5);
        stem(0, 0, 1, h);
        double cy = h * 0.62, ry = h * 0.42, rx = 2.2 + r.nextDouble();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = 3; y <= h + 3; y++) {
                    double e = (dx * dx + dz * dz) / (rx * rx) + (y - cy) * (y - cy) / (ry * ry);
                    if (e > 1) continue;
                    if (e > 0.75 && r.nextInt(100) < 18) continue;
                    leaf(dx, y, dz);
                }
            }
        }
    }

    /** huge crimson fungus: a stem with a flat wart hat, glowing shroomlight and drooping edges */
    private void fungus() {
        int h = 5 + r.nextInt(8);
        stem(0, 0, 1, h);
        disc(0, h + 1, 0, 1.5, 0);
        disc(0, h, 0, 3.4, 0.2);
        disc(0, h - 1, 0, 3.4, 0.35);
        disc(0, h - 2, 0, 2.0, 0.5);
        BlockData glow = Blocks.data(Material.SHROOMLIGHT);
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int dx = r.nextInt(5) - 2, dz = r.nextInt(5) - 2;
            Shapes.put(out, allow, ox + dx, y0 + h - 2, oz + dz, glow);
        }
    }
}
