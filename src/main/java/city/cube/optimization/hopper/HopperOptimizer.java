package city.cube.optimization.hopper;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import city.cube.optimization.util.CooldownGate;
import city.cube.optimization.util.Keys;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hopper throttling. Bukkit exposes no "hopper check" hook, so the observable
 * behaviour is enforced instead: transfers that happen faster than the configured
 * cooldown are cancelled. Density and chaining are enforced at placement time.
 */
public final class HopperOptimizer implements Listener {

    private final CubeCityOptimization plugin;
    private final CooldownGate gate = new CooldownGate();
    private BukkitTask pruneTask;

    public HopperOptimizer(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().hopper.enabled) {
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
    public void onItemMove(InventoryMoveItemEvent event) {
        PluginConfig.HopperSettings settings = plugin.settings().hopper;
        if (!settings.enabled) {
            return;
        }

        Hopper hopper = hopperHolder(event.getSource());
        if (hopper == null) {
            hopper = hopperHolder(event.getDestination());
        }
        if (hopper == null) {
            return;
        }

        Location location = hopper.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        if (settings.onlyNearPlayers && !isNearPlayer(location, settings.playerRange)) {
            return;
        }

        int cooldownTicks = plugin.tps().effectiveCooldown(settings.transferCooldownTicks);
        String key = Keys.block(location);
        if (!gate.allow(key, cooldownTicks * 50L)) {
            event.setCancelled(true);
            plugin.stats().inc(Stats.HOPPERS_THROTTLED);
            if (plugin.settings().debug) {
                plugin.getLogger().info("Throttled hopper at " + key);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PluginConfig.HopperSettings settings = plugin.settings().hopper;
        if (!settings.enabled) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.HOPPER) {
            return;
        }

        if (settings.maxPerChunk > 0) {
            int existing = countHoppers(block.getChunk(), block.getLocation());
            if (existing >= settings.maxPerChunk) {
                event.setCancelled(true);
                plugin.message(event.getPlayer(), "<red>Hopper limit reached for this chunk ("
                        + settings.maxPerChunk + ").</red>");
                plugin.stats().inc(Stats.HOPPERS_BLOCKED);
                return;
            }
        }

        if (settings.disableChains && touchesHopper(block)) {
            event.setCancelled(true);
            plugin.message(event.getPlayer(),
                    "<red>Hopper chains are disabled on this server.</red>");
            plugin.stats().inc(Stats.HOPPERS_BLOCKED);
        }
    }

    private int countHoppers(Chunk chunk, Location exclude) {
        int count = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() == Material.HOPPER && !state.getLocation().equals(exclude)) {
                count++;
            }
        }
        return count;
    }

    private boolean touchesHopper(Block block) {
        BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            if (block.getRelative(face).getType() == Material.HOPPER) {
                return true;
            }
        }
        return false;
    }

    private Hopper hopperHolder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder;
        try {
            holder = inventory.getHolder();
        } catch (RuntimeException err) {
            return null;
        }
        return holder instanceof Hopper hopper ? hopper : null;
    }

    private boolean isNearPlayer(Location location, int range) {
        double rangeSquared = (double) range * range;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private void prune() {
        gate.prune(System.currentTimeMillis() - 10 * 60_000L);
    }

    public int trackedHoppers() {
        return gate.size();
    }
}
