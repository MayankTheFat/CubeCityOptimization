package city.cube.optimization.tnt;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * TNT control: per-chunk limits, merging of nearby primed TNT, explosion power
 * caps and block-list trimming. All world access happens on the server thread;
 * the async part is only telemetry bookkeeping and periodic DB writes.
 */
public final class TntOptimizer implements Listener {

    private final CubeCityOptimization plugin;
    private final Map<UUID, Double> mergeBonus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeTnt = new ConcurrentHashMap<>();
    private BukkitTask sweepTask;

    public TntOptimizer(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().tnt.enabled) {
            return;
        }
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 20L * 30, 20L * 30);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        PluginConfig.TntSettings settings = plugin.settings().tnt;
        if (!settings.enabled) {
            return;
        }

        if (settings.maxPerChunk > 0) {
            int inChunk = 0;
            for (Entity entity : tnt.getChunk().getEntities()) {
                if (entity instanceof TNTPrimed primed && primed.isValid()) {
                    inChunk++;
                }
            }
            if (inChunk >= settings.maxPerChunk) {
                event.setCancelled(true);
                removeLater(tnt);
                plugin.stats().inc(Stats.TNT_BLOCKED);
                return;
            }
        }

        if (settings.mergeRadius > 0.0) {
            TNTPrimed nearest = nearestPrimed(tnt, settings.mergeRadius);
            if (nearest != null) {
                event.setCancelled(true);
                mergeBonus.merge(nearest.getUniqueId(), 4.0, Double::sum);
                nearest.setFuseTicks(Math.min(nearest.getFuseTicks(), Math.max(1, tnt.getFuseTicks())));
                removeLater(tnt);
                plugin.stats().inc(Stats.TNT_MERGED);
                if (plugin.settings().debug) {
                    plugin.getLogger().info("Merged primed TNT near " + tnt.getLocation());
                }
                return;
            }
        }

        activeTnt.put(tnt.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        PluginConfig.TntSettings settings = plugin.settings().tnt;
        if (!settings.enabled) {
            return;
        }

        activeTnt.remove(tnt.getUniqueId());
        Double bonus = mergeBonus.remove(tnt.getUniqueId());
        float radius = event.getRadius() + (bonus == null ? 0.0f : bonus.floatValue());
        event.setRadius((float) Math.min(settings.maxExplosionPower, radius));
        plugin.stats().inc(Stats.EXPLOSIONS_OPTIMIZED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) {
            return;
        }
        PluginConfig.TntSettings settings = plugin.settings().tnt;
        if (!settings.enabled || settings.maxBlocksPerExplosion <= 0) {
            return;
        }
        int size = event.blockList().size();
        if (size > settings.maxBlocksPerExplosion) {
            event.blockList().subList(settings.maxBlocksPerExplosion, size).clear();
            plugin.stats().add(Stats.BLOCKS_SAVED, size - settings.maxBlocksPerExplosion);
        }
    }

    private TNTPrimed nearestPrimed(TNTPrimed self, double radius) {
        World world = self.getWorld();
        TNTPrimed nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(self.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof TNTPrimed other) || other.getUniqueId().equals(self.getUniqueId())) {
                continue;
            }
            if (!other.isValid()) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(self.getLocation());
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    private void removeLater(TNTPrimed tnt) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (tnt.isValid()) {
                tnt.remove();
            }
        });
    }

    private void sweep() {
        mergeBonus.keySet().removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || !entity.isValid();
        });
        activeTnt.keySet().removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || !entity.isValid();
        });
    }

    public int primedCount() {
        return activeTnt.size();
    }

    public int pendingMerges() {
        return mergeBonus.size();
    }
}
