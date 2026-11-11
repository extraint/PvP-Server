package dev.mintpvp.listeners;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/** Join/quit presentation, kit + scoreboard on join. */
public final class PlayerListener implements Listener {

    private final MintPvP plugin;

    public PlayerListener(MintPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Theme t = plugin.theme();
        plugin.stats().rememberName(p.getUniqueId(), p.getName());

        e.joinMessage(t.raw("+ ", t.primary()).append(t.raw(p.getName(), t.accent())));
        p.sendMessage(t.info("welcome to " + t.serverName() + ", ")
                .append(t.raw(p.getName(), t.primary()))
                .append(t.text("! use /menu to queue, challenge or edit your kit.", t.accent())));
        p.showTitle(net.kyori.adventure.title.Title.title(
                t.text(t.serverName(), t.primary()),
                t.text("fight fresh.", t.secondary())));

        if (plugin.getConfig().getBoolean("spawn.teleport-on-join", true) && plugin.spawn().isSet()) {
            p.teleport(plugin.spawn().get());
        }
        plugin.lobby().give(p);
        if (plugin.getConfig().getBoolean("kit.give-on-join", false)) {
            plugin.kits().give(p);
        }
        plugin.boards().update(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Theme t = plugin.theme();
        e.quitMessage(t.raw("- ", t.muted()).append(t.raw(e.getPlayer().getName(), t.muted())));
    }
}
