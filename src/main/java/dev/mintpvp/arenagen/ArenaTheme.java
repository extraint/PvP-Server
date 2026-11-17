package dev.mintpvp.arenagen;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.Locale;

/** arena themes */
public enum ArenaTheme {
    PLAINS("plains", "Plains", Material.GRASS_BLOCK, Material.DIRT),
    DESERT("desert", "Desert", Material.SAND, Material.SANDSTONE),
    POPLAR("poplar", "Poplar Grove", Material.YELLOW_POPLAR_LEAVES, Material.DIRT),
    SNOWY("snowy", "Snowy", Material.SNOW_BLOCK, Material.DIRT),
    CHERRY("cherry", "Cherry Grove", Material.CHERRY_LEAVES, Material.DIRT);

    public final String id;
    public final String display;
    public final Material icon;
    /** under-floor block */
    public final Material sub;

    ArenaTheme(String id, String display, Material icon, Material sub) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.sub = sub;
    }

    public Biome biome() {
        return switch (this) {
            case PLAINS -> Biome.PLAINS;
            case DESERT -> Biome.DESERT;
            case POPLAR -> Biome.FOREST;
            case SNOWY -> Biome.SNOWY_PLAINS;
            case CHERRY -> Biome.CHERRY_GROVE;
        };
    }

    public static ArenaTheme byId(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT);
        for (ArenaTheme t : values()) if (t.id.equals(k)) return t;
        return null;
    }
}
