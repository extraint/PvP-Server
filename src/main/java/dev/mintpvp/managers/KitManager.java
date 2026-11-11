package dev.mintpvp.managers;

import dev.mintpvp.MintPvP;
import dev.mintpvp.util.Items;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Admin-defined kits (captured from an inventory) plus per-player layouts.
 *
 * A kit stores 41 slots in Bukkit's PlayerInventory order: 0-35 storage
 * (0-8 hotbar), 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand.
 * Players may rearrange slots 0-35 of any kit; the arrangement is stored
 * separately and only used while it still contains exactly the kit's items.
 */
public final class KitManager {

    public static final int SIZE = 41;
    public static final int MAIN = 36;

    public static final class Kit {
        public final String id;
        public String display;
        public Material icon;
        public ItemStack[] contents; // length SIZE, never null entries

        Kit(String id, String display, Material icon, ItemStack[] contents) {
            this.id = id;
            this.display = display;
            this.icon = icon;
            this.contents = normalize(contents);
        }

        public ItemStack[] main() {
            return Arrays.copyOf(contents, MAIN);
        }

        public void setMain(ItemStack[] main) {
            for (int i = 0; i < MAIN; i++) contents[i] = i < main.length && !Items.isEmpty(main[i]) ? main[i].clone() : new ItemStack(Material.AIR);
        }

        public int itemCount() {
            int n = 0;
            for (ItemStack i : contents) if (!Items.isEmpty(i)) n++;
            return n;
        }
    }

    private final MintPvP plugin;
    private final File kitFile, layoutFile;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ItemStack[]>> layouts = new HashMap<>();

    public KitManager(MintPvP plugin) {
        this.plugin = plugin;
        this.kitFile = new File(plugin.getDataFolder(), "kits.yml");
        this.layoutFile = new File(plugin.getDataFolder(), "layouts.yml");
        loadKits();
        loadLayouts();
        if (kits.isEmpty()) {
            Kit classic = new Kit("classic", "classic", Material.IRON_SWORD, defaultContents());
            kits.put(classic.id, classic);
            saveKits();
        }
    }

