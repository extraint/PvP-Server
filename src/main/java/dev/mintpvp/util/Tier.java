package dev.mintpvp.util;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/** tiers */
public enum Tier {
    LT5("lt5", Material.COPPER_INGOT, 1, C.LIGHTGREEN),
    HT5("ht5", Material.IRON_INGOT, 2, C.LIGHTGREEN),
    LT4("lt4", Material.GOLD_INGOT, 4, C.GREYBLUE),
    HT4("ht4", Material.LAPIS_LAZULI, 6, C.GREYBLUE),
    LT3("lt3", Material.EMERALD, 10, C.BRONZE),
    HT3("ht3", Material.DIAMOND, 15, C.BRONZE),
    LT2("lt2", Material.AMETHYST_SHARD, 25, C.SILVER),
    HT2("ht2", Material.NETHERITE_SCRAP, 35, C.SILVER),
    LT1("lt1", Material.NETHERITE_INGOT, 60, C.GOLD),
    HT1("ht1", Material.NETHER_STAR, 80, C.CHAMP);

    public final String label;
    public final Material icon;
    public final int points;
    public final TextColor color;

    /** tier colours (HT1 blue is taken from the champion banner) */
    private static final class C {
        static final TextColor LIGHTGREEN = TextColor.color(0x7CFC7C);
        static final TextColor GREYBLUE = TextColor.color(0x8FA9C4);
        static final TextColor BRONZE = TextColor.color(0xCD7F32);
        static final TextColor SILVER = TextColor.color(0xC8CDD2);
        static final TextColor GOLD = TextColor.color(0xFFD23F);
        static final TextColor CHAMP = TextColor.color(0x3DB5F0);
    }

    Tier(String label, Material icon, int points, TextColor color) {
        this.label = label;
        this.icon = icon;
        this.points = points;
        this.color = color;
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
        if (avg >= 80) return "grandmaster"; // ht1 in every kit
        if (avg >= 60) return "master"; // lt1 in every kit
        if (avg >= 25) return "ace"; // lt2 in every kit
        if (avg >= 15) return "specialist"; // ht3 in every kit
        if (avg >= 10) return "cadet"; // lt3 in every kit
        if (avg >= 4) return "novice"; // lt4 in every kit
        if (avg >= 1) return "rookie"; // lt5 in every kit
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
