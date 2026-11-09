package dev.mintpvp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;

/** Mint colour palette + small-caps message helpers. */
public final class Theme {

    private TextColor primary = TextColor.fromHexString("#98F5C0");
    private TextColor secondary = TextColor.fromHexString("#5FD8A0");
    private TextColor accent = TextColor.fromHexString("#E6FFF0");
    private TextColor muted = TextColor.fromHexString("#7FA896");
    private String serverName = "Mint PvP";

    public void load(FileConfiguration cfg) {
        primary = color(cfg.getString("theme.primary"), primary);
        secondary = color(cfg.getString("theme.secondary"), secondary);
        accent = color(cfg.getString("theme.accent"), accent);
        muted = color(cfg.getString("theme.muted"), muted);
        serverName = cfg.getString("server-name", "Mint PvP");
    }

    private static TextColor color(String hex, TextColor fallback) {
        if (hex == null) return fallback;
        TextColor c = TextColor.fromHexString(hex);
        return c != null ? c : fallback;
    }

    public TextColor primary() { return primary; }
    public TextColor secondary() { return secondary; }
    public TextColor accent() { return accent; }
    public TextColor muted() { return muted; }
    public String serverName() { return serverName; }

    /** Small-caps text in a given colour. */
    public Component text(String s, TextColor c) {
        return Component.text(SmallCaps.of(s), c);
    }

    /** Raw text (e.g. player names) in a colour, no small caps. */
    public Component raw(String s, TextColor c) {
        return Component.text(s, c);
    }

    public Component prefix() {
        return Component.text(SmallCaps.of(serverName), primary)
                .append(Component.text(" » ", muted));
    }

    /** Prefixed message in the accent colour. */
    public Component info(String s) {
        return prefix().append(text(s, accent));
    }

    /** Prefixed message from components. */
    public Component info(Component body) {
        return prefix().append(body);
    }

    public Component error(String s) {
        return prefix().append(text(s, TextColor.fromHexString("#FF8A8A")));
    }

    /** "label: value" line used by stats and help output. */
    public Component line(String label, String value) {
        return text(label + ": ", secondary).append(Component.text(value, accent));
    }
}