    private static ItemStack[] normalize(ItemStack[] in) {
        ItemStack[] out = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) {
            out[i] = i < in.length && !Items.isEmpty(in[i]) ? in[i].clone() : new ItemStack(Material.AIR);
        }
        return out;
    }

    private static ItemStack[] defaultContents() {
        ItemStack[] c = new ItemStack[SIZE];
        c[0] = new ItemStack(Material.IRON_SWORD);
        c[1] = new ItemStack(Material.BOW);
        c[2] = new ItemStack(Material.GOLDEN_APPLE, 4);
        c[3] = new ItemStack(Material.COOKED_BEEF, 32);
        c[9] = new ItemStack(Material.ARROW, 32);
        c[36] = new ItemStack(Material.IRON_BOOTS);
        c[37] = new ItemStack(Material.IRON_LEGGINGS);
        c[38] = new ItemStack(Material.IRON_CHESTPLATE);
        c[39] = new ItemStack(Material.IRON_HELMET);
        return c;
    }

    // ---- persistence ----------------------------------------------------

    private static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] decode(String s) {
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
    }

    private void loadKits() {
        if (!kitFile.exists()) return;
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(kitFile).getConfigurationSection("kits");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            try {
                Material icon = Material.matchMaterial(sec.getString(id + ".icon", "IRON_SWORD"));
                kits.put(id, new Kit(id, sec.getString(id + ".display", id),
                        icon == null ? Material.IRON_SWORD : icon, decode(sec.getString(id + ".data", ""))));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping unreadable kit '" + id + "': " + e.getMessage());
            }
        }
    }

    public void saveKits() {
        YamlConfiguration y = new YamlConfiguration();
        for (Kit k : kits.values()) {
            y.set("kits." + k.id + ".display", k.display);
            y.set("kits." + k.id + ".icon", k.icon.name());
            y.set("kits." + k.id + ".data", encode(k.contents));
        }
        write(y, kitFile);
    }

    private void loadLayouts() {
        if (!layoutFile.exists()) return;
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(layoutFile).getConfigurationSection("players");
        if (sec == null) return;
        for (String uid : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uid);
                ConfigurationSection mine = sec.getConfigurationSection(uid);
                if (mine == null) continue;
                Map<String, ItemStack[]> map = new HashMap<>();
                for (String kit : mine.getKeys(false)) map.put(kit, decode(mine.getString(kit, "")));
                layouts.put(id, map);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping unreadable layouts for " + uid);
            }
        }
    }

    public void saveLayouts() {
        YamlConfiguration y = new YamlConfiguration();
        layouts.forEach((id, map) -> map.forEach((kit, items) -> y.set("players." + id + "." + kit, encode(items))));
        write(y, layoutFile);
    }

    private void write(YamlConfiguration y, File f) {
        try {
            plugin.getDataFolder().mkdirs();
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + f.getName() + ": " + e.getMessage());
        }
    }

    // ---- kits -----------------------------------------------------------

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public Kit defaultKit() {
        Kit k = get(plugin.getConfig().getString("kit.default", "classic"));
        return k != null ? k : kits.values().iterator().next();
    }

    /** Creates a kit from a player's current inventory. Null if the id is taken or the inventory is empty. */
    public Kit create(String id, Player from) {
        id = id.toLowerCase(Locale.ROOT);
        if (kits.containsKey(id)) return null;
        ItemStack[] contents = from.getInventory().getContents();
        Kit k = new Kit(id, id, guessIcon(contents), contents);
        if (k.itemCount() == 0) return null;
        kits.put(id, k);
        saveKits();
        return k;
    }

    /** Re-captures a kit from a player's inventory; invalidates stale player layouts. */
    public void recapture(Kit kit, Player from) {
        kit.contents = normalize(from.getInventory().getContents());
        saveKits();
    }

    public boolean delete(String id) {
        if (kits.size() <= 1) return false; // always keep one kit
        Kit k = kits.remove(id.toLowerCase(Locale.ROOT));
        if (k == null) return false;
        layouts.values().forEach(m -> m.remove(k.id));
        saveKits();
        saveLayouts();
        return true;
    }

    private static Material guessIcon(ItemStack[] c) {
        Material first = null;
        for (ItemStack i : c) {
            if (Items.isEmpty(i)) continue;
            String n = i.getType().name();
            if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("MACE")) {
                return i.getType();
            }
            if (first == null) first = i.getType();
        }
        return first != null ? first : Material.IRON_SWORD;
    }

    // ---- layouts --------------------------------------------------------

    /** Same total amount of every item (stack arrangement ignored)? */
    public static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        return Items.tally(a).equals(Items.tally(b));
    }

    /** The 36 storage slots this player should receive for a kit. */
    public ItemStack[] layoutOf(UUID id, Kit kit) {
        Map<String, ItemStack[]> mine = layouts.get(id);
        ItemStack[] custom = mine == null ? null : mine.get(kit.id);
        if (custom != null && custom.length == MAIN && sameItems(custom, kit.main())) return custom;
        return kit.main();
    }

    public boolean hasCustomLayout(UUID id, Kit kit) {
        Map<String, ItemStack[]> mine = layouts.get(id);
        return mine != null && mine.containsKey(kit.id);
    }

    public void saveLayout(UUID id, Kit kit, ItemStack[] main) {
        layouts.computeIfAbsent(id, k -> new HashMap<>()).put(kit.id, Arrays.copyOf(main, MAIN));
        saveLayouts();
    }

    public void resetLayout(UUID id, Kit kit) {
        Map<String, ItemStack[]> mine = layouts.get(id);
        if (mine != null && mine.remove(kit.id) != null) saveLayouts();
    }

    // ---- giving ---------------------------------------------------------

    public void give(Player p) {
        give(p, defaultKit());
    }

    /** Replaces the player's inventory with the kit, using their saved layout. */
    public void give(Player p, Kit kit) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] all = new ItemStack[SIZE];
        ItemStack[] main = layoutOf(p.getUniqueId(), kit);
        for (int i = 0; i < SIZE; i++) {
            ItemStack src = i < MAIN ? main[i] : kit.contents[i];
            all[i] = Items.isEmpty(src) ? null : src.clone();
        }
        inv.clear();
        inv.setContents(all);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }
}
