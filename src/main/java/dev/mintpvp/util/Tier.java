package dev.mintpvp.util;

import org.bukkit.Material;

/** Rank tiers by Elo. Everyone starts at 1000 (silver). */
public enum Tier {
    BRONZE("bronze", Material.COPPER_INGOT, 0),
    SILVER("silver", Material.IRON_INGOT, 900),
    GOLD("gold", Material.GOLD_INGOT, 1100),
    PLATINUM("platinum", Material.EMERALD, 1300),
    DIAMOND("diamond", Material.DIAMOND, 1500),
    MINT("mint master", Material.NETHER_STAR, 1700);

    public final String label;
    public final Material icon;
    public final int minElo;

    Tier(String label, Material icon, int minElo) {
        this.label = label;
        this.icon = icon;
        this.minElo = minElo;
    }

    public static Tier of(int elo) {
        Tier best = BRONZE;
        for (Tier t : values()) if (elo >= t.minElo) best = t;
        return best;
    }
}
