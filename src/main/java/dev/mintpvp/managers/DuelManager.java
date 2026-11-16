package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.managers.ArenaManager.Arena;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Matches: 1v1 duels, party-vs-party duels and free-for-alls all run on this engine. A match is a
 * list of teams (a 1v1 is two teams of one, a free-for-all is every player on their own team); a
 * player who dies is eliminated, and the match ends when one team (or nobody) is left standing.
 */
public final class DuelManager {

    public enum State { COUNTDOWN, FIGHTING, ENDING }

    public enum Mode { DUEL, PARTY, FFA }

    public static final class Duel {
        public final Arena arena;
        public final Kit kit;
        public final boolean ranked;
        public final Mode mode;
        /** Everyone who started in the match (including players who died or left). */
        public final List<Player> players = new ArrayList<>();
        final Map<UUID, Integer> team = new HashMap<>();
        final List<String> teamNames = new ArrayList<>();
        /** Players still fighting. */
        public final Set<UUID> alive = new LinkedHashSet<>();
        final Map<UUID, Location> spawns = new HashMap<>();
        /** Players who quit or forfeited and were already put back. */
        final Set<UUID> left = new HashSet<>();
        /** Who last hurt each player (victim -> attacker), so eliminations credit a kill. */
        public final Map<UUID, UUID> lastHitBy = new HashMap<>();
        public State state = State.COUNTDOWN;
        /** First to this many round wins takes the match (1 = a single round). */
        public int firstTo = 1;
        public int round = 1;
        /** Round wins per team. */
        public final int[] score = new int[2];
        /** True while the short pause between two rounds is running. */
        boolean roundBreak;
        /** Set once end() has run, so it can only run once. */
        boolean ended;
        final Map<UUID, Saved> saved = new HashMap<>();
        public final Set<UUID> spectators = new LinkedHashSet<>();
        /** Original state of every block changed during the match, so the arena can be put back. */
        final Map<Long, org.bukkit.block.BlockState> changes = new LinkedHashMap<>();
        /** Chunks holding a plugin ticket because something in them was changed (so they can't unload and save). */
        final Set<Long> heldChunks = new HashSet<>();
        BukkitTask countdown, timeout, sweep, ending;
        /** Set once the result is decided and the match is just winding down (-1 = none/draw). */
        public int pendingWinTeam = -1;
        /** Shown in the chat message (and logged) if the match ends in a draw. */
        public String drawReason = "the time limit was reached";

        Duel(Arena arena, Kit kit, boolean ranked, Mode mode) {
            this.arena = arena;
            this.kit = kit;
            this.ranked = ranked;
            this.mode = mode;
        }

        public int teamOf(Player p) {
            return team.getOrDefault(p.getUniqueId(), -1);
        }

        public boolean sameTeam(Player a, Player b) {
            return teamOf(a) >= 0 && teamOf(a) == teamOf(b);
        }

        public boolean isAlive(Player p) {
            return alive.contains(p.getUniqueId());
        }

        public Location spawnOf(Player p) {
            return spawns.get(p.getUniqueId());
        }

        public Player first() {
            return players.get(0);
        }

        /** The other player of a 1v1 duel. */
        public Player opponentOf(Player p) {
            for (Player q : players) if (!q.getUniqueId().equals(p.getUniqueId())) return q;
            return p;
        }

        /** "first to 3 · round 2 · 1-0", or "" for a single-round duel. */
        public String roundInfo() {
            return firstTo > 1 ? "first to " + firstTo + " · round " + round + " · " + score[0] + "-" + score[1] : "";
        }

        public String teamName(int t) {
            return t >= 0 && t < teamNames.size() ? teamNames.get(t) : "nobody";
        }

        /** "A vs B", "A's party vs B's party", or "free-for-all (6 players)". */
        public String title() {
            return switch (mode) {
                case FFA -> "party free-for-all (" + players.size() + " players)";
                default -> teamNames.get(0) + " vs " + teamNames.get(1);
            };
        }

        Set<Integer> aliveTeams() {
            Set<Integer> out = new LinkedHashSet<>();
            for (UUID id : alive) out.add(team.getOrDefault(id, -1));
            return out;
        }
    }

    private record Saved(Location loc, ItemStack[] contents, GameMode mode, double health,
                         int food, float saturation, int level, float exp,
                         Collection<PotionEffect> effects) {}

    private record SpecSaved(Location loc, GameMode mode) {}

    private record Request(UUID from, String kit, String arena, long expires, boolean ranked, boolean party, int rounds) {}

