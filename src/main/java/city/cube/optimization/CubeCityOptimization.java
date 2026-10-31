package city.cube.optimization;

import city.cube.optimization.chunk.ChunkOptimizer;
import city.cube.optimization.command.CcoCommand;
import city.cube.optimization.config.PluginConfig;
import city.cube.optimization.dispenser.DispenserOptimizer;
import city.cube.optimization.gui.GuiManager;
import city.cube.optimization.hopper.HopperOptimizer;
import city.cube.optimization.hooks.EconomyHook;
import city.cube.optimization.hooks.Hooks;
import city.cube.optimization.mob.MobOptimizer;
import city.cube.optimization.storage.SnapshotJson;
import city.cube.optimization.storage.SqlStatsStorage;
import city.cube.optimization.storage.StatsSnapshot;
import city.cube.optimization.storage.StatsStorage;
import city.cube.optimization.tick.TpsMonitor;
import city.cube.optimization.tnt.TntOptimizer;
import city.cube.optimization.util.CleanupService;
import city.cube.optimization.util.Messages;
import city.cube.optimization.util.Totals;
import city.cube.optimization.util.WorldSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Plugin entry point: wires managers, tasks and optional integrations. */
public final class CubeCityOptimization extends JavaPlugin {

    private PluginConfig config;
    private Stats stats;
    private TpsMonitor tps;
    private StatsStorage storage;
    private EconomyHook economy;

    private ChunkOptimizer chunkOptimizer;
    private HopperOptimizer hopperOptimizer;
    private DispenserOptimizer dispenserOptimizer;
    private TntOptimizer tntOptimizer;
    private MobOptimizer mobOptimizer;
    private CleanupService cleanupService;
    private GuiManager gui;

    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile Totals totals = Totals.EMPTY;
    private volatile Map<String, WorldSnapshot> worldSnapshots = Map.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new PluginConfig(this);
        config.reload();

        stats = new Stats();
        tps = new TpsMonitor(this);
        economy = new EconomyHook(this);
        economy.setup();

        storage = new SqlStatsStorage(settings().storage, getDataFolder(), getLogger());
        Bukkit.getScheduler().runTaskAsynchronously(this, storage::init);

        chunkOptimizer = new ChunkOptimizer(this);
        hopperOptimizer = new HopperOptimizer(this);
        dispenserOptimizer = new DispenserOptimizer(this);
        tntOptimizer = new TntOptimizer(this);
        mobOptimizer = new MobOptimizer(this);
        cleanupService = new CleanupService(this);
        gui = new GuiManager(this);

        getServer().getPluginManager().registerEvents(hopperOptimizer, this);
        getServer().getPluginManager().registerEvents(dispenserOptimizer, this);
        getServer().getPluginManager().registerEvents(tntOptimizer, this);
        getServer().getPluginManager().registerEvents(mobOptimizer, this);
        getServer().getPluginManager().registerEvents(gui, this);

        PluginCommand command = getCommand("cci");
        if (command != null) {
            CcoCommand executor = new CcoCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Command /cci is missing from plugin.yml!");
        }

        startTasks();
        Hooks.registerPlaceholders(this);

        // Vault providers often register after us; retry once and warn if still missing.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!economy.available()) {
                economy.setup();
            }
            if (settings().economy.enabled && !economy.available()) {
                getLogger().warning("economy.enabled is true but no Vault economy provider registered.");
            }
        }, 60L);

        getLogger().info("Enabled: storage=" + settings().storage.type
                + ", view-distance=" + settings().chunk.viewDistance
                + ", mob-cap=" + settings().mob.globalCap
                + ", hopper-cooldown=" + settings().hopper.transferCooldownTicks + "t");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (storage != null) {
            storage.close();
        }
        getLogger().info("CubeCityOptimization disabled.");
    }

    public void startTasks() {
        stopTasks();

        long sampleTicks = settings().sampleIntervalTicks;
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, tps::sample, 20L, sampleTicks));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, this::refreshTotals, 40L, 200L));

        long snapshotTicks = Math.max(20L * 15, settings().storage.saveIntervalSeconds * 20L);
        tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::writeSnapshot,
                20L * 20, snapshotTicks));

        chunkOptimizer.start();
        hopperOptimizer.start();
        dispenserOptimizer.start();
        tntOptimizer.start();
        mobOptimizer.start();
        cleanupService.start();

        refreshTotals();
    }

    public void stopTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (chunkOptimizer != null) {
            chunkOptimizer.stop();
            hopperOptimizer.stop();
            dispenserOptimizer.stop();
            tntOptimizer.stop();
            mobOptimizer.stop();
            cleanupService.stop();
        }
    }

    public void restartTasks() {
        startTasks();
    }

    public void reloadEverything() {
        config.reload();
        startTasks();
        getLogger().info("Configuration reloaded.");
    }

    public void refreshTotals() {
        List<WorldSnapshot> snapshots = new ArrayList<>();
        Map<String, WorldSnapshot> byWorld = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            WorldSnapshot snapshot = WorldSnapshot.of(world);
            snapshots.add(snapshot);
            byWorld.put(world.getName(), snapshot);
        }
        this.worldSnapshots = Map.copyOf(byWorld);
        this.totals = Totals.of(snapshots);
    }

    public Map<String, WorldSnapshot> worldSnapshots() {
        return worldSnapshots;
    }

    private void writeSnapshot() {
        Totals current = totals;
        String counters = SnapshotJson.counters(stats.snapshot());
        storage.record(new StatsSnapshot(System.currentTimeMillis(), tps.tps(), tps.mspt(),
                current.worlds(), current.chunks(), current.entities(), current.hoppers(),
                current.dispensers(), current.tnt(), current.mobs(), counters));
    }

    public void message(CommandSender sender, String miniMessage) {
        sender.sendMessage(Messages.parse(settings().prefix + miniMessage));
    }

    public PluginConfig.Settings settings() {
        return config.settings();
    }

    public PluginConfig config() {
        return config;
    }

    public Stats stats() {
        return stats;
    }

    public TpsMonitor tps() {
        return tps;
    }

    public StatsStorage storage() {
        return storage;
    }

    public EconomyHook economy() {
        return economy;
    }

    public ChunkOptimizer chunkOptimizer() {
        return chunkOptimizer;
    }

    public HopperOptimizer hopperOptimizer() {
        return hopperOptimizer;
    }

    public DispenserOptimizer dispenserOptimizer() {
        return dispenserOptimizer;
    }

    public TntOptimizer tntOptimizer() {
        return tntOptimizer;
    }

    public MobOptimizer mobOptimizer() {
        return mobOptimizer;
    }

    public CleanupService cleanupService() {
        return cleanupService;
    }

    public GuiManager gui() {
        return gui;
    }

    public Totals totals() {
        return totals;
    }
}
