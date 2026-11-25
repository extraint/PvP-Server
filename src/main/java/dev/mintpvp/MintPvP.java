package dev.mintpvp;

import dev.mintpvp.arenagen.ArenaGenerator;
import dev.mintpvp.commands.DuelCommands;
import dev.mintpvp.commands.KitCommands;
import dev.mintpvp.commands.PartyCommands;
import dev.mintpvp.commands.MintCommands;
import dev.mintpvp.commands.QueueCommands;
import dev.mintpvp.commands.SpawnCommands;
import dev.mintpvp.listeners.LobbyListener;
import dev.mintpvp.managers.LobbyManager;
import dev.mintpvp.managers.SpawnManager;
import dev.mintpvp.gui.GuiManager;
import dev.mintpvp.listeners.ArenaBuildListener;
import dev.mintpvp.listeners.DuelListener;
import dev.mintpvp.managers.ArenaManager;
import dev.mintpvp.managers.DuelManager;
import dev.mintpvp.listeners.CombatListener;
import dev.mintpvp.listeners.PlayerListener;
import dev.mintpvp.managers.CombatManager;
import dev.mintpvp.managers.KitManager;
import dev.mintpvp.managers.PartyManager;
import dev.mintpvp.managers.QueueManager;
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
    private dev.mintpvp.managers.TabHook tab;
    private ArenaManager arenas;
    private ArenaGenerator generator;
    private DuelManager duels;
    private QueueManager queue;
    private PartyManager parties;
    private SpawnManager spawn;
    private LobbyManager lobby;
    private GuiManager guis;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyConfig();

        stats = new StatsManager(this);
        stats.start();
        getServer().getScheduler().runTaskTimer(this, () -> stats.decayAll(), 200L, 72000L);
        kits = new KitManager(this);
        dev.mintpvp.util.Tier.kitCount = () -> kits.all().size();
        boards = new ScoreboardManager(this);
        arenas = new ArenaManager(this);
        generator = new ArenaGenerator(this);
        duels = new DuelManager(this);
        queue = new QueueManager(this);
        parties = new PartyManager(this);
        spawn = new SpawnManager(this);
        lobby = new LobbyManager(this);
        guis = new GuiManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new DuelListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaBuildListener(this), this);

        MintCommands commands = new MintCommands(this);
        for (String name : new String[] {"mintpvp", "stats", "leaderboard", "tiers", "region", "eu", "na"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        DuelCommands duelCommands = new DuelCommands(this);
        for (String name : new String[] {"duel", "arena", "spectate", "challenge", "leave"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(duelCommands);
                cmd.setTabCompleter(duelCommands);
            }
        }

        KitCommands kitCommands = new KitCommands(this);
        var kitCmd = getCommand("kit");
        if (kitCmd != null) {
            kitCmd.setExecutor(kitCommands);
            kitCmd.setTabCompleter(kitCommands);
        }
        QueueCommands queueCommands = new QueueCommands(this);
        for (String name : new String[] {"queue", "menu"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(queueCommands);
                cmd.setTabCompleter(queueCommands);
            }
        }

        SpawnCommands spawnCommands = new SpawnCommands(this);
        for (String name : new String[] {"spawn", "setspawn", "build"}) {
            var cmd = getCommand(name);
            if (cmd != null) cmd.setExecutor(spawnCommands);
        }

        PartyCommands partyCommands = new PartyCommands(this);
        for (String name : new String[] {"party", "pc"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(partyCommands);
                cmd.setTabCompleter(partyCommands);
            }
        }
        boards.start();
        tab = new dev.mintpvp.managers.TabHook(this);
        getServer().getScheduler().runTask(this, () -> tab.start()); // after TAB has enabled
        queue.start();
        generator.start();
        // autosave stats
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> stats.save(), 6000L, 6000L);
        getLogger().info("ᴍɪɴᴛ ᴘᴠᴘ enabled.");
    }

    @Override
    public void onDisable() {
        if (generator != null) generator.stop();
        if (guis != null) guis.shutdown();
        if (tab != null) tab.stop();
        if (queue != null) queue.stop();
        if (duels != null) duels.shutdown();
        if (kits != null) kits.saveLayouts();
        if (boards != null) boards.stop();
        if (stats != null) {
            stats.save();
            stats.shutdown();
        }
    }

    /** reload config */
    public void applyConfig() {
        theme.load(getConfig());
        combat.setDurationSeconds(getConfig().getInt("combat.tag-seconds", 15));
    }

    public Theme theme() { return theme; }
    public CombatManager combat() { return combat; }
    public StatsManager stats() { return stats; }
    public KitManager kits() { return kits; }
    public ScoreboardManager boards() { return boards; }
    public dev.mintpvp.managers.TabHook tab() { return tab; }
    public ArenaManager arenas() { return arenas; }
    public ArenaGenerator generator() { return generator; }
    public DuelManager duels() { return duels; }
    public QueueManager queue() { return queue; }
    public PartyManager parties() { return parties; }
    public SpawnManager spawn() { return spawn; }
    public LobbyManager lobby() { return lobby; }
}
