package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.PartyGui;
import dev.mintpvp.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /party ... and /pc (party chat). */
public final class PartyCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("create", "invite", "accept", "deny", "leave", "kick", "disband", "promote", "list", "chat", "duel", "help");

    private final MintPvP plugin;

    public PartyCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(t.error("players only."));
            return true;
        }
        var parties = plugin.parties();
        if (cmd.getName().equalsIgnoreCase("pc")) {
            if (args.length == 0) p.sendMessage(t.usage("/pc <message>", "talk to your party"));
            else parties.chat(p, String.join(" ", args));
            return true;
        }
        if (args.length == 0) {
            new PartyGui(plugin, p).open(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String arg1 = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "create" -> parties.create(p);
            case "invite", "add" -> {
                Player target = arg1 == null ? null : Bukkit.getPlayerExact(arg1);
                if (target == null) p.sendMessage(t.error("usage: /party invite <player> (they must be online)."));
                else parties.invite(p, target);
            }
            case "accept", "join" -> parties.accept(p, arg1);
            case "deny" -> parties.deny(p, arg1);
            case "leave" -> parties.leave(p, false);
            case "kick" -> {
                if (arg1 == null) p.sendMessage(t.error("usage: /party kick <player>"));
                else parties.kick(p, arg1);
            }
            case "disband" -> parties.disband(p);
            case "promote", "leader" -> {
                if (arg1 == null) p.sendMessage(t.error("usage: /party promote <player>"));
                else parties.promote(p, arg1);
            }
            case "list" -> parties.list(p);
            case "chat" -> {
                if (args.length < 2) p.sendMessage(t.error("usage: /party chat <message>"));
                else parties.chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "duel", "fight", "challenge" -> {
                if (arg1 == null) {
                    PartyGui.challengeParty(plugin, p, () -> new PartyGui(plugin, p).open(p));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(arg1);
                if (target == null) { p.sendMessage(t.error("player not found.")); return true; }
                String kit = args.length > 2 ? args[2] : null;
                if (kit != null && plugin.kits().get(kit) == null) { p.sendMessage(t.error("that kit doesn't exist.")); return true; }
                plugin.duels().requestParty(p, target, kit);
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        Theme t = plugin.theme();
        p.sendMessage(t.usage("/party", "open the party menu"));
        p.sendMessage(t.usage("/party create", "start a party"));
        p.sendMessage(t.usage("/party invite <player>", "invite someone (leader)"));
        p.sendMessage(t.usage("/party accept|deny [leader]", "answer an invite"));
        p.sendMessage(t.usage("/party leave", "leave your party"));
        p.sendMessage(t.usage("/party kick|promote <player>", "leader tools"));
        p.sendMessage(t.usage("/party disband", "end the party (leader)"));
        p.sendMessage(t.usage("/party list", "show the members"));
        p.sendMessage(t.usage("/party duel <player> [kit]", "challenge another party (leader)"));
        p.sendMessage(t.usage("/pc <message>", "party chat"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("party")) {
            if (args.length == 1) out.addAll(SUBS);
            else if (args.length == 2 && List.of("invite", "add", "accept", "deny", "kick", "promote", "duel").contains(args[0].toLowerCase(Locale.ROOT))) {
                Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("duel")) {
                plugin.kits().all().forEach(k -> out.add(k.id));
            }
        }
        String pre = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
    }
}
