package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/** Parties: a leader and up to {@code party.max-size} members who can fight other parties together. */
public final class PartyManager {

    public static final class Party {
        public final UUID id = UUID.randomUUID();
        public UUID leader;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    }

    private record Invite(UUID party, UUID from, long expires) {}

    private final MintPvP plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, List<Invite>> invites = new HashMap<>(); // keyed by invited player

    public PartyManager(MintPvP plugin) {
        this.plugin = plugin;
    }

    public Party partyOf(UUID id) {
        return byMember.get(id);
    }

    public Collection<Party> all() {
        return new LinkedHashSet<>(byMember.values());
    }

    public int maxSize() {
        return Math.max(2, plugin.getConfig().getInt("party.max-size", 6));
    }

    public List<Player> online(Party party) {
        List<Player> out = new ArrayList<>();
        for (UUID id : party.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public void broadcast(Party party, Component msg) {
        online(party).forEach(p -> p.sendMessage(msg));
    }

    private boolean busy(Player p, Theme t, String what) {
        UUID id = p.getUniqueId();
        if (plugin.duels().inDuel(id)) { p.sendMessage(t.error("you can't " + what + " during a duel.")); return true; }
        if (plugin.duels().isSpectating(id)) { p.sendMessage(t.error("stop spectating first (/spectate leave).")); return true; }
        if (plugin.queue().isQueued(id)) { p.sendMessage(t.error("leave the queue first (/queue leave).")); return true; }
        return false;
    }

    public boolean inParty(UUID id) {
        return byMember.containsKey(id);
    }

    /**
     * While you're in a party you can only do party things. Returns true (after telling the player)
     * if this player is in a party and so can't {@code what}.
     */
    public boolean blocks(Player p, String what) {
        if (!inParty(p.getUniqueId())) return false;
        p.sendMessage(plugin.theme().error("you're in a party, so you can't " + what + ". leave it first with /party leave (party duels are in /party)."));
        return true;
    }

    public Party create(Player p) {
        Theme t = plugin.theme();
        if (byMember.containsKey(p.getUniqueId())) { p.sendMessage(t.error("you're already in a party (/party leave).")); return null; }
        if (busy(p, t, "create a party")) return null;
        Party party = new Party();
        party.leader = p.getUniqueId();
        party.members.add(p.getUniqueId());
        byMember.put(p.getUniqueId(), party);
        p.sendMessage(t.info("party created! invite players with /party invite <player>."));
        return party;
    }

    public void invite(Player leader, Player target) {
        Theme t = plugin.theme();
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) party = create(leader); // inviting makes you a party if you aren't in one
        if (party == null) return;
        if (!party.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can invite players.")); return; }
        if (target.equals(leader)) { leader.sendMessage(t.error("you can't invite yourself.")); return; }
        if (byMember.containsKey(target.getUniqueId())) { leader.sendMessage(t.error("that player is already in a party.")); return; }
        if (party.members.size() >= maxSize()) { leader.sendMessage(t.error("your party is full (" + maxSize() + " players).")); return; }

        int expire = Math.max(10, plugin.getConfig().getInt("party.invite-expire-seconds", 60));
        List<Invite> list = invites.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        final UUID pid = party.id;
        list.removeIf(i -> i.party().equals(pid));
        list.add(new Invite(party.id, leader.getUniqueId(), System.currentTimeMillis() + expire * 1000L));

        leader.sendMessage(t.info(t.text("invited ", t.accent()).append(t.raw(target.getName(), t.primary())).append(t.text(" to your party.", t.accent()))));
        Component accept = t.text("[accept]", t.primary()).clickEvent(ClickEvent.runCommand("/party accept " + leader.getName()));
        Component deny = t.text("[deny]", t.muted()).clickEvent(ClickEvent.runCommand("/party deny " + leader.getName()));
        target.sendMessage(t.info(t.raw(leader.getName(), t.primary())
                .append(t.text(" invited you to their party (" + party.members.size() + " members). ", t.accent()))
                .append(accept).append(Component.text(" ")).append(deny)));
    }

    private Invite findInvite(Player target, String fromName) {
        List<Invite> list = invites.get(target.getUniqueId());
        if (list == null) return null;
        long now = System.currentTimeMillis();
        list.removeIf(i -> i.expires() < now || byMember.values().stream().noneMatch(p -> p.id.equals(i.party())));
        for (int i = list.size() - 1; i >= 0; i--) {
            Invite inv = list.get(i);
            Player from = Bukkit.getPlayer(inv.from());
            if (fromName == null || (from != null && from.getName().equalsIgnoreCase(fromName))) return inv;
        }
        return null;
    }

    public void accept(Player target, String fromName) {
        Theme t = plugin.theme();
        if (byMember.containsKey(target.getUniqueId())) { target.sendMessage(t.error("you're already in a party (/party leave first).")); return; }
        if (busy(target, t, "join a party")) return;
        Invite inv = findInvite(target, fromName);
        if (inv == null) { target.sendMessage(t.error("no pending party invite.")); return; }
        Party party = byMember.values().stream().filter(p -> p.id.equals(inv.party())).findFirst().orElse(null);
        if (party == null) { target.sendMessage(t.error("that party no longer exists.")); return; }
        if (party.members.size() >= maxSize()) { target.sendMessage(t.error("that party is full.")); return; }
        invites.remove(target.getUniqueId());
        party.members.add(target.getUniqueId());
        byMember.put(target.getUniqueId(), party);
        broadcast(party, t.info(t.raw(target.getName(), t.primary()).append(t.text(" joined the party (" + party.members.size() + "/" + maxSize() + ").", t.accent()))));
    }

    public void deny(Player target, String fromName) {
        Theme t = plugin.theme();
        Invite inv = findInvite(target, fromName);
        if (inv == null) { target.sendMessage(t.error("no pending party invite.")); return; }
        invites.getOrDefault(target.getUniqueId(), new ArrayList<>()).remove(inv);
        target.sendMessage(t.info("invite denied."));
        Player from = Bukkit.getPlayer(inv.from());
        if (from != null) from.sendMessage(t.info(t.raw(target.getName(), t.primary()).append(t.text(" denied your party invite.", t.accent()))));
    }

    /** @param quiet no message to the leaver (used when they log out) */
    public void leave(Player p, boolean quiet) {
        Theme t = plugin.theme();
        Party party = byMember.get(p.getUniqueId());
        if (party == null) {
            if (!quiet) p.sendMessage(t.error("you're not in a party."));
            return;
        }
        remove(party, p.getUniqueId());
        if (!quiet) p.sendMessage(t.info("you left the party."));
        if (!party.members.isEmpty()) {
            broadcast(party, t.info(t.raw(p.getName(), t.secondary()).append(t.text(" left the party.", t.muted()))));
        }
    }

    /** Removes a member; passes leadership on, or disbands an empty party. */
    private void remove(Party party, UUID id) {
        party.members.remove(id);
        byMember.remove(id);
        if (party.members.isEmpty()) return;
        if (party.leader.equals(id)) {
            party.leader = party.members.iterator().next();
            Player nl = Bukkit.getPlayer(party.leader);
            broadcast(party, plugin.theme().info(plugin.theme().raw(nl != null ? nl.getName() : "someone", plugin.theme().primary())
                    .append(plugin.theme().text(" is the new party leader.", plugin.theme().accent()))));
        }
    }

    public void kick(Player leader, String name) {
        Theme t = plugin.theme();
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(t.error("you're not in a party.")); return; }
        if (!party.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can kick players.")); return; }
        UUID target = null;
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.getName().equalsIgnoreCase(name)) target = id;
        }
        if (target == null) { leader.sendMessage(t.error("that player isn't in your party.")); return; }
        if (target.equals(leader.getUniqueId())) { leader.sendMessage(t.error("you can't kick yourself - use /party leave or /party disband.")); return; }
        Player kicked = Bukkit.getPlayer(target);
        remove(party, target);
        if (kicked != null) kicked.sendMessage(t.info("you were kicked from the party."));
        broadcast(party, t.info(t.raw(name, t.secondary()).append(t.text(" was kicked from the party.", t.muted()))));
    }

