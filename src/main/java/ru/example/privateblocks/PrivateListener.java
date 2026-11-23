package ru.example.privateblocks;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.List;

/** Создание и удаление приватов через WorldGuard при установке/ломании блоков. */
public final class PrivateListener implements Listener {
    private final PrivateBlocksPlugin plugin;

    public PrivateListener(PrivateBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isPrivate(Block b) {
        return plugin.tierOf(b.getType()) != null && plugin.store().get("p", b) != null;
    }

    // ---------- установка ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        PrivateTier t = plugin.tierOf(b.getType());
        if (t == null || plugin.disabledWorld(b.getWorld().getName())) return;
        Player p = e.getPlayer();
        if (p.hasPermission("privateblocks.bypass")) return;
        if (plugin.wg().foreignOverlap(p, b, t)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("private-overlap"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceMonitor(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        PrivateTier t = plugin.tierOf(b.getType());
        if (t == null || plugin.disabledWorld(b.getWorld().getName())) return;
        Player p = e.getPlayer();
        if (!plugin.wg().create(p, b, t)) return;
        plugin.store().set("p", b, "0");
        String size = String.valueOf(t.size());
        p.sendMessage(plugin.msg("private-created", "size", size));
    }

    // ---------- ломание ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isPrivate(b)) return;
        Player p = e.getPlayer();
        ProtectedRegion r = plugin.wg().get(b);
        if (r != null && !r.getOwners().contains(p.getUniqueId()) && !p.hasPermission("privateblocks.bypass")) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("not-owner"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakMonitor(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isPrivate(b)) return;
        plugin.wg().remove(b);
        plugin.store().remove("p", b);
        e.getPlayer().sendMessage(plugin.msg("private-removed"));
    }

    // ---------- защита самого блока ----------

    @EventHandler(ignoreCancelled = true)
    public void onMobGrief(EntityChangeBlockEvent e) {
        if (isPrivate(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (movesPrivate(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (movesPrivate(e.getBlocks())) e.setCancelled(true);
    }

    private boolean movesPrivate(List<Block> blocks) {
        for (Block b : blocks) if (isPrivate(b)) return true;
        return false;
    }
}
