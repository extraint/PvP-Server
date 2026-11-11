package dev.mintpvp.util;

import org.bukkit.Material;

/**
 * Rank tiers by Elo, named like the usual PvP tier lists: LT = low tier,
 * HT = high tier, 5 is the bottom and 1 is the top (LT5 < HT5 < LT4 < ... < HT1).
 * Everyone starts at 1000 Elo, which is LT4.
 */
public enum Tier {
    LT5("lt5", Material.COPPER_INGOT, 0),
    HT5("ht5", Material.IRON_INGOT, 850),
    LT4("lt4", Material.GOLD_INGOT, 1000),
    HT4("ht4", Material.LAPIS_LAZULI, 1150),
    LT3("lt3", Material.EMERALD, 1300),
    HT3("ht3", Material.DIAMOND, 1450),
    LT2("lt2", Material.AMETHYST_SHARD, 1600),
    HT2("ht2", Material.NETHERITE_SCRAP, 1750),
    LT1("lt1", Material.NETHERITE_INGOT, 1900),
    HT1("ht1", Material.NETHER_STAR, 2050);

    public final String label;
    public final Material icon;
    public final int minElo;

    Tier(String label, Material icon, int minElo) {
        this.label = label;
        this.icon = icon;
        this.minElo = minElo;
    }

    public static Tier of(int elo) {
        Tier best = LT5;
        for (Tier t : values()) if (elo >= t.minElo) best = t;
        return best;
    }
}
