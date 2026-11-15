package dev.mintpvp.arenagen;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.Map;
import java.util.function.Function;

/**
 * The mint lobby: a round floating quartz platform with a mint spawn disc, wedge-pattern floor,
 * lime rings, eight lantern pillars, four cherry trees and a rail with an invisible barrier above it.
 */
public final class LobbyDesign {

    /** Platform radius (the rail sits on the outer 1.5 blocks). */
    public static final int R = 26;
    public static final int BARRIER_TOP = 40;

    private LobbyDesign() {}

    private static Material top(double d, double ang) {
        if (d <= 2.5) return Material.EMERALD_BLOCK;
        if (d <= 3.5) return Material.SEA_LANTERN;
        if (d <= 9) return Material.SMOOTH_QUARTZ;
        if (d <= 10.5) return Material.LIME_CONCRETE;
        if (d <= 19) {
            int sector = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * 16);
            return sector % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
        }
        if (d <= 20.5) return Material.LIME_CONCRETE;
        if (d <= 24.5) return Material.SMOOTH_QUARTZ;
        return Material.LIME_CONCRETE;
    }

    public static void place(Map<Long, BlockData> out, Function<Material, BlockData> data, int cx, int cz, int y0) {
        BlockData air = data.apply(Material.AIR);
        int reach = R + 2;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > reach + .5) continue;
                int x = cx + dx, z = cz + dz;
                for (int y = y0 - 14; y <= y0 + 14; y++) out.put(ArenaGenerator.key(x, y, z), air);
                if (d > R) continue;

                double ang = Math.atan2(dz, dx);
                out.put(ArenaGenerator.key(x, y0, z), data.apply(top(d, ang)));
                out.put(ArenaGenerator.key(x, y0 - 1, z), data.apply(Material.QUARTZ_BLOCK));
                out.put(ArenaGenerator.key(x, y0 - 2, z), data.apply(Material.SMOOTH_QUARTZ));
                // tapering underside, so it reads as a floating island
                for (int k = 1; k <= 9; k++) {
                    if (d <= R - 2.6 * k) {
                        out.put(ArenaGenerator.key(x, y0 - 2 - k, z), data.apply(k % 2 == 0 ? Material.QUARTZ_BRICKS : Material.WHITE_CONCRETE));
                    }
                }
                // rail with lanterns every 45 degrees, and the barrier above it
                if (d > 24.5) {
                    out.put(ArenaGenerator.key(x, y0 + 1, z), data.apply(Material.QUARTZ_BRICKS));
                    out.put(ArenaGenerator.key(x, y0 + 2, z), data.apply(Material.QUARTZ_BRICKS));
                    double deg = Math.toDegrees(ang);
                    boolean lantern = d > 25 && Math.abs(((deg % 45) + 45) % 45) < 2.2;
                    out.put(ArenaGenerator.key(x, y0 + 3, z), data.apply(lantern ? Material.SEA_LANTERN : Material.BARRIER));
                    for (int y = y0 + 4; y <= y0 + BARRIER_TOP; y++) out.put(ArenaGenerator.key(x, y, z), data.apply(Material.BARRIER));
                }
            }
        }

        // eight pillars topped with sea lanterns
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + k * 45);
            int px = cx + (int) Math.round(15 * Math.cos(a)), pz = cz + (int) Math.round(15 * Math.sin(a));
            out.put(ArenaGenerator.key(px, y0 + 1, pz), data.apply(Material.CHISELED_QUARTZ_BLOCK));
            BlockData pillar = data.apply(Material.QUARTZ_PILLAR).clone();
            if (pillar instanceof Orientable o) o.setAxis(Axis.Y);
            for (int y = 2; y <= 6; y++) out.put(ArenaGenerator.key(px, y0 + y, pz), pillar);
            out.put(ArenaGenerator.key(px, y0 + 7, pz), data.apply(Material.CHISELED_QUARTZ_BLOCK));
            out.put(ArenaGenerator.key(px, y0 + 8, pz), data.apply(Material.SEA_LANTERN));
        }

        // four cherry trees on the diagonals
        BlockData log = data.apply(Material.CHERRY_LOG);
        BlockData leaves = data.apply(Material.CHERRY_LEAVES);
        for (int sx : new int[] {1, -1}) {
            for (int sz : new int[] {1, -1}) {
                int tx = cx + sx * 15, tz = cz + sz * 15;
                for (int y = 1; y <= 5; y++) out.put(ArenaGenerator.key(tx, y0 + y, tz), log);
                for (int ly = 4; ly <= 7; ly++) {
                    int r = ly == 4 || ly == 7 ? 1 : 2;
                    for (int x = -r; x <= r; x++) {
                        for (int z = -r; z <= r; z++) {
                            if (Math.hypot(x, z) > r + .35) continue;
                            if (x == 0 && z == 0 && ly <= 5) continue;
                            out.put(ArenaGenerator.key(tx + x, y0 + ly, tz + z), leaves);
                        }
                    }
                }
            }
        }
    }
}
