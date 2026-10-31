package city.cube.optimization.mob;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import city.cube.optimization.util.Keys;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mob caps with two layers: spawn-time gatekeeping (cheap, exact for natural
 * spawns) and a periodic reconcile pass that also removes excess mobs.
 */
public final class MobOptimizer implements Listener {

    private static final class WorldCounts {
        int total;
        final Map<Long, Integer> perChunk = new HashMap<>();
    }

    private final CubeCityOptimization plugin;
    private final Map<UUID, WorldCounts> counts = new HashMap<>();
    private final Map<UUID, Long> countedChunks = new HashMap<>();
    private BukkitTask task;

    public MobOptimizer(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().mob.enabled) {
            return;
        }
        long period = Math.max(20L, plugin.settings().mob.removeIntervalSeconds * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 40L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        PluginConfig.MobSettings settings = plugin.settings().mob;
        if (!settings.enabled || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (settings.hostileOnly && !(mob instanceof Monster)) {
            return;
        }

        if (plugin.tps().pressure() && plugin.settings().tick.pressureSpawnThrottle) {
            event.setCancelled(true);
            plugin.stats().inc(Stats.SPAWNS_BLOCKED);
            return;
        }

        WorldCounts worldCounts = counts.computeIfAbsent(mob.getWorld().getUID(), ignored -> new WorldCounts());
        long chunkKey = Keys.chunk(mob.getLocation());

        if (settings.globalCap > 0 && worldCounts.total >= settings.globalCap) {
            event.setCancelled(true);
            plugin.stats().inc(Stats.SPAWNS_BLOCKED);
            return;
        }
        int inChunk = worldCounts.perChunk.getOrDefault(chunkKey, 0);
        if (settings.perChunkCap > 0 && inChunk >= settings.perChunkCap) {
            event.setCancelled(true);
            plugin.stats().inc(Stats.SPAWNS_BLOCKED);
            return;
        }

        worldCounts.total++;
        worldCounts.perChunk.merge(chunkKey, 1, Integer::sum);
        countedChunks.put(mob.getUniqueId(), chunkKey);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        Long chunkKey = countedChunks.remove(mob.getUniqueId());
        WorldCounts worldCounts = counts.get(mob.getWorld().getUID());
        if (chunkKey == null || worldCounts == null) {
            return;
        }
        worldCounts.total = Math.max(0, worldCounts.total - 1);
        Integer value = worldCounts.perChunk.get(chunkKey);
        if (value != null) {
            if (value <= 1) {
                worldCounts.perChunk.remove(chunkKey);
            } else {
                worldCounts.perChunk.put(chunkKey, value - 1);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        counts.remove(event.getWorld().getUID());
    }

    /** Recounts every world and optionally removes the excess. */
    public void reconcile() {
        PluginConfig.MobSettings settings = plugin.settings().mob;
        if (!settings.enabled) {
            return;
        }
        countedChunks.clear();
        for (World world : Bukkit.getWorlds()) {
            List<Mob> mobs = new ArrayList<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob && (!settings.hostileOnly || mob instanceof Monster)) {
                    mobs.add(mob);
                }
            }
            List<Player> players = world.getPlayers();

            if (settings.autoRemoveExcess) {
                removePerChunkExcess(settings, mobs, players);
                mobs.removeIf(mob -> !mob.isValid());
                removeGlobalExcess(settings, mobs, players);
                mobs.removeIf(mob -> !mob.isValid());
            }

            recount(world, mobs);
        }
    }

    private void removePerChunkExcess(PluginConfig.MobSettings settings, List<Mob> mobs, List<Player> players) {
        if (settings.perChunkCap <= 0) {
            return;
        }
        Map<Long, List<Mob>> byChunk = new HashMap<>();
        for (Mob mob : mobs) {
            byChunk.computeIfAbsent(Keys.chunk(mob.getLocation()), ignored -> new ArrayList<>()).add(mob);
        }
        for (List<Mob> chunkMobs : byChunk.values()) {
            if (chunkMobs.size() <= settings.perChunkCap) {
                continue;
            }
            List<Mob> candidates = new ArrayList<>();
            for (Mob mob : chunkMobs) {
                if (!exempt(mob, settings)) {
                    candidates.add(mob);
                }
            }
            int removable = chunkMobs.size() - settings.perChunkCap;
            candidates.sort(Comparator.comparingDouble(
                    mob -> -nearestPlayerDistanceSquared(mob.getLocation(), players)));
            for (int i = 0; i < Math.min(removable, candidates.size()); i++) {
                candidates.get(i).remove();
                plugin.stats().inc(Stats.MOBS_REMOVED);
            }
        }
    }

    private void removeGlobalExcess(PluginConfig.MobSettings settings, List<Mob> mobs, List<Player> players) {
        if (settings.globalCap <= 0 || mobs.size() <= settings.globalCap) {
            return;
        }
        List<Mob> candidates = new ArrayList<>();
        for (Mob mob : mobs) {
            if (!exempt(mob, settings)) {
                candidates.add(mob);
            }
        }
        int removable = mobs.size() - settings.globalCap;
        candidates.sort(Comparator.comparingDouble(
                mob -> -nearestPlayerDistanceSquared(mob.getLocation(), players)));
        for (int i = 0; i < Math.min(removable, candidates.size()); i++) {
            candidates.get(i).remove();
            plugin.stats().inc(Stats.MOBS_REMOVED);
        }
    }

    private void recount(World world, List<Mob> mobs) {
        WorldCounts worldCounts = new WorldCounts();
        for (Mob mob : mobs) {
            long chunkKey = Keys.chunk(mob.getLocation());
            worldCounts.total++;
            worldCounts.perChunk.merge(chunkKey, 1, Integer::sum);
            countedChunks.put(mob.getUniqueId(), chunkKey);
        }
        counts.put(world.getUID(), worldCounts);
    }

    private boolean exempt(Mob mob, PluginConfig.MobSettings settings) {
        if (mob instanceof Villager || mob instanceof Player) {
            return true;
        }
        if (settings.exemptNamed && mob.getCustomName() != null) {
            return true;
        }
        if (settings.exemptTamed && mob instanceof Tameable tameable && tameable.isTamed()) {
            return true;
        }
        return settings.exemptPersistence && mob.isPersistent();
    }

    private double nearestPlayerDistanceSquared(Location location, List<Player> players) {
        double best = Double.MAX_VALUE;
        for (Player player : players) {
            if (!player.getWorld().equals(location.getWorld())) {
                continue;
            }
            best = Math.min(best, player.getLocation().distanceSquared(location));
        }
        return best;
    }

    public int total() {
        int total = 0;
        for (WorldCounts worldCounts : counts.values()) {
            total += worldCounts.total;
        }
        return total;
    }

    public int inChunk(World world, int chunkX, int chunkZ) {
        WorldCounts worldCounts = counts.get(world.getUID());
        return worldCounts == null ? 0 : worldCounts.perChunk.getOrDefault(Keys.chunk(chunkX, chunkZ), 0);
    }
}
