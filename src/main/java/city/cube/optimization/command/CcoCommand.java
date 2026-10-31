package city.cube.optimization.command;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import city.cube.optimization.util.CleanupService;
import city.cube.optimization.util.Messages;
import city.cube.optimization.util.Numbers;
import city.cube.optimization.util.Totals;
import city.cube.optimization.util.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /cci — status, GUI, cleanup and diagnostics. */
public final class CcoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "status", "gui", "cleanup", "chunks", "hoppers", "tnt", "mobs", "stats", "reload", "help");

    private final CubeCityOptimization plugin;

    public CcoCommand(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "status" -> {
                if (require(sender, "cco.status")) {
                    status(sender);
                }
            }
            case "gui" -> {
                if (!require(sender, "cco.gui")) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    plugin.message(sender, "<red>The GUI is only available in-game.</red>");
                    return true;
                }
                plugin.gui().open(player);
            }
            case "cleanup" -> {
                if (require(sender, "cco.cleanup")) {
                    cleanup(sender, args);
                }
            }
            case "chunks" -> {
                if (require(sender, "cco.status")) {
                    chunks(sender);
                }
            }
            case "hoppers" -> {
                if (require(sender, "cco.status")) {
                    hoppers(sender);
                }
            }
            case "tnt" -> {
                if (require(sender, "cco.status")) {
                    tnt(sender);
                }
            }
            case "mobs" -> {
                if (require(sender, "cco.status")) {
                    mobs(sender);
                }
            }
            case "stats" -> {
                if (require(sender, "cco.stats")) {
                    stats(sender);
                }
            }
            case "reload" -> {
                if (!require(sender, "cco.reload")) {
                    return true;
                }
                plugin.reloadEverything();
                plugin.message(sender, "<green>Configuration reloaded.</green>");
            }
            case "help" -> help(sender);
            default -> plugin.message(sender, "<red>Unknown subcommand. Try <white>/cci help</white>.</red>");
        }
        return true;
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.message(sender, "<red>You need <white>" + permission + "</white> to do that.</red>");
        return false;
    }

    private void status(CommandSender sender) {
        // Served from the cached aggregate refreshed every 10s; never scans on the tick thread.
        PluginConfig.Settings settings = plugin.settings();
        Totals totals = plugin.totals();

        plugin.message(sender, "<dark_aqua><bold>CubeCity Optimization</bold></dark_aqua> <gray>v"
                + plugin.getDescription().getVersion() + "</gray>");
        plugin.message(sender, "TPS " + Numbers.tpsText(plugin.tps().tps())
                + " <gray>·</gray> MSPT <white>"
                + Numbers.oneDecimal(plugin.tps().mspt()) + "ms</white> <gray>·</gray> "
                + (plugin.tps().pressure() ? "<red>pressure mode</red>" : "<green>normal</green>"));
        plugin.message(sender, "Worlds <white>" + totals.worlds() + "</white> <gray>·</gray> Chunks <white>"
                + Numbers.ints(totals.chunks()) + "</white> <gray>·</gray> Entities <white>"
                + Numbers.ints(totals.entities()) + "</white> <gray>·</gray> Players <white>"
                + totals.players() + "</white>");
        plugin.message(sender, "Hoppers <white>" + Numbers.ints(totals.hoppers()) + "</white> <gray>·</gray> Dispensers <white>"
                + Numbers.ints(totals.dispensers()) + "</white> <gray>·</gray> Primed TNT <white>"
                + Numbers.ints(totals.tnt()) + "</white> <gray>·</gray> Mobs <white>"
                + Numbers.ints(totals.mobs()) + "</white>");
        plugin.message(sender, "Storage: " + (plugin.storage().ready()
                ? "<green>" + plugin.storage().describe() + "</green>"
                : "<red>disabled</red>")
                + " <gray>·</gray> View distance <white>" + settings.chunk.viewDistance + "</white>"
                + " <gray>·</gray> Mob cap <white>" + settings.mob.globalCap + "</white>");
    }

    private void chunks(CommandSender sender) {
        PluginConfig.ChunkSettings settings = plugin.settings().chunk;
        plugin.message(sender, "<aqua>Chunks</aqua> <gray>— view " + settings.viewDistance
                + ", simulation " + settings.simulationDistance + ", budget "
                + settings.maxChunksPerPlayer + ", unloaded counter "
                + Numbers.ints(plugin.stats().get(Stats.CHUNKS_UNLOADED)) + "</gray>");
        for (World world : Bukkit.getWorlds()) {
            WorldSnapshot snapshot = plugin.worldSnapshots().get(world.getName());
            if (snapshot == null) {
                continue;
            }
            plugin.message(sender, " <white>" + world.getName() + "</white> <gray>chunks</gray> <white>"
                    + Numbers.ints(snapshot.chunks) + "</white> <gray>entities</gray> <white>"
                    + Numbers.ints(snapshot.entities) + "</white>");
        }
        if (sender instanceof Player player) {
            plugin.message(sender, "Your view distance: <white>" + player.getViewDistance()
                    + "</white>, simulation: <white>" + player.getSimulationDistance() + "</white>");
        }
    }

    private void hoppers(CommandSender sender) {
        PluginConfig.HopperSettings settings = plugin.settings().hopper;
        plugin.message(sender, "<aqua>Hoppers</aqua> <gray>— cooldown " + settings.transferCooldownTicks
                + "t, max/chunk " + settings.maxPerChunk + ", chains "
                + (settings.disableChains ? "blocked" : "allowed") + "</gray>");
        plugin.message(sender, "Throttled <white>" + Numbers.ints(plugin.stats().get(Stats.HOPPERS_THROTTLED))
                + "</white> <gray>·</gray> Placement blocked <white>"
                + Numbers.ints(plugin.stats().get(Stats.HOPPERS_BLOCKED))
                + "</white> <gray>·</gray> Tracked <white>"
                + plugin.hopperOptimizer().trackedHoppers() + "</white>");
    }

    private void tnt(CommandSender sender) {
        PluginConfig.TntSettings settings = plugin.settings().tnt;
        plugin.message(sender, "<aqua>TNT</aqua> <gray>— max/chunk " + settings.maxPerChunk
                + ", merge radius " + Numbers.oneDecimal(settings.mergeRadius)
                + ", power cap " + Numbers.oneDecimal(settings.maxExplosionPower)
                + ", block cap " + settings.maxBlocksPerExplosion + "</gray>");
        plugin.message(sender, "Merged <white>" + Numbers.ints(plugin.stats().get(Stats.TNT_MERGED))
                + "</white> <gray>·</gray> Blocked <white>"
                + Numbers.ints(plugin.stats().get(Stats.TNT_BLOCKED))
                + "</white> <gray>·</gray> Blocks saved <white>"
                + Numbers.ints(plugin.stats().get(Stats.BLOCKS_SAVED)) + "</white>");
    }

    private void mobs(CommandSender sender) {
        PluginConfig.MobSettings settings = plugin.settings().mob;
        plugin.message(sender, "<aqua>Mobs</aqua> <gray>— world cap " + settings.globalCap
                + ", per-chunk " + settings.perChunkCap + ", hostile only "
                + settings.hostileOnly + "</gray>");
        plugin.message(sender, "Removed <white>" + Numbers.ints(plugin.stats().get(Stats.MOBS_REMOVED))
                + "</white> <gray>·</gray> Spawns denied <white>"
                + Numbers.ints(plugin.stats().get(Stats.SPAWNS_BLOCKED)) + "</white>");
    }

    private void stats(CommandSender sender) {
        if (!plugin.storage().ready()) {
            plugin.message(sender, "<red>Storage is disabled.</red> Check the console for the SQL error.");
            return;
        }
        plugin.message(sender, "<aqua>Storage</aqua> <gray>" + plugin.storage().describe() + "</gray>");

        // JDBC is blocking: run the SELECT off the server thread, reply on it.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> recent = plugin.storage().recentActions(10);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (recent.isEmpty()) {
                    plugin.message(sender, "<gray>No recorded actions yet.</gray>");
                    return;
                }
                plugin.message(sender, "<gray>Recent actions:</gray>");
                for (String line : recent) {
                    plugin.message(sender, " <dark_gray>·</dark_gray> <white>"
                            + Messages.escape(line) + "</white>");
                }
            });
        });
    }

    private void cleanup(CommandSender sender, String[] args) {
        World target = null;
        if (args.length > 1) {
            target = Bukkit.getWorld(args[1]);
            if (target == null) {
                plugin.message(sender, "<red>Unknown world <white>"
                        + Messages.escape(args[1]) + "</white>.</red>");
                return;
            }
        }

        double cost = plugin.settings().economy.cleanupCost;
        if (plugin.settings().economy.enabled && cost > 0.0 && plugin.economy().available()) {
            if (!(sender instanceof Player player)) {
                plugin.message(sender, "<gray>Console runs cleanup for free.</gray>");
            } else if (!plugin.economy().charge(player, cost)) {
                plugin.message(sender, "<red>You need " + plugin.economy().format(cost) + " to run this.</red>");
                return;
            } else {
                plugin.message(sender, "<gray>Charged " + plugin.economy().format(cost) + ".</gray>");
            }
        }

        CleanupService.CleanupReport report = target == null
                ? plugin.cleanupService().runAll(sender.getName())
                : plugin.cleanupService().run(target);

        plugin.message(sender, "<green>Cleanup complete</green> <gray>(" + report.millis() + "ms)</gray>");
        plugin.message(sender, "Items <white>" + report.items() + "</white> <gray>·</gray> Orbs <white>"
                + report.orbs() + "</white> <gray>·</gray> Entities <white>"
                + report.entities() + "</white> <gray>·</gray> Chunks unloaded <white>"
                + report.chunksUnloaded() + "</white>");
    }

    private void help(CommandSender sender) {
        plugin.message(sender, "<dark_aqua><bold>CubeCity Optimization</bold></dark_aqua> <gray>commands</gray>");
        plugin.message(sender, "<white>/cci status</white> <dark_gray>—</dark_gray> TPS, counts, storage");
        plugin.message(sender, "<white>/cci gui</white> <dark_gray>—</dark_gray> admin control panel");
        plugin.message(sender, "<white>/cci cleanup [world]</white> <dark_gray>—</dark_gray> clean drops, orbs, dense chunks");
        plugin.message(sender, "<white>/cci chunks|hoppers|tnt|mobs</white> <dark_gray>—</dark_gray> per-module details");
        plugin.message(sender, "<white>/cci stats</white> <dark_gray>—</dark_gray> storage history");
        plugin.message(sender, "<white>/cci reload</white> <dark_gray>—</dark_gray> reload config.yml");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cleanup")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(world.getName());
                }
            }
        }
        return out;
    }
}
