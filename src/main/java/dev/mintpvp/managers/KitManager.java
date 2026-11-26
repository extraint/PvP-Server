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

/** kits and layouts */
public final class KitManager {

    public static final int SIZE = 41;
    public static final int MAIN = 36;

    public static final class Kit {
        public final String id;
        public String display;
        public Material icon;
        public ItemStack[] contents;
        /** owner of a custom kit */
        public UUID owner;

        public boolean custom() {
            return owner != null;
        }

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
    private final File customFile;
    private final Map<UUID, List<Kit>> customKits = new LinkedHashMap<>();
    private final Map<String, Kit> customById = new HashMap<>();

    public KitManager(MintPvP plugin) {
        this.plugin = plugin;
        this.kitFile = new File(plugin.getDataFolder(), "kits.yml");
        this.layoutFile = new File(plugin.getDataFolder(), "layouts.yml");
        this.customFile = new File(plugin.getDataFolder(), "customkits.yml");
        loadKits();
        loadLayouts();
        loadCustom();
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

    public Kit get(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        Kit official = kits.get(k);
        return official != null ? official : customById.get(k);
    }

    // custom kits

    public int maxCustom() {
        return Math.max(1, plugin.getConfig().getInt("custom-kits.max", 5));
    }

    public boolean isCustom(Kit kit) {
        return kit != null && kit.custom();
    }

    public List<Kit> customOf(UUID owner) {
        return Collections.unmodifiableList(customKits.getOrDefault(owner, List.of()));
    }

    /** a kit by id */
    public Kit resolve(Player p, String name) {
        if (name == null) return null;
        for (Kit k : customOf(p.getUniqueId())) if (k.display.equalsIgnoreCase(name) || k.display.replace(' ', '_').equalsIgnoreCase(name)) return k;
        Kit k = get(name);
        return k != null && k.custom() && !k.owner.equals(p.getUniqueId()) ? null : k;
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9 _-]{1,16}") && !name.isBlank();
    }

    public String nameProblem(UUID owner, String name, Kit except) {
        if (!validName(name)) return "names are 1-16 letters, numbers, spaces, _ or -.";
        for (Kit k : customOf(owner)) if (k != except && k.display.equalsIgnoreCase(name.trim())) return "you already have a kit with that name.";
        return null;
    }

    /** new empty custom kit */
    public Kit createCustom(UUID owner, String name) {
        List<Kit> mine = customKits.computeIfAbsent(owner, k -> new ArrayList<>());
        if (mine.size() >= maxCustom() || nameProblem(owner, name, null) != null) return null;
        String id;
        do {
            id = "custom-" + owner.toString().substring(0, 8) + "-" + Integer.toHexString(new Random().nextInt(0x10000));
        } while (customById.containsKey(id));
        Kit k = new Kit(id, name.trim(), Material.CHEST, new ItemStack[SIZE]);
        k.owner = owner;
        mine.add(k);
        customById.put(id, k);
        saveCustom();
        return k;
    }

    public boolean renameCustom(Kit kit, String name) {
        if (!kit.custom() || nameProblem(kit.owner, name, kit) != null) return false;
        kit.display = name.trim();
        saveCustom();
        return true;
    }

    public void deleteCustom(Kit kit) {
        if (!kit.custom()) return;
        List<Kit> mine = customKits.get(kit.owner);
        if (mine != null) mine.remove(kit);
        customById.remove(kit.id);
        layouts.values().forEach(m -> m.remove(kit.id));
        plugin.queue().leaveKit(kit);
        saveCustom();
        saveLayouts();
    }

    /** store edited contents and refresh */
    public void updateCustom(Kit kit, ItemStack[] contents) {
        if (!kit.custom()) return;
        kit.contents = normalize(contents);
        kit.icon = guessIcon(kit.contents);
        layouts.values().forEach(m -> m.remove(kit.id)); // old arrangements no longer match
        saveCustom();
        saveLayouts();
    }

    private void loadCustom() {
        if (!customFile.exists()) return;
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(customFile).getConfigurationSection("players");
        if (sec == null) return;
        for (String uid : sec.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(uid);
                ConfigurationSection mine = sec.getConfigurationSection(uid);
                if (mine == null) continue;
                for (String key : mine.getKeys(false)) {
                    String id = mine.getString(key + ".id", "");
                    if (id.isEmpty()) continue;
                    Material icon = Material.matchMaterial(mine.getString(key + ".icon", "CHEST"));
                    Kit k = new Kit(id, mine.getString(key + ".display", "kit"), icon == null ? Material.CHEST : icon, decode(mine.getString(key + ".data", "")));
                    k.owner = owner;
                    customKits.computeIfAbsent(owner, x -> new ArrayList<>()).add(k);
                    customById.put(id, k);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping unreadable custom kits for " + uid + ": " + e.getMessage());
            }
        }
    }

    public void saveCustom() {
        YamlConfiguration y = new YamlConfiguration();
        customKits.forEach((owner, list) -> {
            int n = 0;
            for (Kit k : list) {
                String base = "players." + owner + "." + (n++);
                y.set(base + ".id", k.id);
                y.set(base + ".display", k.display);
                y.set(base + ".icon", k.icon.name());
                y.set(base + ".data", encode(k.contents));
            }
        });
        write(y, customFile);
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public Kit defaultKit() {
        Kit k = get(plugin.getConfig().getString("kit.default", "classic"));
        return k != null ? k : kits.values().iterator().next();
    }

    /** create kit */
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

    /** recapture kit */
    public void recapture(Kit kit, Player from) {
        kit.contents = normalize(from.getInventory().getContents());
        saveKits();
    }

    public boolean delete(String id) {
        if (kits.size() <= 1) return false;
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

    /** same items? */
    public static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        return Items.tally(a).equals(Items.tally(b));
    }

    /** player layout */
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

    public void give(Player p) {
        give(p, defaultKit());
    }

    /** give kit */
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