    private static final int[][] OFFSETS = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};

    private final MintPvP plugin;
    private final Map<UUID, Request> requests = new HashMap<>(); // keyed by target
    private final Map<UUID, Duel> byPlayer = new HashMap<>();
    private final Map<String, Deque<Long>> rankedPairs = new HashMap<>();
    private final Map<UUID, Duel> spectating = new HashMap<>();
    private final Map<UUID, SpecSaved> specSaved = new HashMap<>();

    public DuelManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public boolean inDuel(UUID id) {
        return byPlayer.containsKey(id);
    }

    public Duel duelOf(UUID id) {
        return byPlayer.get(id);
    }

    public Duel duelIn(Arena arena) {
        for (Duel d : new HashSet<>(byPlayer.values())) if (d.arena == arena) return d;
        return null;
    }

    /** Remembers a block's state before a match changes it (first change wins). */
    public void recordBlock(org.bukkit.block.BlockState state) {
        if (state == null) return;
        Arena a = plugin.arenas().activeAt(state.getLocation()).orElse(null);
        if (a == null) return;
        Duel d = duelIn(a);
        if (d == null) return;
        long key = ((long) (state.getX() & 0x3FFFFFF) << 38) | ((long) (state.getY() & 0xFFF) << 26) | (state.getZ() & 0x3FFFFFF);
        d.changes.putIfAbsent(key, state);
        int cx = state.getX() >> 4, cz = state.getZ() >> 4;
        if (d.heldChunks.add(((long) cx << 32) | (cz & 0xFFFFFFFFL))) {
            state.getWorld().addPluginChunkTicket(cx, cz, plugin);
        }
    }

    private void releaseChunkTickets(Duel duel) {
        var ctr = duel.arena.center();
        if (ctr != null && ctr.getWorld() != null && plugin.isEnabled()) {
            for (long ck : duel.heldChunks) ctr.getWorld().removePluginChunkTicket((int) (ck >> 32), (int) ck, plugin);
        }
        duel.heldChunks.clear();
    }

    /** Puts every changed block back and clears loose items and arrows. */
    private void restoreWorld(Duel duel) {
        for (var st : duel.changes.values()) {
            try {
                st.update(true, false);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not restore a block in arena " + duel.arena.name + ": " + e.getMessage());
            }
        }
        duel.changes.clear();
        var ctr = duel.arena.center();
        if (ctr != null && ctr.getWorld() != null) {
            ctr.getWorld().getNearbyEntities(duel.arena.box(),
                    e -> e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.AbstractArrow).forEach(org.bukkit.entity.Entity::remove);
        }
    }

    public boolean isSpectating(UUID id) {
        return spectating.containsKey(id);
    }

    public Duel spectatedDuel(UUID id) {
        return spectating.get(id);
    }

    // ---- requests -------------------------------------------------------

    public void request(Player from, Player to, String kitId, String arenaName) {
        request(from, to, kitId, arenaName, false);
    }

    /** Max ranked challenge duels between the same two players per hour (stops win-trading). */
    private boolean overRankedLimit(UUID a, UUID b) {
        int max = plugin.getConfig().getInt("ranked.challenge-limit-per-hour", 5);
        if (max <= 0) return false;
        Deque<Long> q = rankedPairs.get(pairKey(a, b));
        if (q == null) return false;
        long cutoff = System.currentTimeMillis() - 3_600_000L;
        q.removeIf(ts -> ts < cutoff);
        return q.size() >= max;
    }

    private void recordRankedPair(UUID a, UUID b) {
        rankedPairs.computeIfAbsent(pairKey(a, b), k -> new ArrayDeque<>()).add(System.currentTimeMillis());
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    /** Is this player free to be put into a match right now? Returns the reason if not, else null. */
    public String busyReason(Player p) {
        UUID id = p.getUniqueId();
        if (inDuel(id)) return "in a match";
        if (isSpectating(id)) return "spectating";
        if (plugin.combat().isTagged(id)) return "in combat";
        if (plugin.queue().isQueued(id)) return "in a queue";
        return null;
    }

    public void request(Player from, Player to, String kitId, String arenaName, boolean ranked) {
        request(from, to, kitId, arenaName, ranked, 1);
    }

    /** @param rounds first to this many round wins (1 = a single round) */
    public void request(Player from, Player to, String kitId, String arenaName, boolean ranked, int rounds) {
        rounds = Math.max(1, Math.min(rounds, plugin.getConfig().getInt("duel.max-rounds", 15)));
        Theme t = plugin.theme();
        Kit kit = kitId == null ? plugin.kits().defaultKit() : plugin.kits().get(kitId);
        if (kit == null) { from.sendMessage(t.error("that kit doesn't exist.")); return; }
        if (plugin.parties().blocks(from, "start a 1v1 duel")) return;
        if (plugin.parties().inParty(to.getUniqueId())) { from.sendMessage(t.error("that player is in a party.")); return; }
        if (plugin.queue().isQueued(from.getUniqueId())) { from.sendMessage(t.error("leave the queue first (/queue leave).")); return; }
        if (ranked && !plugin.queue().rankedEnabled()) { from.sendMessage(t.error("ranked is disabled right now.")); return; }
        if (ranked && overRankedLimit(from.getUniqueId(), to.getUniqueId())) {
            from.sendMessage(t.error("you've already played a lot of ranked duels against that player this hour - try someone else or the queue."));
            return;
        }
        if (from.equals(to)) { from.sendMessage(t.error("you can't duel yourself.")); return; }
        if (inDuel(from.getUniqueId())) { from.sendMessage(t.error("you're already in a duel.")); return; }
        if (isSpectating(from.getUniqueId())) { from.sendMessage(t.error("stop spectating first (/spectate leave).")); return; }
        if (isSpectating(to.getUniqueId())) { from.sendMessage(t.error("that player is spectating a duel.")); return; }
        if (inDuel(to.getUniqueId())) { from.sendMessage(t.error("that player is already in a duel.")); return; }
        if (plugin.combat().isTagged(from.getUniqueId())) { from.sendMessage(t.error("you can't duel while in combat.")); return; }
        if (plugin.combat().isTagged(to.getUniqueId())) { from.sendMessage(t.error("that player is in combat.")); return; }

        String chosen = null;
        if (arenaName != null) {
            Arena a = plugin.arenas().get(arenaName);
            if (a == null) { from.sendMessage(t.error("that arena doesn't exist.")); return; }
            if (!a.ready()) { from.sendMessage(t.error("that arena can't be used: " + a.problem() + ".")); return; }
            chosen = a.name;
        } else if (plugin.arenas().randomFree(ranked).isEmpty()) {
            plugin.generator().requestExtra(ranked);
            from.sendMessage(t.error(noArenaReason()));
            return;
        }

        int expire = plugin.getConfig().getInt("duel.request-expire-seconds", 30);
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), kit.id, chosen, System.currentTimeMillis() + expire * 1000L, ranked, false, rounds));

        from.sendMessage(t.info(t.text("duel request sent to ", t.accent()).append(t.raw(to.getName(), t.primary()))
                .append(t.text(" (" + (ranked ? "ranked " : "") + kit.display + (rounds > 1 ? ", first to " + rounds : "") + ")", t.muted()))));
        Component accept = t.text("[accept]", t.primary())
                .clickEvent(ClickEvent.runCommand("/duel accept " + from.getName()));
        Component deny = t.text("[deny]", t.muted())
                .clickEvent(ClickEvent.runCommand("/duel deny " + from.getName()));
        to.sendMessage(t.info(t.raw(from.getName(), t.primary())
                .append(t.text(" challenged you to a " + (ranked ? "ranked " : "") + kit.display + " duel" + (rounds > 1 ? " (first to " + rounds + ")" : "") + (chosen != null ? " on " + chosen : "") + "! ", t.accent()))
                .append(accept).append(Component.text(" ")).append(deny)));
    }

    /**
     * A party leader challenges another party. The request goes to that party's leader and is
     * answered with the same /duel accept as a normal duel.
     */
    public void requestParty(Player leader, Player targetMember, String kitId) {
        Theme t = plugin.theme();
        var mine = plugin.parties().partyOf(leader.getUniqueId());
        var theirs = plugin.parties().partyOf(targetMember.getUniqueId());
        if (mine == null) { leader.sendMessage(t.error("you're not in a party.")); return; }
        if (!mine.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can start a party duel.")); return; }
        if (theirs == null) { leader.sendMessage(t.error("that player isn't in a party.")); return; }
        if (theirs == mine) { leader.sendMessage(t.error("that's your own party.")); return; }
        Kit kit = kitId == null ? plugin.kits().defaultKit() : plugin.kits().get(kitId);
        if (kit == null) { leader.sendMessage(t.error("that kit doesn't exist.")); return; }
        if (!plugin.getConfig().getBoolean("party.allow-uneven-teams", true) && mine.members.size() != theirs.members.size()) {
            leader.sendMessage(t.error("the parties must be the same size (" + mine.members.size() + " vs " + theirs.members.size() + ")."));
            return;
        }
        String why = partyBusy(mine);
        if (why != null) { leader.sendMessage(t.error("your party can't duel right now: " + why)); return; }
        Player theirLeader = Bukkit.getPlayer(theirs.leader);
        if (theirLeader == null) { leader.sendMessage(t.error("that party's leader is offline.")); return; }
        String theirWhy = partyBusy(theirs);
        if (theirWhy != null) { leader.sendMessage(t.error("that party can't duel right now: " + theirWhy)); return; }
        if (plugin.arenas().randomFree(partyNeedsLarge(mine.members.size() + theirs.members.size())).isEmpty()) {
            plugin.generator().requestExtra(partyNeedsLarge(mine.members.size() + theirs.members.size()));
            leader.sendMessage(t.error(noArenaReason()));
            return;
        }

        int expire = plugin.getConfig().getInt("duel.request-expire-seconds", 30);
        requests.put(theirLeader.getUniqueId(), new Request(leader.getUniqueId(), kit.id, null, System.currentTimeMillis() + expire * 1000L, false, true, 1));
        leader.sendMessage(t.info(t.text("party duel request sent to ", t.accent()).append(t.raw(theirLeader.getName() + "'s party", t.primary()))
                .append(t.text(" (" + mine.members.size() + "v" + theirs.members.size() + ", " + kit.display + ")", t.muted()))));
        Component accept = t.text("[accept]", t.primary()).clickEvent(ClickEvent.runCommand("/duel accept " + leader.getName()));
        Component deny = t.text("[deny]", t.muted()).clickEvent(ClickEvent.runCommand("/duel deny " + leader.getName()));
        theirLeader.sendMessage(t.info(t.raw(leader.getName() + "'s party", t.primary())
                .append(t.text(" challenged your party to a " + mine.members.size() + "v" + theirs.members.size() + " " + kit.display + " duel! ", t.accent()))
                .append(accept).append(Component.text(" ")).append(deny)));
    }

    /**
     * A party leader splits their own party into two teams of any sizes (2v1, 3v2...) and plays them
     * against each other.
     */
    public void startScrim(Player leader, List<Player> red, List<Player> blue, String kitId) {
        Theme t = plugin.theme();
        var party = plugin.parties().partyOf(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can start a custom match.")); return; }
        if (red.isEmpty() || blue.isEmpty()) { leader.sendMessage(t.error("both teams need at least one player.")); return; }
        Kit kit = kitId == null ? plugin.kits().defaultKit() : plugin.kits().get(kitId);
        if (kit == null) { leader.sendMessage(t.error("that kit doesn't exist.")); return; }
        Set<UUID> inParty = new HashSet<>(party.members);
        for (Player p : red) if (!inParty.contains(p.getUniqueId())) { leader.sendMessage(t.error("the party changed - open the team menu again.")); return; }
        for (Player p : blue) if (!inParty.contains(p.getUniqueId())) { leader.sendMessage(t.error("the party changed - open the team menu again.")); return; }
        if (red.size() + blue.size() != party.members.size()) { leader.sendMessage(t.error("everyone in the party must be on a team.")); return; }
        String why = partyBusy(party);
        if (why != null) { leader.sendMessage(t.error("your party can't play right now: " + why)); return; }
        boolean large = partyNeedsLarge(party.members.size());
        Arena arena = plugin.arenas().randomFree(large).orElse(null);
        if (arena == null) {
            plugin.generator().requestExtra(large);
            leader.sendMessage(t.error(noArenaReason()));
            return;
        }
        start(Mode.PARTY, List.of(red, blue), List.of("red team", "blue team"), arena, kit, false);
    }

    private boolean partyNeedsLarge(int totalPlayers) {
        return totalPlayers >= plugin.getConfig().getInt("party.large-arena-min-players", 7);
    }

    /** Why this party can't fight right now (offline or busy member), or null. */
    private String partyBusy(PartyManager.Party party) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m == null) return "a member is offline.";
            String why = busyReason(m);
            if (why != null) return m.getName() + " is " + why + ".";
        }
        return null;
    }

    /** Explains why no arena can be used (none set up, all busy, or all broken). */
    public String noArenaReason() {
        if (plugin.generator().building()) return "a new arena is being built - try again in a few seconds.";
        if (plugin.arenas().all().isEmpty()) return "there are no arenas yet - an admin needs to /arena create one.";
        boolean anyReady = plugin.arenas().all().stream().anyMatch(a -> a.ready());
        if (anyReady) return "all arenas are busy right now - try again in a moment.";
        var first = plugin.arenas().all().iterator().next();
        return "no arena is set up correctly: " + first.name + " - " + first.problem() + ". (/arena check)";
    }

    private Request validRequest(Player target, String fromName) {
        Request r = requests.get(target.getUniqueId());
        if (r == null || r.expires < System.currentTimeMillis()) {
            requests.remove(target.getUniqueId());
            return null;
        }
        Player from = Bukkit.getPlayer(r.from);
        if (from == null || (fromName != null && !from.getName().equalsIgnoreCase(fromName))) return null;
        return r;
    }

    public void accept(Player target, String fromName) {
        Theme t = plugin.theme();
        Request r = validRequest(target, fromName);
        if (r == null) { target.sendMessage(t.error("no pending duel request.")); return; }
        Player from = Bukkit.getPlayer(r.from);
        requests.remove(target.getUniqueId());
        Kit kit = plugin.kits().get(r.kit);
        if (kit == null) {
            target.sendMessage(t.error("that kit no longer exists."));
            from.sendMessage(t.error("that kit no longer exists."));
            return;
        }
        if (r.party()) {
            acceptParty(from, target, kit);
            return;
        }
        if (plugin.parties().blocks(target, "accept a 1v1 duel")) return;
        if (plugin.parties().inParty(from.getUniqueId())) {
            target.sendMessage(t.error("that player joined a party, so the duel was cancelled."));
            return;
        }

        if (inDuel(from.getUniqueId()) || inDuel(target.getUniqueId())
                || isSpectating(from.getUniqueId()) || isSpectating(target.getUniqueId())
                || plugin.combat().isTagged(from.getUniqueId()) || plugin.combat().isTagged(target.getUniqueId())) {
            target.sendMessage(t.error("the duel can't start right now."));
            return;
        }
        Arena arena = r.arena != null ? plugin.arenas().get(r.arena) : plugin.arenas().randomFree(r.ranked()).orElse(null);
        if (arena == null || !arena.ready() || arena.inUse) {
            if (r.arena == null) plugin.generator().requestExtra(r.ranked());
            target.sendMessage(t.error("that arena isn't available."));
            from.sendMessage(t.error("that arena isn't available."));
            return;
        }
        start(from, target, arena, kit, r.ranked(), r.rounds());
        if (r.ranked()) recordRankedPair(from.getUniqueId(), target.getUniqueId());
    }

    private void acceptParty(Player challengerLeader, Player targetLeader, Kit kit) {
        Theme t = plugin.theme();
        var mine = plugin.parties().partyOf(challengerLeader.getUniqueId());
        var theirs = plugin.parties().partyOf(targetLeader.getUniqueId());
        if (mine == null || theirs == null || mine == theirs) {
            targetLeader.sendMessage(t.error("the party duel can't start any more (a party changed)."));
            return;
        }
        if (!plugin.getConfig().getBoolean("party.allow-uneven-teams", true) && mine.members.size() != theirs.members.size()) {
            targetLeader.sendMessage(t.error("the parties are no longer the same size."));
            challengerLeader.sendMessage(t.error("the parties are no longer the same size."));
            return;
        }
        String why = partyBusy(mine), theirWhy = partyBusy(theirs);
        if (why != null || theirWhy != null) {
            String msg = "the party duel can't start: " + (why != null ? why : theirWhy);
            targetLeader.sendMessage(t.error(msg));
            challengerLeader.sendMessage(t.error(msg));
            return;
        }
        boolean large = partyNeedsLarge(mine.members.size() + theirs.members.size());
        Arena arena = plugin.arenas().randomFree(large).orElse(null);
        if (arena == null) {
            plugin.generator().requestExtra(large);
            targetLeader.sendMessage(t.error(noArenaReason()));
            challengerLeader.sendMessage(t.error(noArenaReason()));
            return;
        }
        List<Player> a = new ArrayList<>(), b = new ArrayList<>();
        for (UUID id : mine.members) a.add(Bukkit.getPlayer(id));
        for (UUID id : theirs.members) b.add(Bukkit.getPlayer(id));
        start(Mode.PARTY, List.of(a, b), List.of(challengerLeader.getName() + "'s party", targetLeader.getName() + "'s party"), arena, kit, false);
    }

    /** Starts a queue match on a random free arena. @return false if no arena is free. */
    public boolean startMatch(Player a, Player b, Kit kit, boolean ranked) {
        Arena arena = plugin.arenas().randomFree(ranked).orElse(null);
        if (arena == null) {
            plugin.generator().requestExtra(ranked); // all busy: make another one
            return false;
        }
        a.sendMessage(found(b, ranked, kit));
        b.sendMessage(found(a, ranked, kit));
        start(a, b, arena, kit, ranked);
        return true;
    }

    /**
     * A party leader starts a free-for-all among their own party: everyone fights everyone, the last
     * player standing wins.
     */
    public void startPartyFfa(Player leader, String kitId) {
        Theme t = plugin.theme();
        var party = plugin.parties().partyOf(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can start a party free-for-all.")); return; }
        int min = Math.max(3, plugin.getConfig().getInt("party.ffa-min-players", 3));
        if (party.members.size() < min) { leader.sendMessage(t.error("a free-for-all needs at least " + min + " players in the party.")); return; }
        Kit kit = kitId == null ? plugin.kits().defaultKit() : plugin.kits().get(kitId);
        if (kit == null) { leader.sendMessage(t.error("that kit doesn't exist.")); return; }
        String why = partyBusy(party);
        if (why != null) { leader.sendMessage(t.error("your party can't play right now: " + why)); return; }
        boolean large = party.members.size() >= plugin.getConfig().getInt("party.ffa-large-arena-min-players", 5);
        Arena arena = plugin.arenas().randomFree(large).orElse(null);
        if (arena == null) {
            plugin.generator().requestExtra(large);
            leader.sendMessage(t.error(noArenaReason()));
            return;
        }
        List<List<Player>> teams = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (UUID id : party.members) {
            Player p = Bukkit.getPlayer(id);
            teams.add(List.of(p));
            names.add(p.getName());
        }
        start(Mode.FFA, teams, names, arena, kit, false);
    }

    public void deny(Player target, String fromName) {
        Theme t = plugin.theme();
        Request r = validRequest(target, fromName);
        if (r == null) { target.sendMessage(t.error("no pending duel request.")); return; }
        requests.remove(target.getUniqueId());
        target.sendMessage(t.info("duel request denied."));
        Player from = Bukkit.getPlayer(r.from);
        if (from != null) from.sendMessage(t.info(t.raw(target.getName(), t.primary()).append(t.text(" denied your duel request.", t.accent()))));
    }

    // ---- lifecycle ------------------------------------------------------

    private Component found(Player opponent, boolean ranked, Kit kit) {
        Theme t = plugin.theme();
        Component c = t.text("match found! opponent: ", t.accent()).append(t.raw(opponent.getName(), t.primary()));
        if (ranked) {
            c = c.append(t.text(" (" + dev.mintpvp.util.Tier.name(plugin.stats().get(opponent.getUniqueId()).tierIndex(kit.id)) + ")", t.muted()));
        }
        return t.info(c);
    }

    private void start(Player a, Player b, Arena arena, Kit kit, boolean ranked) {
        start(a, b, arena, kit, ranked, 1);
    }

    private void start(Player a, Player b, Arena arena, Kit kit, boolean ranked, int rounds) {
        start(Mode.DUEL, List.of(List.of(a), List.of(b)), List.of(a.getName(), b.getName()), arena, kit, ranked, rounds);
    }

    private void start(Mode mode, List<List<Player>> teams, List<String> names, Arena arena, Kit kit, boolean ranked) {
        start(mode, teams, names, arena, kit, ranked, 1);
    }

    /** Starts a match; if anything goes wrong the players are put back and the arena is freed. */
    private void start(Mode mode, List<List<Player>> teams, List<String> names, Arena arena, Kit kit, boolean ranked, int rounds) {
        List<Player> all = new ArrayList<>();
        teams.forEach(all::addAll);
        try {
            begin(mode, teams, names, arena, kit, ranked, rounds);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Match failed to start on arena " + arena.name, ex);
            Duel d = all.isEmpty() ? null : byPlayer.get(all.get(0).getUniqueId());
            if (d != null) {
                d.drawReason = "an error stopped the match from starting";
                end(d, -1, false);
            } else {
                arena.inUse = false;
            }
            Theme t = plugin.theme();
            for (Player p : all) p.sendMessage(t.error("the match couldn't start - an admin can see the error in the console."));
        }
    }

    private void fail(Duel duel, RuntimeException ex) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Match error on arena " + duel.arena.name, ex);
        duel.drawReason = "an error occurred";
        end(duel, -1, false);
        for (Player p : duel.players) p.sendMessage(plugin.theme().error("the match hit an error and was cancelled."));
    }

    private void begin(Mode mode, List<List<Player>> teams, List<String> names, Arena arena, Kit kit, boolean ranked, int rounds) {
        Duel duel = new Duel(arena, kit, ranked, mode);
        if (mode == Mode.DUEL) duel.firstTo = Math.max(1, rounds);
        duel.teamNames.addAll(names);
        for (int i = 0; i < teams.size(); i++) {
            for (Player p : teams.get(i)) {
                duel.players.add(p);
                duel.team.put(p.getUniqueId(), i);
                duel.alive.add(p.getUniqueId());
            }
        }
        for (Player p : duel.players) {
            plugin.queue().leave(p.getUniqueId());
        }
        arena.inUse = true;
        for (Player p : duel.players) {
            byPlayer.put(p.getUniqueId(), duel);
            requests.remove(p.getUniqueId());
        }
        computeSpawns(duel, teams);

        for (Player p : duel.players) {
            duel.saved.put(p.getUniqueId(), new Saved(p.getLocation(), p.getInventory().getContents().clone(),
                    p.getGameMode(), p.getHealth(), p.getFoodLevel(), p.getSaturation(),
                    p.getLevel(), p.getExp(), new ArrayList<>(p.getActivePotionEffects())));
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            p.setGameMode(GameMode.SURVIVAL);
            p.setFireTicks(0);
            p.setLevel(0);
            p.setExp(0);
            plugin.kits().give(p, kit);
        }
        for (Player p : duel.players) {
            p.teleport(duel.spawnOf(p));
            applyBorder(p, arena); // visible per-player border so everyone can see the arena limits
        }

        if (plugin.getConfig().getBoolean("duel.announce-start", true)) {
            Theme t = plugin.theme();
            String label = switch (mode) {
                case FFA -> "party free-for-all (" + duel.players.size() + " players, " + kit.display + ")";
                case PARTY -> "party duel " + teams.get(0).size() + "v" + teams.get(1).size() + " (" + kit.display + ")";
                default -> (ranked ? "ranked " : "") + kit.display + " duel" + (duel.firstTo > 1 ? ", first to " + duel.firstTo : "");
            };
            Bukkit.broadcast(t.info(t.raw(mode == Mode.FFA ? "a " : duel.title(), t.primary())
                    .append(t.text(mode == Mode.FFA ? "" : " ", t.muted()))
                    .append(t.text("is starting: " + label + " on " + arena.name + " ", t.accent()))
                    .append(t.text("[watch]", t.secondary())
                            .clickEvent(ClickEvent.runCommand("/spectate " + duel.first().getName())))));
        }

        if (plugin.getConfig().getBoolean("duel.clear-mobs", true)) {
            clearMobs(arena);
            duel.sweep = Bukkit.getScheduler().runTaskTimer(plugin, () -> clearMobs(arena), 40L, 40L);
        }

        countdown(duel, Math.max(1, plugin.getConfig().getInt("duel.countdown-seconds", 5)));
    }

    /** The pre-round countdown; at zero the round starts (and its time limit begins). */
    private void countdown(Duel duel, int seconds) {
        Mode mode = duel.mode;
        Theme t = plugin.theme();
        int[] left = {seconds};
        duel.countdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> { try {
            if (left[0] > 0) {
                for (Player p : duel.players) {
                    p.showTitle(Title.title(t.raw(String.valueOf(left[0]), t.primary()),
                            t.raw(duel.roundInfo().isEmpty() ? duel.title() : duel.title() + " · " + duel.roundInfo(), t.secondary()),
                            Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1100), java.time.Duration.ZERO)));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                left[0]--;
                return;
            }
            duel.countdown.cancel();
            duel.state = State.FIGHTING;
            for (Player p : duel.players) {
                p.showTitle(Title.title(t.text("fight!", t.primary()), duel.firstTo > 1 ? t.text("round " + duel.round, t.accent()) : Component.empty()));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            }
            int max = plugin.getConfig().getInt("duel.max-duration-seconds", 300);
            if (mode == Mode.FFA) max = plugin.getConfig().getInt("party.ffa-max-duration-seconds", max);
            if (max > 0) {
                duel.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> timeUp(duel), max * 20L);
            }
        } catch (RuntimeException ex) { duel.countdown.cancel(); fail(duel, ex); } }, 0L, 20L);
    }

    // ---- where everyone starts -------------------------------------------

    private void computeSpawns(Duel duel, List<List<Player>> teams) {
        Arena arena = duel.arena;
        if (duel.mode == Mode.FFA) {
            Location c = arena.center();
            int n = duel.players.size();
            double radius = Math.min(arena.radius() * 0.6, 70);
            double start = Math.random() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double ang = start + Math.PI * 2 * i / n;
                int x = (int) Math.round(c.getX() + Math.cos(ang) * radius), z = (int) Math.round(c.getZ() + Math.sin(ang) * radius);
                Location spot = standingSpot(arena, c.getWorld(), x, z);
                float yaw = (float) Math.toDegrees(Math.atan2(c.getZ() - z, c.getX() - x)) - 90f;
                spot.setYaw(yaw);
                duel.spawns.put(duel.players.get(i).getUniqueId(), spot);
            }
            return;
        }
        for (int t = 0; t < teams.size(); t++) {
            Location anchor = t == 0 ? arena.spawn1() : arena.spawn2();
            List<Player> members = teams.get(t);
            for (int i = 0; i < members.size(); i++) {
                int[] off = OFFSETS[Math.min(i, OFFSETS.length - 1)];
                Location spot = i == 0 ? anchor.clone() : standingSpot(arena, anchor.getWorld(), anchor.getBlockX() + off[0], anchor.getBlockZ() + off[1]);
                if (i > 0) {
                    spot.setYaw(anchor.getYaw());
                    spot.setPitch(0);
                }
                duel.spawns.put(members.get(i).getUniqueId(), spot);
            }
        }
    }

    /** A safe place to stand at x/z: on the generated ground, or the highest block, lifted clear of obstacles. */
    private Location standingSpot(Arena arena, org.bukkit.World world, int x, int z) {
        int y = plugin.generator().standY(arena, x, z);
        if (y < 0) y = world.getHighestBlockYAt(x, z) + 1;
        for (int tries = 0; tries < 12; tries++) {
            var feet = world.getBlockAt(x, y, z);
            var head = world.getBlockAt(x, y + 1, z);
            var below = world.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && head.isPassable() && !below.isPassable()) break;
            if (!feet.isPassable() || !head.isPassable()) y++; else y--;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void applyBorder(Player p, Arena arena) {
        WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(arena.center());
        wb.setSize((arena.radius() + (arena.circular() ? 2 : 0)) * 2.0);
        wb.setWarningDistance(5);
        p.setWorldBorder(wb);
    }

    // ---- spectating -----------------------------------------------------

    /** Finds a running match by a participant's name or by arena name. */
    public Duel find(String query) {
        Player pl = Bukkit.getPlayerExact(query);
        if (pl != null && byPlayer.containsKey(pl.getUniqueId())) return byPlayer.get(pl.getUniqueId());
        return new HashSet<>(byPlayer.values()).stream()
                .filter(d -> d.arena.name.equalsIgnoreCase(query)).findFirst().orElse(null);
    }

    public Collection<Duel> activeDuels() {
        return new HashSet<>(byPlayer.values());
    }

    public void spectate(Player sp, String query) {
        Theme t = plugin.theme();
        UUID id = sp.getUniqueId();
        if (!plugin.getConfig().getBoolean("duel.allow-spectators", true)) { sp.sendMessage(t.error("spectating is disabled.")); return; }
        if (inDuel(id)) { sp.sendMessage(t.error("you're in a duel.")); return; }
        if (isSpectating(id)) { sp.sendMessage(t.error("you're already spectating (/spectate leave).")); return; }
        if (plugin.combat().isTagged(id)) { sp.sendMessage(t.error("you can't spectate while in combat.")); return; }
        if (plugin.parties().blocks(sp, "spectate")) return;
        if (plugin.queue().isQueued(id)) { sp.sendMessage(t.error("leave the queue first (/queue leave).")); return; }
        Duel d = find(query);
        if (d == null) { sp.sendMessage(t.error("no running duel found for that player or arena.")); return; }
        int max = plugin.getConfig().getInt("duel.max-spectators", 20);
        if (max > 0 && d.spectators.size() >= max) { sp.sendMessage(t.error("that duel has too many spectators.")); return; }

        specSaved.put(id, new SpecSaved(sp.getLocation(), sp.getGameMode()));
        d.spectators.add(id);
        spectating.put(id, d);
        applyBorder(sp, d.arena);
        sp.setGameMode(GameMode.SPECTATOR);
        Player focus = d.alive.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().orElse(d.first());
        sp.teleport(focus.getLocation());
        sp.sendMessage(t.info(t.text("now spectating ", t.accent())
                .append(t.raw(d.title(), t.primary()))
                .append(t.text(". /spectate leave to stop.", t.accent()))));
    }

    /** @return false if the player wasn't spectating. */
    public boolean stopSpectating(Player sp) {
        Duel d = spectating.get(sp.getUniqueId());
        if (d == null) return false;
        removeSpectator(d, sp.getUniqueId());
        return true;
    }

    private void removeSpectator(Duel d, UUID id) {
        d.spectators.remove(id);
        spectating.remove(id);
        SpecSaved s = specSaved.remove(id);
        Player sp = Bukkit.getPlayer(id);
        if (sp == null || s == null) return;
        sp.setWorldBorder(null);
        sp.setGameMode(s.mode());
        sp.teleport(s.loc());
    }

    /**
     * Removes mobs from the arena volume. Players, tamed pets and name-tagged
     * mobs are left alone; removal is silent (no drops or xp).
     */
    public void clearMobs(Arena arena) {
        var center = arena.center();
        if (center == null || center.getWorld() == null) return;
        center.getWorld().getNearbyEntities(arena.box(), e ->
                e instanceof org.bukkit.entity.LivingEntity
                        && !(e instanceof Player)
                        && !(e instanceof org.bukkit.entity.ArmorStand)
                        && !(e instanceof org.bukkit.entity.Tameable t && t.isTamed())
                        && e.customName() == null)
                .forEach(org.bukkit.entity.Entity::remove);
    }

    // ---- results --------------------------------------------------------

    /** Total health (with absorption) of the living members of each team. */
    private Map<Integer, Double> teamHealth(Duel d) {
        Map<Integer, Double> out = new LinkedHashMap<>();
        for (Player p : d.players) {
            if (!d.isAlive(p)) continue;
            out.merge(d.teamOf(p), p.getHealth() + p.getAbsorptionAmount(), Double::sum);
        }
        return out;
    }

    /** Time limit reached: the team with more health wins; only an exact tie is a draw. */
    private void timeUp(Duel duel) {
        if (duel.state != State.FIGHTING || duel.ended) return;
        Theme t = plugin.theme();
        if (plugin.getConfig().getBoolean("duel.timeout-winner-by-health", true)) {
            Map<Integer, Double> hp = teamHealth(duel);
            List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(hp.entrySet());
            sorted.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
            if (!sorted.isEmpty() && (sorted.size() == 1 || sorted.get(0).getValue() - sorted.get(1).getValue() >= 1.0)) {
                int win = sorted.get(0).getKey();
                for (Player p : duel.players) {
                    p.sendMessage(t.info(t.text("time's up! ", t.accent()).append(t.raw(duel.teamName(win), t.primary()))
                            .append(t.text(" wins with more health.", t.accent()))));
                }
                teamWon(duel, win);
                return;
            }
        }
        if (duel.firstTo > 1 && duel.mode == Mode.DUEL) {
            for (Player p : duel.players) p.sendMessage(t.info("time's up and the health was level - the round is replayed."));
            roundBreak(duel, -1);
            return;
        }
        duel.drawReason = "time ran out and the health was level";
        end(duel, -1);
    }

    /**
     * A player is out: killed (they watch from spectator mode where they fell), or quit/forfeited
     * ({@code leaving}: they are put back at once). When one team is left standing it wins.
     */
    public void eliminate(Duel d, Player victim, boolean leaving) {
        UUID id = victim.getUniqueId();
        if (!d.alive.remove(id)) {
            if (leaving && !d.left.contains(id) && byPlayer.get(id) == d) {
                // already dead (watching), now leaving: just put them back
                if (d.state == State.ENDING) {
                    end(d, resultOnLeave(d, id));
                } else {
                    removeFromMatch(d, victim);
                }
            }
            return;
        }
        if (d.state == State.ENDING) { // the result is already decided; only tidy up
            if (leaving) end(d, resultOnLeave(d, id));
            return;
        }
        Theme t = plugin.theme();
        creditElimination(d, victim, leaving, t);
        if (leaving) {
            removeFromMatch(d, victim);
        } else {
            try {
                if (victim.getLocation().getY() < d.arena.minY()) victim.teleport(d.spawnOf(victim));
                victim.setFireTicks(0);
                victim.setGameMode(GameMode.SPECTATOR);
                var at = victim.getLocation().add(0, 1, 0);
                victim.getWorld().playSound(at, Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
                victim.getWorld().spawnParticle(org.bukkit.Particle.SOUL, at, 40, .3, .6, .3, .03);
                victim.showTitle(Title.title(t.text("eliminated", t.secondary()), t.text("you can keep watching", t.accent())));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Elimination effects failed", ex);
            }
        }
        if (d.mode != Mode.DUEL) {
            Component msg = t.info(t.raw(victim.getName(), t.secondary())
                    .append(t.text(leaving ? " left the match" : " was eliminated", t.muted()))
                    .append(t.text(" (" + d.alive.size() + " left)", t.muted())));
            for (Player p : d.players) if (byPlayer.get(p.getUniqueId()) == d) p.sendMessage(msg);
        }

        Set<Integer> teams = d.aliveTeams();
        if (teams.size() > 1) return;
        int win = teams.isEmpty() ? -1 : teams.iterator().next();
        if (win < 0) d.drawReason = "everyone was eliminated";
        if (win < 0 && !leaving && d.firstTo > 1 && d.mode == Mode.DUEL) roundBreak(d, -1);
        else if (leaving || win < 0) end(d, win);
        else teamWon(d, win);
    }

    /** Who wins if a player leaves while the result (or the break between rounds) is already pending. */
    private int resultOnLeave(Duel d, UUID leaver) {
        if (!d.roundBreak) return d.pendingWinTeam;
        return d.team.getOrDefault(leaver, 0) == 0 ? 1 : 0; // leaving between rounds forfeits the match
    }

    /** A team won the round (or the whole match, for a single-round duel). */
    private void teamWon(Duel d, int win) {
        if (d.firstTo > 1 && d.mode == Mode.DUEL && win >= 0 && win < d.score.length) {
            d.score[win]++;
            if (d.score[win] < d.firstTo) {
                roundBreak(d, win);
                return;
            }
        }
        finishTeam(d, win);
    }

    /** The round is over but the match goes on: a short pause, then the arena and players are reset. -1 = replay (a drawn round). */
    private void roundBreak(Duel d, int winTeam) {
        if (d.ended || d.state == State.ENDING) return;
        d.state = State.ENDING;
        d.roundBreak = true;
        d.pendingWinTeam = winTeam;
        if (d.timeout != null) d.timeout.cancel();
        int delay = Math.max(1, plugin.getConfig().getInt("duel.round-delay-seconds", 3));
        Theme t = plugin.theme();
        var times = Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(Math.max(1, delay - 1)), java.time.Duration.ofMillis(500));
        String sc = d.score[0] + "-" + d.score[1];
        for (Player p : d.players) {
            if (byPlayer.get(p.getUniqueId()) != d) continue;
            try {
                if (winTeam < 0) {
                    p.showTitle(Title.title(t.text("round drawn", t.accent()), t.text("replaying · " + sc, t.muted()), times));
                } else {
                    boolean won = d.teamOf(p) == winTeam;
                    p.showTitle(Title.title(t.text(won ? "round won" : "round lost", won ? t.primary() : t.secondary()),
                            t.text(sc + " · first to " + d.firstTo, t.accent()), times));
                    if (won) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Round end effects failed", ex);
            }
        }
        d.ending = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                nextRound(d);
            } catch (RuntimeException ex) {
                fail(d, ex);
            }
        }, delay * 20L);
    }

    /** Puts everyone back at their spawn with a fresh kit, restores the arena's changed blocks and counts down. */
    private void nextRound(Duel d) {
        if (d.ended) return;
        d.roundBreak = false;
        d.round++;
        restoreWorld(d);
        d.lastHitBy.clear();
        d.alive.clear();
        for (Player p : d.players) {
            if (byPlayer.get(p.getUniqueId()) != d || d.left.contains(p.getUniqueId())) continue;
            d.alive.add(p.getUniqueId());
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            p.setGameMode(GameMode.SURVIVAL);
            p.setFireTicks(0);
            p.setFallDistance(0);
            p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            plugin.kits().give(p, d.kit);
            p.teleport(d.spawnOf(p));
        }
        d.state = State.COUNTDOWN;
        d.pendingWinTeam = -1;
        countdown(d, Math.max(1, plugin.getConfig().getInt("duel.round-countdown-seconds", 3)));
    }

    /** Stats for an elimination: a kill for whoever last hit the victim (if still an opponent), a death for the victim. */
    private void creditElimination(Duel d, Player victim, boolean leaving, Theme t) {
        try {
            UUID killerId = leaving ? null : d.lastHitBy.get(victim.getUniqueId());
            Player killer = null;
            if (killerId != null) {
                for (Player p : d.players) {
                    if (p.getUniqueId().equals(killerId)) killer = p;
                }
                if (killer != null && d.sameTeam(killer, victim)) killer = null;
            }
            if (killer == null) {
                plugin.stats().recordDeath(victim.getUniqueId());
                return;
            }
            int streak = plugin.stats().recordKill(killer.getUniqueId(), victim.getUniqueId());
            int every = plugin.getConfig().getInt("gameplay.streak-announce-every", 5);
            if (every > 0 && streak % every == 0) {
                Bukkit.broadcast(t.info(t.raw(killer.getName(), t.primary())
                        .append(t.text(" is on a " + streak + " kill streak!", t.accent()))));
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Stat crediting failed", ex);
        }
    }

    /** Compatibility: a 1v1 where {@code winner} won. */
    public void finish(Duel duel, Player winner) {
        finishTeam(duel, duel.teamOf(winner));
    }

    /**
     * The result is decided, but let the fight wind down for a few seconds (duel.end-delay-seconds)
     * before everyone is sent back. Winners celebrate, nobody can be hurt any more.
     */
    public void finishTeam(Duel duel, int winTeam) {
        if (duel.state == State.ENDING || duel.ended) return;
        int delay = plugin.getConfig().getInt("duel.end-delay-seconds", 4);
        if (delay <= 0) {
            end(duel, winTeam);
            return;
        }
        duel.state = State.ENDING;
        duel.pendingWinTeam = winTeam;
        if (duel.timeout != null) duel.timeout.cancel();

        Theme t = plugin.theme();
        var times = Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(Math.max(1, delay - 1)), java.time.Duration.ofMillis(500));
        for (Player p : duel.players) {
            if (byPlayer.get(p.getUniqueId()) != duel) continue;
            try {
                boolean won = duel.teamOf(p) == winTeam;
                p.showTitle(Title.title(t.text(won ? "victory" : "defeat", won ? t.primary() : t.secondary()),
                        t.text("returning in " + delay + "s", t.accent()), times));
                if (won) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Match end effects failed", ex);
            }
        }
        duel.ending = Bukkit.getScheduler().runTaskLater(plugin, () -> end(duel, winTeam), delay * 20L);
    }

    /** End a match. winTeam -1 = a draw. */
    public void end(Duel duel, int winTeam) {
        end(duel, winTeam, true);
    }

    /** @param count false voids the result (server shutdown / error): no ranked rating change. */
    private void end(Duel duel, int winTeam, boolean count) {
        if (duel.ended) return;
        duel.ended = true;
        if (duel.countdown != null) duel.countdown.cancel();
        if (duel.timeout != null) duel.timeout.cancel();
        if (duel.sweep != null) duel.sweep.cancel();
        if (duel.ending != null) duel.ending.cancel();
        for (Player p : duel.players) if (byPlayer.get(p.getUniqueId()) == duel) byPlayer.remove(p.getUniqueId());

        for (UUID sid : new ArrayList<>(duel.spectators)) {
            Player sp = Bukkit.getPlayer(sid);
            if (sp != null) sp.sendMessage(plugin.theme().info("the duel has ended."));
            removeSpectator(duel, sid);
        }

        Theme t = plugin.theme();
        if (winTeam >= 0) {
            List<Player> winners = new ArrayList<>(), losers = new ArrayList<>();
            for (Player p : duel.players) (duel.teamOf(p) == winTeam ? winners : losers).add(p);
            if (count) {
                for (Player p : winners) plugin.stats().addDuelWin(p.getUniqueId());
                for (Player p : losers) plugin.stats().addDuelLoss(p.getUniqueId());
            }
            plugin.getLogger().info("Match " + duel.title() + " on " + duel.arena.name + ": " + duel.teamName(winTeam) + " won");
            Component msg = switch (duel.mode) {
                case FFA -> t.info(t.raw(duel.teamName(winTeam), t.primary())
                        .append(t.text(" won the party free-for-all (" + duel.players.size() + " players)!", t.accent())));
                case PARTY -> t.info(t.raw(duel.teamName(winTeam), t.primary())
                        .append(t.text(" defeated ", t.accent()))
                        .append(t.raw(duel.teamName(1 - winTeam), t.secondary()))
                        .append(t.text(" in a party duel!", t.accent())));
                default -> t.info(t.raw(duel.teamName(winTeam), t.primary())
                        .append(t.text(" defeated ", t.accent()))
                        .append(t.raw(duel.teamName(1 - winTeam), t.secondary()))
                        .append(t.text((duel.ranked ? " in a ranked duel" : " in a duel")
                                + (duel.firstTo > 1 ? " (" + duel.score[winTeam] + "-" + duel.score[1 - winTeam] + ")" : "") + "!", t.accent())));
            };
            Bukkit.broadcast(msg);
            if (duel.mode == Mode.DUEL && duel.ranked && count) rankedResult(duel, winners.get(0), false);
        } else {
            for (Player p : duel.players) p.sendMessage(t.info("the match ended in a draw: " + duel.drawReason + "."));
            plugin.getLogger().info("Match " + duel.title() + " on " + duel.arena.name + " ended in a draw: " + duel.drawReason);
            if (duel.mode == Mode.DUEL && duel.ranked && count) rankedResult(duel, duel.players.get(0), true);
        }
        restoreWorld(duel);
        for (Player p : duel.players) if (!duel.left.contains(p.getUniqueId())) restore(p, duel.saved.get(p.getUniqueId()));
        releaseArena(duel);
    }

    /**
     * The arena stays locked until it has been reset, so the next match always gets a fresh one.
     * Generated arenas are reset completely; hand-made ones only get back the blocks that were
     * recorded as changed.
     */
    private void releaseArena(Duel duel) {
        Arena a = duel.arena;
        Runnable finish = () -> {
            releaseChunkTickets(duel); // the changed chunks were held loaded until the reset was done
            a.inUse = false;
        };
        if (plugin.isEnabled() && plugin.generator().resets(a)) {
            plugin.generator().reset(a, finish);
        } else {
            finish.run();
        }
    }

    /** Applies the kit tier change and tells both players (1v1 ranked duels only). */
    private void rankedResult(Duel duel, Player first, boolean draw) {
        Player second = duel.opponentOf(first);
        var c = plugin.stats().recordRanked(first.getUniqueId(), second.getUniqueId(), draw, duel.kit.id);
        tierMessage(first, duel.kit.display, c[0]);
        tierMessage(second, duel.kit.display, c[1]);
    }

    private void tierMessage(Player p, String kitName, StatsManager.TierChange c) {
        Theme t = plugin.theme();
        String now = dev.mintpvp.util.Tier.name(c.newTier());
        Component msg;
        if (c.placement()) {
            msg = t.text(kitName + " placed: " + now, t.accent());
        } else if (c.newTier() > c.oldTier()) {
            msg = t.text(kitName + " promoted: " + dev.mintpvp.util.Tier.name(c.oldTier()) + " → " + now, t.primary());
        } else if (c.newTier() < c.oldTier()) {
            msg = t.text(kitName + " demoted: " + dev.mintpvp.util.Tier.name(c.oldTier()) + " → " + now, t.secondary());
        } else {
            int need = plugin.getConfig().getInt(c.progress() >= 0 ? "ranked.wins-to-promote" : "ranked.losses-to-demote", 2);
            msg = t.text(kitName + " " + now + " · progress " + c.progress() + "/" + (c.progress() >= 0 ? need : -need), t.accent());
        }
        msg = msg.append(t.text(" · score " + c.score() + (c.scoreDelta() != 0 ? " (" + (c.scoreDelta() > 0 ? "+" : "") + c.scoreDelta() + ")" : ""), t.muted()));
        p.sendMessage(t.info(msg));
    }

    private void restore(Player p, Saved s) {
        p.setWorldBorder(null);
        if (s == null) return;
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.getInventory().setContents(s.contents());
        p.setGameMode(s.mode());
        p.setLevel(s.level());
        p.setExp(s.exp());
        p.setFoodLevel(s.food());
        p.setSaturation(s.saturation());
        p.setFireTicks(0);
        p.addPotionEffects(s.effects());
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.max(1.0, Math.min(max, s.health())));
        p.teleport(s.loc());
    }

    /** Puts one player back where they were and takes them out of the match (the others fight on). */
    private void removeFromMatch(Duel d, Player p) {
        byPlayer.remove(p.getUniqueId());
        d.alive.remove(p.getUniqueId());
        d.left.add(p.getUniqueId());
        restore(p, d.saved.get(p.getUniqueId()));
    }

    /** A player left (quit / forfeit command): they're out and put back at once. */
    public void forfeit(Player p) {
        Duel d = byPlayer.get(p.getUniqueId());
        if (d == null) return;
        eliminate(d, p, true);
    }

    public void shutdown() {
        new HashSet<>(byPlayer.values()).forEach(d -> {
            d.drawReason = "the server is restarting";
            end(d, -1, false);
            // chunks are saved on shutdown, so put a generated arena back right now rather than leave the mess
            if (plugin.generator().resets(d.arena)) plugin.generator().resetSync(d.arena);
        });
    }
}
