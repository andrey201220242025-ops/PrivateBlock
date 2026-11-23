package ru.example.privateblocks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PrivateBlocksPlugin extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<Material, PrivateTier> tiers = new HashMap<>();
    private final Map<String, Dynamite> dynamites = new LinkedHashMap<>();
    private NamespacedKey dynKey;
    private BlockStore store;
    private Wg wg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dynKey = new NamespacedKey(this, "dyn");
        store = new BlockStore(this);
        wg = new Wg(this);
        loadSettings();

        getServer().getPluginManager().registerEvents(new PrivateListener(this), this);
        getServer().getPluginManager().registerEvents(new DynamiteListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);

        PluginCommand cmd = getCommand("dynamite");
        if (cmd != null) {
            DynamiteCommand dc = new DynamiteCommand(this);
            cmd.setExecutor(dc);
            cmd.setTabCompleter(dc);
        }
    }

    private void loadSettings() {
        tiers.clear();
        dynamites.clear();

        ConfigurationSection pb = getConfig().getConfigurationSection("private-blocks");
        if (pb != null) {
            for (String key : pb.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null || !m.isBlock()) {
                    getLogger().warning("private-blocks: неверный материал " + key);
                    continue;
                }
                int size = Math.max(1, pb.getInt(key + ".size", 3));
                int hits = Math.max(1, pb.getInt(key + ".hits", 1));
                tiers.put(m, new PrivateTier(m, size, hits));
            }
        }

        ConfigurationSection ds = getConfig().getConfigurationSection("dynamites");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection s = ds.getConfigurationSection(id);
                if (s == null) continue;
                Dynamite.Mode mode;
                try {
                    mode = Dynamite.Mode.valueOf(s.getString("mode", "NORMAL").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    getLogger().warning("dynamites." + id + ": неверный mode");
                    continue;
                }
                dynamites.put(id.toUpperCase(Locale.ROOT), new Dynamite(
                        id.toUpperCase(Locale.ROOT),
                        s.getDouble("power-multiplier", 1.0),
                        s.getBoolean("underwater", false),
                        s.getBoolean("breaks-obsidian", false),
                        mode,
                        Math.max(0, s.getInt("area-radius", 1)),
                        s.getString("name", "Динамит " + id),
                        s.getStringList("lore")));
            }
        }
    }

    // ---------- доступ ----------

    public BlockStore store() { return store; }
    public Wg wg() { return wg; }
    public NamespacedKey dynKey() { return dynKey; }
    public PrivateTier tierOf(Material m) { return tiers.get(m); }
    public Dynamite dynamite(String id) { return id == null ? null : dynamites.get(id.toUpperCase(Locale.ROOT)); }
    public Collection<Dynamite> dynamites() { return dynamites.values(); }

    public boolean disabledWorld(String world) {
        return getConfig().getStringList("disabled-worlds").contains(world);
    }

    // ---------- предметы ----------

    private static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack createDynamite(Dynamite d, int amount) {
        ItemStack it = new ItemStack(Material.TNT, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(d.name())));
        List<Component> lore = new ArrayList<>();
        for (String l : d.lore()) lore.add(noItalic(MM.deserialize(l)));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(dynKey, PersistentDataType.STRING, d.id());
        it.setItemMeta(meta);
        return it;
    }

    public String dynIdOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(dynKey, PersistentDataType.STRING);
    }

    // ---------- сообщения ----------

    public Component msg(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "<red>Нет сообщения: " + key);
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return MM.deserialize(getConfig().getString("messages.prefix", "") + s);
    }

    public Component plain(String miniMessage) {
        return MM.deserialize(miniMessage);
    }
}
