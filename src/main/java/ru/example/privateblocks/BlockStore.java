package ru.example.privateblocks;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Хранит метки на блоках в PDC чанка (переживает рестарт, не нужны файлы). */
public final class BlockStore {
    private final JavaPlugin plugin;

    public BlockStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(String kind, Block b) {
        return new NamespacedKey(plugin, kind + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ());
    }

    private PersistentDataContainer pdc(Block b) {
        return b.getChunk().getPersistentDataContainer();
    }

    public String get(String kind, Block b) {
        return pdc(b).get(key(kind, b), PersistentDataType.STRING);
    }

    public void set(String kind, Block b, String value) {
        pdc(b).set(key(kind, b), PersistentDataType.STRING, value);
    }

    public void remove(String kind, Block b) {
        pdc(b).remove(key(kind, b));
    }
}
