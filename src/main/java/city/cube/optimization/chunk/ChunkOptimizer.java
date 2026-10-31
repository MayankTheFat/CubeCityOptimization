package city.cube.optimization.chunk;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Applies per-player view distance ceilings and unloads idle chunks.
 *
 * The chunk budget is converted from a chunk count to a radius so
 * "max-chunks-per-player: 289" behaves like "at most a 17x17 area".
 */
public final class ChunkOptimizer {

    private final CubeCityOptimization plugin;
    private BukkitTask viewTask;
    private BukkitTask unloadTask;

    public ChunkOptimizer(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().chunk.enabled) {
            return;
        }
        viewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyViewDistances, 20L, 40L);
        long unloadPeriod = Math.max(200L, plugin.settings().chunk.unloadDelaySeconds * 20L);
        unloadTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> unloadIdleChunks(), 200L, unloadPeriod);
    }

    public void stop() {
        if (viewTask != null) {
            viewTask.cancel();
            viewTask = null;
        }
        if (unloadTask != null) {
            unloadTask.cancel();
            unloadTask = null;
        }
    }

    public void applyViewDistances() {
        PluginConfig.Settings settings = plugin.settings();
        if (!settings.chunk.enabled) {
            return;
        }

        int target = targetViewDistance(settings);
        int simulation = Math.min(target, settings.chunk.simulationDistance);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("cco.bypass.viewdistance")) {
                continue;
            }
            if (player.getViewDistance() != target) {
                player.setViewDistance(target);
            }
            if (player.getSimulationDistance() != simulation) {
                player.setSimulationDistance(simulation);
            }
        }
    }

    public int targetViewDistance(PluginConfig.Settings settings) {
        int target = settings.chunk.viewDistance;
        int budget = settings.chunk.maxChunksPerPlayer;
        if (budget > 0) {
            // (2r + 1)^2 chunks cover a square area; 289 -> 17x17 -> r = 8.
            int radius = (int) Math.floor((Math.sqrt(budget) - 1.0) / 2.0);
            target = Math.min(target, Math.max(2, radius));
        }
        if (settings.chunk.adaptive && plugin.tps().pressure()) {
            target = Math.min(target, settings.tick.pressureViewDistance);
        }
        return Math.max(2, Math.min(32, target));
    }

    /** Unloads idle chunks in every world. @return chunks unloaded during this pass */
    public int unloadIdleChunks() {
        int unloaded = 0;
        for (World world : Bukkit.getWorlds()) {
            unloaded += unloadIdleChunks(world);
        }
        return unloaded;
    }

    /** Unloads idle chunks in one world. @return chunks unloaded during this pass */
    public int unloadIdleChunks(World world) {
        PluginConfig.Settings settings = plugin.settings();
        if (!settings.chunk.enabled) {
            return 0;
        }

        int unloaded = 0;
        int budget = 800;
        int margin = 3;
        Location spawn = world.getSpawnLocation();
        int spawnX = spawn.getBlockX() >> 4;
        int spawnZ = spawn.getBlockZ() >> 4;

        for (Chunk chunk : world.getLoadedChunks()) {
            if (budget-- <= 0) {
                break;
            }
            if (Math.abs(chunk.getX() - spawnX) <= 2 && Math.abs(chunk.getZ() - spawnZ) <= 2) {
                continue;
            }
            if (world.isChunkInUse(chunk.getX(), chunk.getZ())) {
                continue;
            }
            if (hasPlayerNear(world, chunk.getX(), chunk.getZ(), margin)) {
                continue;
            }
            if (chunk.getTileEntities().length > 0) {
                continue;
            }
            if (chunk.getEntities().length > 0) {
                continue;
            }
            if (world.unloadChunk(chunk.getX(), chunk.getZ(), false)) {
                unloaded++;
                plugin.stats().inc(Stats.CHUNKS_UNLOADED);
            }
        }
        return unloaded;
    }

    private boolean hasPlayerNear(World world, int chunkX, int chunkZ, int marginChunks) {
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            int px = location.getBlockX() >> 4;
            int pz = location.getBlockZ() >> 4;
            if (Math.abs(px - chunkX) <= marginChunks && Math.abs(pz - chunkZ) <= marginChunks) {
                return true;
            }
        }
        return false;
    }
}
