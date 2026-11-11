package dev.mintpvp.commands;

import dev.mintpvp.MintPvP;
import dev.mintpvp.gui.Guis;
import dev.mintpvp.gui.KitAdminGui;
import dev.mintpvp.gui.LayoutEditorGui;
import dev.mintpvp.managers.KitManager.Kit;
import dev.mintpvp.util.Items;
import dev.mintpvp.util.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /kit            - open the layout editor (everyone)
 * /kit create|delete|seticon|update|edit|list|admin - kit management (mintpvp.admin)
 */
public final class KitCommands implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = List.of("create", "delete", "seticon", "update", "edit", "list", "admin");

    private final MintPvP plugin;

    public KitCommands(MintPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Theme t = plugin.theme();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(t.error("players only."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("layout")) {
            if (args.length > 1) {
                Kit kit = plugin.kits().get(args[1]);
                if (kit == null) { p.sendMessage(t.error("that kit doesn't exist.")); return true; }
                new LayoutEditorGui(plugin, p, kit, false, null).open(p);
            } else {
                Guis.layoutKits(plugin, p, null);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!ADMIN_SUBS.contains(sub)) {
            help(p);
            return true;
        }
        if (!p.hasPermission("mintpvp.admin")) {
            p.sendMessage(t.error("no permission."));
            return true;
        }
        if (sub.equals("admin")) {
            new KitAdminGui(plugin, p, null).open(p);
            return true;
        }
        if (sub.equals("list")) {
            p.sendMessage(t.info("kits (" + plugin.kits().all().size() + ")"));
            for (Kit k : plugin.kits().all()) {
                p.sendMessage(t.line(k.display, k.itemCount() + " items, icon " + k.icon.name().toLowerCase(Locale.ROOT)));
            }
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(t.error("specify a kit name."));
            return true;
        }
        String name = args[1];

        if (sub.equals("create")) {
            if (!name.matches("[A-Za-z0-9_-]{1,16}")) {
                p.sendMessage(t.error("use letters, numbers, - or _ (max 16)."));
                return true;
            }
            Kit k = plugin.kits().create(name, p);
            p.sendMessage(k != null
                    ? t.info("kit " + k.display + " created from your inventory. use /kit admin to set its icon and layout.")
                    : t.error("that kit already exists, or your inventory is empty."));
            return true;
        }

        Kit kit = plugin.kits().get(name);
        if (kit == null) { p.sendMessage(t.error("no such kit.")); return true; }
        switch (sub) {
            case "delete" -> {
                if (plugin.kits().all().size() <= 1) {
                    p.sendMessage(t.error("you can't delete the last kit."));
                } else {
                    plugin.queue().leaveKit(kit);
                    plugin.kits().delete(kit.id);
                    p.sendMessage(t.info("kit deleted."));
                }
            }
            case "seticon" -> {
                var held = p.getInventory().getItemInMainHand();
                if (Items.isEmpty(held)) { p.sendMessage(t.error("hold the item you want as the icon.")); return true; }
                kit.icon = held.getType();
                plugin.kits().saveKits();
                p.sendMessage(t.info("icon updated."));
            }
            case "update" -> {
                plugin.kits().recapture(kit, p);
                p.sendMessage(t.info("kit updated from your inventory."));
            }
            case "edit" -> new LayoutEditorGui(plugin, p, kit, true, null).open(p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        Theme t = plugin.theme();
        p.sendMessage(t.usage("/kit", "edit your kit layouts"));
        if (p.hasPermission("mintpvp.admin")) {
            p.sendMessage(t.usage("/kit create <name>", "make a kit from your inventory"));
            p.sendMessage(t.usage("/kit update <name>", "re-capture from your inventory"));
            p.sendMessage(t.usage("/kit seticon <name>", "use the held item as the icon"));
            p.sendMessage(t.usage("/kit edit <name>", "edit the default layout"));
            p.sendMessage(t.usage("/kit delete <name>", "delete a kit"));
            p.sendMessage(t.usage("/kit admin", "open the kit manager"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("layout");
            if (s.hasPermission("mintpvp.admin")) out.addAll(ADMIN_SUBS);
        } else if (args.length == 2) {
            boolean named = args[0].equalsIgnoreCase("layout")
                    || (s.hasPermission("mintpvp.admin") && List.of("delete", "seticon", "update", "edit").contains(args[0].toLowerCase(Locale.ROOT)));
            if (named) plugin.kits().all().forEach(k -> out.add(k.id));
        }
        String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
    }
}
