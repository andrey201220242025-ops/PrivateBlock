package ru.example.privateblocks;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Взрывы: защита регионов, урон по приватным блокам, логика динамита A-F. */
public final class ExplosionListener implements Listener {
    private final PrivateBlocksPlugin plugin;

    public ExplosionListener(PrivateBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    // ================= события =================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Entity en = e.getEntity();
        Location c = e.getLocation();
        World w = c.getWorld();
        if (w == null) return;

        String id = en.getPersistentDataContainer().get(plugin.dynKey(), PersistentDataType.STRING);
        Dynamite d = plugin.dynamite(id);
        double power = powerOf(en);

        if (d != null) {
            if (!d.underwater() && isUnderwater(c)) {      // не взрывается под водой
                e.setCancelled(true);
                return;
            }
            if (d.mode() == Dynamite.Mode.SPAWNER) {
                e.setCancelled(true);
                breakSpawners(w, c, d.areaRadius());
                effects(w, c);
                return;
            }
            if (d.mode() == Dynamite.Mode.BEDROCK) {
                e.setCancelled(true);
                breakBedrock(w, c);
                effects(w, c);
                return;
            }
            List<Block> calc = computeBlocks(w, c, power, d);
            e.blockList().clear();
            e.blockList().addAll(calc);
            e.setYield(0.25f);
        }

        filter(w, e.blockList());
        damagePrivates(w, c, power);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        World w = e.getBlock().getWorld();
        filter(w, e.blockList());
    }

    // ================= защита =================

    private boolean isPrivate(Block b) {
        return plugin.tierOf(b.getType()) != null && plugin.store().get("p", b) != null;
    }

    /** Приватные блоки не ломаются обычным взрывом; блоки внутри регионов защищены. */
    private void filter(World w, List<Block> list) {
        boolean protect = plugin.getConfig().getBoolean("protect-region-from-explosions", true);
        RegionManager mgr = plugin.wg().manager(w);
        list.removeIf(b -> isPrivate(b) || (protect && mgr != null && plugin.wg().inRegion(mgr, b)));
    }

