package dev.mintpvp;

import dev.mintpvp.commands.DuelCommands;
import dev.mintpvp.commands.MintCommands;
import dev.mintpvp.listeners.DuelListener;
import dev.mintpvp.managers.ArenaManager;
import dev.mintpvp.managers.DuelManager;
import dev.mintpvp.listeners.CombatListener;
import dev.mintpvp.listeners.PlayerListener;
import dev.mintpvp.managers.CombatManager;
import dev.mintpvp.managers.KitManager;
import dev.mintpvp.managers.ScoreboardManager;
import dev.mintpvp.managers.StatsManager;
import dev.mintpvp.util.Theme;
import org.bukkit.plugin.java.JavaPlugin;

public final class MintPvP extends JavaPlugin {

    private final Theme theme = new Theme();
    private final CombatManager combat = new CombatManager();
    private StatsManager stats;
    private KitManager kits;
    private ScoreboardManager boards;
    private ArenaManager arenas;
    private DuelManager duels;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyConfig();

        stats = new StatsManager(this);
        kits = new KitManager(this);
        boards = new ScoreboardManager(this);
        arenas = new ArenaManager(this);
        duels = new DuelManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelListener(this), this);

        MintCommands commands = new MintCommands(this);
        for (String name : new String[] {"mintpvp", "stats", "leaderboard", "kit"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        DuelCommands duelCommands = new DuelCommands(this);
        for (String name : new String[] {"duel", "arena", "spectate"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(duelCommands);
                cmd.setTabCompleter(duelCommands);
            }
        }

        boards.start();
        // autosave stats every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> stats.save(), 6000L, 6000L);
        getLogger().info("ᴍɪɴᴛ ᴘᴠᴘ enabled.");
    }

    @Override
    public void onDisable() {
        if (duels != null) duels.shutdown();
        if (boards != null) boards.stop();
        if (stats != null) stats.save();
    }

    /** (Re)load theme and combat settings from config.yml. */
    public void applyConfig() {
        theme.load(getConfig());
        combat.setDurationSeconds(getConfig().getInt("combat.tag-seconds", 15));
    }

    public Theme theme() { return theme; }
    public CombatManager combat() { return combat; }
    public StatsManager stats() { return stats; }
    public KitManager kits() { return kits; }
    public ScoreboardManager boards() { return boards; }
    public ArenaManager arenas() { return arenas; }
    public DuelManager duels() { return duels; }
}
