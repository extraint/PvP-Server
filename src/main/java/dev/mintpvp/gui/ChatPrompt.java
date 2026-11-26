package dev.mintpvp.gui;

import dev.mintpvp.MintPvP;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** one-line chat input */
public final class ChatPrompt implements Listener {

    private final MintPvP plugin;
    private final Map<UUID, Consumer<String>> waiting = new HashMap<>();

    public ChatPrompt(MintPvP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** ask for a line */
    public void ask(Player p, String question, Consumer<String> done) {
        p.closeInventory();
        waiting.put(p.getUniqueId(), done);
        var t = plugin.theme();
        p.sendMessage(t.info(question));
        p.sendMessage(t.text("type it in chat, or type cancel.", t.muted()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = waiting.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(text.equalsIgnoreCase("cancel") ? null : text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        waiting.remove(e.getPlayer().getUniqueId());
    }
}
