package ru.example.privateblocks;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Динамит как блок: метки, поршни, поджиг (огниво, горящая стрела, редстоун, цепная реакция). */
public final class DynamiteListener implements Listener {
    private final PrivateBlocksPlugin plugin;

    public DynamiteListener(PrivateBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- установка ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (b.getType() != Material.TNT) return;
        String id = plugin.dynIdOf(e.getItemInHand());
        if (id != null && plugin.dynamite(id) != null) plugin.store().set("d", b, id.toUpperCase());
        else plugin.store().remove("d", b); // на случай старой метки
    }

    // ---------- ломание: возвращаем динамит предметом ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.TNT && plugin.store().get("d", b) != null) e.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakMonitor(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.TNT) return;
        String id = plugin.store().get("d", b);
        if (id == null) return;
        plugin.store().remove("d", b);
        Dynamite d = plugin.dynamite(id);
        if (d != null && e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), plugin.createDynamite(d, 1));
        }
    }

    // ---------- поджиг ----------

    @EventHandler(ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent e) {
        Block b = e.getBlock();
        String id = plugin.store().get("d", b);
        if (id == null) return;
        Dynamite d = plugin.dynamite(id);
        plugin.store().remove("d", b);
        if (d == null) return;

        e.setCancelled(true);
        b.setType(Material.AIR);
        boolean chain = e.getReason().name().equals("EXPLOSION");
        int fuse = chain ? 10 + ThreadLocalRandom.current().nextInt(20) : 80;
        spawnPrimed(b.getLocation().add(0.5, 0, 0.5), d, fuse, e.getPrimingEntity());
    }

    private void spawnPrimed(Location loc, Dynamite d, int fuse, Entity source) {
        World w = loc.getWorld();
        double vanilla = plugin.getConfig().getDouble("vanilla-tnt-power", 4.0);
        w.spawn(loc, TNTPrimed.class, t -> {
            t.setFuseTicks(fuse);
            t.setYield((float) (vanilla * d.powerMult()));
            t.getPersistentDataContainer().set(plugin.dynKey(), PersistentDataType.STRING, d.id());
            if (source != null) t.setSource(source);
        });
    }

    // ---------- поршни двигают динамит вместе с меткой ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent e) {
        move(e.getBlock(), e.getBlocks(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent e) {
        move(e.getBlock(), e.getBlocks(), true);
    }

    private void move(Block piston, List<Block> blocks, boolean retract) {
        if (!(piston.getBlockData() instanceof Directional dir)) return;
        BlockFace face = retract ? dir.getFacing().getOppositeFace() : dir.getFacing();

        Map<Block, String> moved = new LinkedHashMap<>();
        for (Block b : blocks) {
            if (b.getType() != Material.TNT) continue;
            String id = plugin.store().get("d", b);
            if (id != null) moved.put(b, id);
        }
        if (moved.isEmpty()) return;
        for (Block b : moved.keySet()) plugin.store().remove("d", b);
        for (Map.Entry<Block, String> en : moved.entrySet()) {
            plugin.store().set("d", en.getKey().getRelative(face), en.getValue());
        }
    }
}
