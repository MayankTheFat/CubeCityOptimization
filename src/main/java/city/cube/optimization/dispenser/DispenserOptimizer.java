package city.cube.optimization.dispenser;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import city.cube.optimization.util.CooldownGate;
import city.cube.optimization.util.Keys;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Dispenser/dropper rate limiting. Dispense is the moment the server performs
 * work, so the cooldown is applied by cancelling dispenses that arrive too soon
 * after the previous one from the same block.
 */
public final class DispenserOptimizer implements Listener {

    private static final class Window {
        long second;
        int count;
    }

    private final CubeCityOptimization plugin;
    private final CooldownGate gate = new CooldownGate();
    private final Map<String, Window> chunkWindows = new ConcurrentHashMap<>();
    private BukkitTask pruneTask;

    public DispenserOptimizer(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().dispenser.enabled) {
            return;
        }
        pruneTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::prune, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        PluginConfig.DispenserSettings settings = plugin.settings().dispenser;
        if (!settings.enabled) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.DISPENSER && block.getType() != Material.DROPPER) {
            return;
        }
        Location location = block.getLocation();
        long now = System.currentTimeMillis();

        if (settings.cooldownTicks > 0) {
            int cooldownTicks = plugin.tps().effectiveCooldown(settings.cooldownTicks);
            if (!gate.allow(Keys.block(location), now, cooldownTicks * 50L)) {
                event.setCancelled(true);
                plugin.stats().inc(Stats.DISPENSERS_THROTTLED);
                return;
            }
        }

        if (settings.maxPerSecondPerChunk > 0) {
            String chunkKey = location.getWorld().getUID() + ":" + Keys.chunk(location);
            long second = now / 1000L;
            Window window = chunkWindows.computeIfAbsent(chunkKey, ignored -> new Window());
            synchronized (window) {
                if (window.second != second) {
                    window.second = second;
                    window.count = 0;
                }
                window.count++;
                if (window.count > settings.maxPerSecondPerChunk) {
                    event.setCancelled(true);
                    plugin.stats().inc(Stats.DISPENSER_SPAM);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PluginConfig.DispenserSettings settings = plugin.settings().dispenser;
        if (!settings.enabled || settings.maxPerChunk <= 0) {
            return;
        }
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        if (type != Material.DISPENSER && type != Material.DROPPER) {
            return;
        }

        int existing = 0;
        Chunk chunk = block.getChunk();
        for (BlockState state : chunk.getTileEntities()) {
            Material stateType = state.getType();
            if (stateType == Material.DISPENSER || stateType == Material.DROPPER) {
                if (!state.getLocation().equals(block.getLocation())) {
                    existing++;
                }
            }
        }

        if (existing >= settings.maxPerChunk) {
            event.setCancelled(true);
            plugin.message(event.getPlayer(), "<red>Dispenser limit reached for this chunk ("
                    + settings.maxPerChunk + ").</red>");
            plugin.stats().inc(Stats.DISPENSERS_BLOCKED);
        }
    }

    private void prune() {
        gate.prune(System.currentTimeMillis() - 10 * 60_000L);
        long oldestSecond = System.currentTimeMillis() / 1000L - 5;
        chunkWindows.entrySet().removeIf(entry -> entry.getValue().second < oldestSecond);
    }

    public int trackedDispensers() {
        return gate.size();
    }
}