    public void disband(Player leader) {
        Theme t = plugin.theme();
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(t.error("you're not in a party.")); return; }
        if (!party.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can disband the party.")); return; }
        broadcast(party, t.info("the party was disbanded."));
        for (UUID id : new ArrayList<>(party.members)) byMember.remove(id);
        party.members.clear();
    }

    public void promote(Player leader, String name) {
        Theme t = plugin.theme();
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) { leader.sendMessage(t.error("you're not in a party.")); return; }
        if (!party.leader.equals(leader.getUniqueId())) { leader.sendMessage(t.error("only the party leader can promote someone.")); return; }
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.getName().equalsIgnoreCase(name) && !id.equals(leader.getUniqueId())) {
                party.leader = id;
                broadcast(party, t.info(t.raw(m.getName(), t.primary()).append(t.text(" is the new party leader.", t.accent()))));
                return;
            }
        }
        leader.sendMessage(t.error("that player isn't in your party."));
    }

    public void list(Player p) {
        Theme t = plugin.theme();
        Party party = byMember.get(p.getUniqueId());
        if (party == null) { p.sendMessage(t.error("you're not in a party. /party create")); return; }
        p.sendMessage(t.info("party (" + party.members.size() + "/" + maxSize() + ")"));
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            String name = m != null ? m.getName() : plugin.stats().nameOf(id);
            p.sendMessage(t.raw((id.equals(party.leader) ? "★ " : "• "), t.secondary())
                    .append(t.raw(name, m != null ? t.primary() : t.muted()))
                    .append(t.text(m == null ? " (offline)" : id.equals(party.leader) ? " (leader)" : "", t.muted())));
        }
    }

    public void chat(Player p, String message) {
        Theme t = plugin.theme();
        Party party = byMember.get(p.getUniqueId());
        if (party == null) { p.sendMessage(t.error("you're not in a party.")); return; }
        broadcast(party, t.text("party ", t.secondary()).append(t.raw(p.getName() + ": ", t.primary())).append(Component.text(message, t.accent())));
    }
}
