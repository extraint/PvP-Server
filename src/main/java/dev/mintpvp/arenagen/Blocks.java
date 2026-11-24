package dev.mintpvp.arenagen;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** block lookup */
public final class Blocks {

    private static final Map<String, Material> MAT = new HashMap<>();
    private static final Map<Material, BlockData> CACHE = new ConcurrentHashMap<>();

    static {
        MAT.put("grass", Material.GRASS_BLOCK);
        MAT.put("mud", Material.MUD);
        MAT.put("redsand", Material.RED_SAND);
        MAT.put("terracotta", Material.TERRACOTTA);
        MAT.put("orangeterra", Material.ORANGE_TERRACOTTA);
        MAT.put("yellowterra", Material.YELLOW_TERRACOTTA);
        MAT.put("junglelog", Material.JUNGLE_LOG);
        MAT.put("jungleleaves", Material.JUNGLE_LEAVES);
        MAT.put("moss", Material.MOSS_BLOCK);
        MAT.put("acacialog", Material.ACACIA_LOG);
        MAT.put("acacialeaves", Material.ACACIA_LEAVES);
        MAT.put("nylium", Material.CRIMSON_NYLIUM);
        MAT.put("netherrack", Material.NETHERRACK);
        MAT.put("crimsonstem", Material.CRIMSON_STEM);
        MAT.put("wartblock", Material.NETHER_WART_BLOCK);
        MAT.put("gravel", Material.GRAVEL);
        MAT.put("snowspruce", Material.SPRUCE_LOG);
        MAT.put("stone", Material.STONE);
        MAT.put("packedmud", Material.PACKED_MUD);
        MAT.put("mudbricks", Material.MUD_BRICKS);
        MAT.put("coarse", Material.COARSE_DIRT);
        MAT.put("path", Material.DIRT_PATH);
        MAT.put("podzol", Material.PODZOL);
        MAT.put("oaklog", Material.OAK_LOG);
        MAT.put("oakleaves", Material.OAK_LEAVES);
        MAT.put("hay", Material.HAY_BLOCK);
        MAT.put("cobble", Material.MOSSY_COBBLESTONE);
        MAT.put("sand", Material.SAND);
        MAT.put("smooth", Material.SMOOTH_SANDSTONE);
        MAT.put("cut", Material.CUT_SANDSTONE);
        MAT.put("chiseled", Material.CHISELED_SANDSTONE);
        MAT.put("pillar", Material.SANDSTONE);
        MAT.put("dune", Material.SAND);
        MAT.put("poplarlog", Material.POPLAR_LOG);
        MAT.put("poplaryellow", Material.YELLOW_POPLAR_LEAVES);
        MAT.put("poplarorange", Material.ORANGE_POPLAR_LEAVES);
        MAT.put("poplarred", Material.RED_POPLAR_LEAVES);
        MAT.put("fallen", Material.POPLAR_LOG);
        MAT.put("snow", Material.SNOW_BLOCK);
        MAT.put("packedice", Material.PACKED_ICE);
        MAT.put("ice", Material.ICE);
        MAT.put("sprucelog", Material.SPRUCE_LOG);
        MAT.put("spruce", Material.SPRUCE_LEAVES);
        MAT.put("drift", Material.SNOW_BLOCK);
        MAT.put("spike", Material.PACKED_ICE);
        MAT.put("cherrylog", Material.CHERRY_LOG);
        MAT.put("cherry", Material.CHERRY_LEAVES);
        MAT.put("stonebrick", Material.MOSSY_STONE_BRICKS);
        MAT.put("gardenwall", Material.STONE_BRICKS);
        MAT.put("pad", Material.LIME_CONCRETE);
        MAT.put("spawn", Material.EMERALD_BLOCK);
        MAT.put("inlay", Material.LIME_TERRACOTTA);
    }

    private Blocks() {}

    public static BlockData data(Material m) {
        return CACHE.computeIfAbsent(m, k -> {
            BlockData d = k.createBlockData();
            if (d instanceof Leaves l) l.setPersistent(true);
            return d;
        });
    }

    private static final Map<String, BlockData> SPECIAL = new java.util.concurrent.ConcurrentHashMap<>();
    private static final BlockData[] SNOW = new BlockData[9];

    /** a snow layer, 1 to 8 high */
    public static synchronized BlockData snow(int layers) {
        int n = Math.max(1, Math.min(8, layers));
        if (SNOW[n] == null) SNOW[n] = Bukkit.createBlockData("minecraft:snow[layers=" + n + "]");
        return SNOW[n];
    }

    public static BlockData data(String key) {
        if (key.equals("snowgrass")) return SPECIAL.computeIfAbsent(key, k -> Bukkit.createBlockData("minecraft:grass_block[snowy=true]"));
        if (key.equals("snowpodzol")) return SPECIAL.computeIfAbsent(key, k -> Bukkit.createBlockData("minecraft:podzol[snowy=true]"));
        Material m = MAT.get(key);
        if (m == null) throw new IllegalStateException("No block for '" + key + "'");
        return data(m);
    }

    /** log with axis */
    public static BlockData log(String key, Axis axis) {
        BlockData d = data(key).clone();
        if (d instanceof Orientable o) o.setAxis(axis);
        return d;
    }

    public static BlockData axis(Material m, Axis axis) {
        BlockData d = data(m).clone();
        if (d instanceof Orientable o) o.setAxis(axis);
        return d;
    }

    /** parse block state */
    static BlockData parse(String s) {
        return Bukkit.createBlockData(s);
    }
}