    private void damagePrivates(World w, Location c, double power) {
        RegionManager mgr = plugin.wg().manager(w);
        if (mgr == null) return;
        double vanilla = plugin.getConfig().getDouble("vanilla-tnt-power", 4.0);
        double damage = power / vanilla;
        double r2 = power * power;

        List<ProtectedRegion> candidates = new ArrayList<>();
        for (ProtectedRegion r : mgr.getRegions().values()) {
            if (r.getId().startsWith("pb_")) candidates.add(r);
        }

        for (ProtectedRegion r : candidates) {
            String[] p = r.getId().split("_");
            if (p.length != 4) continue;
            int x, y, z;
            try {
                x = Integer.parseInt(p[1]);
                y = Integer.parseInt(p[2]);
                z = Integer.parseInt(p[3]);
            } catch (NumberFormatException ex) {
                continue;
            }
            double dx = x + 0.5 - c.getX(), dy = y + 0.5 - c.getY(), dz = z + 0.5 - c.getZ();
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

            Block b = w.getBlockAt(x, y, z);
            String data = plugin.store().get("p", b);
            PrivateTier t = plugin.tierOf(b.getType());
            if (data == null || t == null) continue;

            boolean destroy = t.hits() <= 1;
            if (!destroy) {
                double acc;
                try {
                    acc = Double.parseDouble(data);
                } catch (NumberFormatException ex) {
                    acc = 0;
                }
                acc += damage;
                if (acc >= t.hits() - 1e-9) destroy = true;
                else plugin.store().set("p", b, Double.toString(acc));
            }

            if (destroy) {
                mgr.removeRegion(r.getId());
                plugin.store().remove("p", b);
                Material m = b.getType();
                b.setType(Material.AIR);
                w.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(m));
                w.spawnParticle(Particle.EXPLOSION, b.getLocation().add(0.5, 0.5, 0.5), 1);
            }
        }
    }

    // ================= мощность / вода =================

    private double powerOf(Entity en) {
        if (en instanceof TNTPrimed t) return t.getYield();
        if (en instanceof Creeper cr) return cr.getExplosionRadius() * (cr.isPowered() ? 2 : 1);
        if (en instanceof Explosive ex) return Math.max(1.0, ex.getYield());
        return plugin.getConfig().getDouble("vanilla-tnt-power", 4.0);
    }

    private boolean isUnderwater(Location c) {
        Block b = c.getBlock();
        if (b.isLiquid()) return true;
        return b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged();
    }

    private void effects(World w, Location c) {
        w.spawnParticle(Particle.EXPLOSION, c, 1);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    // ================= динамит E: спавнеры =================

    private void breakSpawners(World w, Location c, int radius) {
        RegionManager mgr = plugin.wg().manager(w);
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() != Material.SPAWNER) continue;
                    if (mgr != null && plugin.wg().inRegion(mgr, b)) continue;

                    ItemStack it = new ItemStack(Material.SPAWNER);
                    if (it.getItemMeta() instanceof BlockStateMeta meta) {
                        meta.setBlockState(b.getState());   // сохраняет моба и настройки спавнера
                        it.setItemMeta(meta);
                    }
                    b.setType(Material.AIR);
                    w.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
                }
            }
        }
    }

    // ================= динамит F: один блок бедрока =================

    private void breakBedrock(World w, Location c) {
        RegionManager mgr = plugin.wg().manager(w);
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() != Material.BEDROCK) continue;
                    if (mgr != null && plugin.wg().inRegion(mgr, b)) continue;
                    double d = b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(c);
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        if (best != null) best.setType(Material.AIR);
    }

    // ================= свой расчёт взрыва (динамит A-D) =================

    private double resistance(Block b, Dynamite d) {
        Material m = b.getType();
        if (m.isAir()) return -1;
        if (b.isLiquid()) return d.underwater() ? -1 : 100;
        double r = m.getBlastResistance();
        if (d.obsidian() && (m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN)) {
            r = plugin.getConfig().getDouble("obsidian-resistance", 6.0);
        }
        return r;
    }

    private List<Block> computeBlocks(World w, Location c, double power, Dynamite d) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Set<Long> seen = new HashSet<>();
        List<Block> out = new ArrayList<>();
        int minH = w.getMinHeight(), maxH = w.getMaxHeight();

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
                    double dx = i / 15.0 * 2 - 1, dy = j / 15.0 * 2 - 1, dz = k / 15.0 * 2 - 1;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx = dx / len * 0.3;
                    dy = dy / len * 0.3;
                    dz = dz / len * 0.3;

                    double strength = power * (0.7 + rnd.nextDouble() * 0.6);
                    double x = c.getX(), y = c.getY(), z = c.getZ();
                    int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
                    boolean lastLoaded = false;

                    for (; strength > 0; x += dx, y += dy, z += dz, strength -= 0.225) {
                        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                        if (by < minH || by >= maxH) break;
                        int cxk = bx >> 4, czk = bz >> 4;
                        if (cxk != lastCx || czk != lastCz) {
                            lastCx = cxk;
                            lastCz = czk;
                            lastLoaded = w.isChunkLoaded(cxk, czk);
                        }
                        if (!lastLoaded) break;

                        Block b = w.getBlockAt(bx, by, bz);
                        double res = resistance(b, d);
                        if (res < 0) continue;
                        strength -= (res + 0.3) * 0.3;
                        if (strength > 0 && !b.isLiquid() && b.getType().getHardness() >= 0) {
                            long key = ((long) bx & 0x3FFFFFFL) << 38 | ((long) bz & 0x3FFFFFFL) << 12 | ((long) by & 0xFFFL);
                            if (seen.add(key)) out.add(b);
                        }
                    }
                }
            }
        }

        int max = plugin.getConfig().getInt("max-explosion-blocks", 30000);
        if (max > 0 && out.size() > max) {
            out.sort(Comparator.comparingDouble(b -> b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(c)));
            out = new ArrayList<>(out.subList(0, max));
        }
        return out;
    }
}
