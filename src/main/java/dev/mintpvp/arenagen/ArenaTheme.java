package dev.mintpvp.arenagen;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.Locale;

/** arena themes */
public enum ArenaTheme {
    PLAINS("plains", "Plains", Material.GRASS_BLOCK, Material.DIRT, true),
    DESERT("desert", "Desert", Material.SAND, Material.SANDSTONE, true),
    POPLAR("poplar", "Poplar Grove", Material.YELLOW_POPLAR_LEAVES, Material.DIRT, true),
    SNOWY("snowy", "Snowy", Material.SNOW_BLOCK, Material.DIRT, true),
    CHERRY("cherry", "Cherry Grove", Material.CHERRY_LEAVES, Material.DIRT, true),
    STADIUM("stadium", "Stadium", Material.MUD, Material.PACKED_MUD),
    BADLANDS("badlands", "Badlands", Material.RED_SAND, Material.TERRACOTTA, true),
    JUNGLE("jungle", "Jungle", Material.JUNGLE_LEAVES, Material.DIRT, true),
    SAVANNA("savanna", "Savanna", Material.ACACIA_LEAVES, Material.DIRT, true),
    CRIMSON("crimson", "Crimson Forest", Material.CRIMSON_NYLIUM, Material.NETHERRACK, true),
    PEAKS("peaks", "Peaks", Material.SNOW_BLOCK, Material.STONE, true);

    public final String id;
    public final String display;
    public final Material icon;
    /** under-floor block */
    public final Material sub;
    /** built by the rolling-terrain generator even at normal size */
    public final boolean procedural;

    ArenaTheme(String id, String display, Material icon, Material sub) {
        this(id, display, icon, sub, false);
    }

    ArenaTheme(String id, String display, Material icon, Material sub, boolean procedural) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.sub = sub;
        this.procedural = procedural;
    }

    public Biome biome() {
        return switch (this) {
            case PLAINS -> Biome.PLAINS;
            case DESERT -> Biome.DESERT;
            case POPLAR -> Biome.FOREST;
            case SNOWY -> Biome.SNOWY_PLAINS;
            case CHERRY -> Biome.CHERRY_GROVE;
            case STADIUM -> Biome.PLAINS;
            case BADLANDS -> Biome.BADLANDS;
            case JUNGLE -> Biome.JUNGLE;
            case SAVANNA -> Biome.SAVANNA;
            case CRIMSON -> Biome.CRIMSON_FOREST;
            case PEAKS -> Biome.JAGGED_PEAKS;
        };
    }

    public static ArenaTheme byId(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT);
        for (ArenaTheme t : values()) if (t.id.equals(k)) return t;
        return null;
    }
}
