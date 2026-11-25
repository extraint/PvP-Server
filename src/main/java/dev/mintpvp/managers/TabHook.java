package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Tier;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Gives the TAB plugin our placeholders, so TAB (which owns nametags and the tab list on servers that run it)
 * can show the tier tag and team colour next to the LuckPerms rank:
 *   %mintpvp_tag%        coloured bold tier and a space, e.g. HT3 (empty when unranked)
 *   %mintpvp_tier%       plain tier, e.g. HT3
 *   %mintpvp_teamcolor%  &9 for the blue team or &c for the red team in a party team match, else nothing
 * Uses reflection so the plugin still builds and runs without TAB.
 */
public final class TabHook {

    private final MintPvP plugin;
    private boolean hooked;
    private org.bukkit.scheduler.BukkitTask task;
    private static final String PREFIX = "%mintpvp_tag%";
    private static final String SUFFIX = "%mintpvp_teamcolor%";

    public TabHook(MintPvP plugin) {
        this.plugin = plugin;
    }

    public boolean hooked() {
        return hooked;
    }

    public static boolean tabPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("TAB");
    }

    public void start() {
        if (!tabPresent()) return;
        if (register()) {
            listenForReloads();
            startPrefixes();
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** put the tier tag into every nametag and tab entry through TAB */
    private void startPrefixes() {
        if (task != null) task.cancel();
        if (!plugin.getConfig().getBoolean("tags.tab-prefix", true)) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var p : Bukkit.getOnlinePlayers()) applyPrefix(p.getUniqueId());
        }, 20L, 40L);
    }

    public void refresh(UUID id) {
        if (hooked) Bukkit.getScheduler().runTaskLater(plugin, () -> applyPrefix(id), 10L);
    }

    private void applyPrefix(UUID id) {
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object tp = apiClass.getMethod("getPlayer", UUID.class).invoke(api, id);
            if (tp == null) return;
            Object ntm = apiClass.getMethod("getNameTagManager").invoke(api);
            nametagActive = ntm != null;
            status = ntm == null ? "TAB's nametag feature is off, using scoreboard teams" : "tag pushed through TAB";
            if (!logged) plugin.getLogger().info("TAB nametag tier prefix: " + status);
            logged = true;
            apply(ntm, "me.neznamy.tab.api.nametag.NameTagManager", tp, true);
            apply(apiClass.getMethod("getTabListFormatManager").invoke(api), "me.neznamy.tab.api.tablist.TabListFormatManager", tp, false);
        } catch (Throwable e) {
            nametagActive = false;
            status = "TAB call failed: " + e;
            if (!warned) plugin.getLogger().warning("Could not set the TAB tier prefix: " + e);
            warned = true;
        }
    }

    private boolean warned;
    private boolean logged;
    private volatile boolean nametagActive;
    private volatile String status = "not run yet";

    /** true when TAB's nametag feature is on and carries our tag */
    public boolean nametagActive() {
        return hooked && nametagActive;
    }

    public String status() {
        return status;
    }

    private void apply(Object mgr, String iface, Object tp, boolean nametag) throws Exception {
        if (mgr == null) return;
        Class<?> tpClass = Class.forName("me.neznamy.tab.api.TabPlayer");
        Class<?> c;
        try {
            c = Class.forName(iface);
        } catch (ClassNotFoundException e) {
            c = mgr.getClass();
        }
        Method get = find(c, "getCustomPrefix", tpClass);
        Method orig = find(c, "getOriginalRawPrefix", tpClass);
        Method set = find(c, "setPrefix", tpClass, String.class);
        if (set == null) return;
        String base = orig == null ? "" : String.valueOf(orig.invoke(mgr, tp));
        if (base == null || base.equals("null")) base = "";
        String want = PREFIX + base + (nametag ? SUFFIX : "");
        Object have = get == null ? null : get.invoke(mgr, tp);
        if (want.equals(have)) return;
        if (base.contains(PREFIX)) return;
        set.invoke(mgr, tp, want);
    }

    private static Method find(Class<?> c, String name, Class<?>... args) {
        try {
            return c.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            for (Class<?> i : c.getInterfaces()) {
                Method m = find(i, name, args);
                if (m != null) return m;
            }
            return null;
        }
    }

    private boolean register() {
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object pm = apiClass.getMethod("getPlaceholderManager").invoke(api);
            Class<?> pmClass = Class.forName("me.neznamy.tab.api.placeholder.PlaceholderManager");
            Method reg = pmClass.getMethod("registerPlayerPlaceholder", String.class, int.class, Function.class);
            Method uuid = Class.forName("me.neznamy.tab.api.TabPlayer").getMethod("getUniqueId");
            reg.invoke(pm, "%mintpvp_tag%", 1000, (Function<Object, Object>) tp -> tag(id(uuid, tp)));
            reg.invoke(pm, "%mintpvp_tier%", 1000, (Function<Object, Object>) tp -> tier(id(uuid, tp)));
            reg.invoke(pm, "%mintpvp_teamcolor%", 1000, (Function<Object, Object>) tp -> teamColor(id(uuid, tp)));
            hooked = true;
            plugin.getLogger().info("TAB found: registered %mintpvp_tag%, %mintpvp_tier% and %mintpvp_teamcolor%.");
            return true;
        } catch (Throwable e) {
            plugin.getLogger().warning("TAB found, but its placeholders could not be registered: " + e);
            return false;
        }
    }

    /** TAB forgets plugin placeholders on /tab reload, so register them again whenever TAB loads */
    private void listenForReloads() {
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object bus = apiClass.getMethod("getEventBus").invoke(api);
            if (bus == null) return;
            Class<?> loadEvent = Class.forName("me.neznamy.tab.api.event.plugin.TabLoadEvent");
            Class<?> handler = Class.forName("me.neznamy.tab.api.event.EventHandler");
            Object h = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[] {handler}, (proxy, m, args) -> {
                if (m.getName().equals("handle")) Bukkit.getScheduler().runTask(plugin, () -> { register(); startPrefixes(); });
                if (m.getName().equals("equals")) return proxy == args[0];
                if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (m.getName().equals("toString")) return "MintPvP TAB reload hook";
                return null;
            });
            Class.forName("me.neznamy.tab.api.event.EventBus").getMethod("register", Class.class, handler).invoke(bus, loadEvent, h);
        } catch (Throwable e) {
            plugin.getLogger().info("TAB reload hook not available (" + e.getClass().getSimpleName() + "); run /mintpvp reload after /tab reload.");
        }
    }

    public void reregister() {
        if (tabPresent()) register();
    }

    private static UUID id(Method uuid, Object tabPlayer) {
        try {
            return (UUID) uuid.invoke(tabPlayer);
        } catch (Exception e) {
            return null;
        }
    }

    /** the tier shown for a player: in a match, their tier in that kit; otherwise their best tier */
    private int tierIndex(UUID id) {
        if (id == null) return -1;
        var st = plugin.stats().peek(id);
        if (st == null) return -1;
        var duel = plugin.duels().duelOf(id);
        return duel != null ? st.tierIndex(duel.kit.id) : st.bestTier();
    }

    private String tag(UUID id) {
        int idx = tierIndex(id);
        if (idx < 0) return id == null ? "" : "#AAAAAA&lUNRANKED&r ";
        Tier t = Tier.at(idx);
        String hex = String.format("#%06X", t.color.value());
        return hex + "&l" + (idx == Tier.values().length - 1 ? "★" : "") + t.label.toUpperCase(Locale.ROOT) + "&r ";
    }

    private String tier(UUID id) {
        int idx = tierIndex(id);
        return idx < 0 ? "UNRANKED" : Tier.name(idx).toUpperCase(Locale.ROOT);
    }

    private String teamColor(UUID id) {
        if (id == null) return "";
        var duel = plugin.duels().duelOf(id);
        if (duel == null || duel.mode != DuelManager.Mode.PARTY) return "";
        var p = Bukkit.getPlayer(id);
        if (p == null || duel.teamOf(p) < 0) return "";
        return duel.teamOf(p) == 1 ? "&9" : "&c";
    }
}
