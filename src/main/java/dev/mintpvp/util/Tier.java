package dev.mintpvp.util;

import org.bukkit.Material;

/** tiers */
public enum Tier {
    LT5("lt5", Material.COPPER_INGOT, 3),
    HT5("ht5", Material.IRON_INGOT, 8),
    LT4("lt4", Material.GOLD_INGOT, 12),
    HT4("ht4", Material.LAPIS_LAZULI, 20),
    LT3("lt3", Material.EMERALD, 28),
    HT3("ht3", Material.DIAMOND, 38),
    LT2("lt2", Material.AMETHYST_SHARD, 50),
    HT2("ht2", Material.NETHERITE_SCRAP, 65),
    LT1("lt1", Material.NETHERITE_INGOT, 92),
    HT1("ht1", Material.NETHER_STAR, 110);

    public final String label;
    public final Material icon;
    public final int points;

    Tier(String label, Material icon, int points) {
        this.label = label;
        this.icon = icon;
        this.points = points;
    }

    /** tier by index */
    public static Tier at(int index) {
        return index < 0 || index >= values().length ? null : values()[index];
    }

    /** kit count */
    public static java.util.function.IntSupplier kitCount = () -> 1;

    /** overall title */
    public static String overall(int totalPoints) {
        double avg = totalPoints / (double) Math.max(1, kitCount.getAsInt());
        if (avg >= 105) return "grandmaster";
        if (avg >= 80) return "master";
        if (avg >= 50) return "ace";
        if (avg >= 28) return "specialist";
        if (avg >= 16) return "cadet";
        if (avg >= 8) return "novice";
        if (avg >= 3) return "rookie";
        return "unranked";
    }

    /** title label */
    public static String overallLabel(int points) {
        return overall(points) + " · " + points + " pts";
    }

    public static String name(int index) {
        Tier t = at(index);
        return t == null ? "unranked" : t.label;
    }
}
