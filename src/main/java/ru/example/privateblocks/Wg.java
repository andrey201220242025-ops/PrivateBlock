package ru.example.privateblocks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Вся работа с WorldGuard API. */
public final class Wg {
    private final PrivateBlocksPlugin plugin;

    public Wg(PrivateBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    public RegionManager manager(World w) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(w));
    }

    public static String idOf(Block b) {
        return "pb_" + b.getX() + "_" + b.getY() + "_" + b.getZ();
    }

    public boolean inRegion(RegionManager mgr, Block b) {
        return mgr.getApplicableRegions(BlockVector3.at(b.getX(), b.getY(), b.getZ())).size() > 0;
    }

    private static int[] bounds(World w, Block b, int size) {
        int half = size / 2;
        int minX = b.getX() - half, minZ = b.getZ() - half;
        int minY = Math.max(w.getMinHeight(), b.getY() - half);
        int maxY = Math.min(w.getMaxHeight() - 1, b.getY() - half + size - 1);
        return new int[]{minX, minY, minZ, minX + size - 1, maxY, minZ + size - 1};
    }

    /** true, если область нового привата задевает регион, где игрок не владелец и не участник. */
    public boolean foreignOverlap(Player p, Block b, PrivateTier t) {
        RegionManager mgr = manager(b.getWorld());
        if (mgr == null) return false;
        int[] bd = bounds(b.getWorld(), b, t.size());
        UUID u = p.getUniqueId();
        for (ProtectedRegion r : mgr.getRegions().values()) {
            if (r.getId().equals(ProtectedRegion.GLOBAL_REGION)) continue;
            BlockVector3 a = r.getMinimumPoint(), c = r.getMaximumPoint();
            boolean overlap = a.getBlockX() <= bd[3] && c.getBlockX() >= bd[0]
                    && a.getBlockY() <= bd[4] && c.getBlockY() >= bd[1]
                    && a.getBlockZ() <= bd[5] && c.getBlockZ() >= bd[2];
            if (overlap && !r.getOwners().contains(u) && !r.getMembers().contains(u)) return true;
        }
        return false;
    }

    public boolean create(Player p, Block b, PrivateTier t) {
        RegionManager mgr = manager(b.getWorld());
        if (mgr == null) return false;
        int[] bd = bounds(b.getWorld(), b, t.size());
        ProtectedCuboidRegion r = new ProtectedCuboidRegion(idOf(b),
                BlockVector3.at(bd[0], bd[1], bd[2]), BlockVector3.at(bd[3], bd[4], bd[5]));
        r.getOwners().addPlayer(p.getUniqueId());

        ConfigurationSection flags = plugin.getConfig().getConfigurationSection("region-flags");
        if (flags != null) {
            for (String name : flags.getKeys(false)) {
                Flag<?> f = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), name);
                if (f instanceof StateFlag sf) {
                    boolean allow = "allow".equalsIgnoreCase(flags.getString(name, "allow"));
                    r.setFlag(sf, allow ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                } else {
                    plugin.getLogger().warning("Флаг '" + name + "' не найден или не state-флаг, пропущен.");
                }
            }
        }
        mgr.addRegion(r);
        return true;
    }

    public ProtectedRegion get(Block b) {
        RegionManager mgr = manager(b.getWorld());
        return mgr == null ? null : mgr.getRegion(idOf(b));
    }

    public void remove(Block b) {
        RegionManager mgr = manager(b.getWorld());
        if (mgr != null) mgr.removeRegion(idOf(b));
    }
}
