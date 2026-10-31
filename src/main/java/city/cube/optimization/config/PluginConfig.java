package city.cube.optimization.config;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Immutable snapshot of config.yml. Rebuilt on /cci reload and whenever the GUI
 * writes a value, so managers can read it without locking.
 */
public final class PluginConfig {

    private final JavaPlugin plugin;
    private volatile Settings settings;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Settings settings() {
        return settings;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.settings = new Settings(plugin.getConfig());
    }

    /** Persist a single value and refresh the snapshot (used by the GUI). */
    public void update(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        this.settings = new Settings(plugin.getConfig());
    }

    public static final class Settings {
        public final String prefix;
        public final boolean debug;
        public final int sampleIntervalTicks;

        public final ChunkSettings chunk;
        public final CleanupSettings cleanup;
        public final HopperSettings hopper;
        public final DispenserSettings dispenser;
        public final TickSettings tick;
        public final TntSettings tnt;
        public final MobSettings mob;
        public final GuiSettings gui;
        public final StorageSettings storage;
        public final EconomySettings economy;

        Settings(FileConfiguration c) {
            this.prefix = c.getString("messages.prefix", "<dark_aqua><bold>CubeCity</bold></dark_aqua> <gray>»</gray> ");
            this.debug = c.getBoolean("general.debug", false);
            this.sampleIntervalTicks = Math.max(20, c.getInt("general.sample-interval-ticks", 40));
            this.chunk = new ChunkSettings(c);
            this.cleanup = new CleanupSettings(c);
            this.hopper = new HopperSettings(c);
            this.dispenser = new DispenserSettings(c);
            this.tick = new TickSettings(c);
            this.tnt = new TntSettings(c);
            this.mob = new MobSettings(c);
            this.gui = new GuiSettings(c);
            this.storage = new StorageSettings(c);
            this.economy = new EconomySettings(c);
        }
    }

    public static final class ChunkSettings {
        public final boolean enabled;
        public final int viewDistance;
        public final int simulationDistance;
        public final int maxChunksPerPlayer;
        public final int unloadDelaySeconds;
        public final boolean adaptive;

        ChunkSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("chunks.enabled", true);
            this.viewDistance = Math.min(32, Math.max(2, c.getInt("chunks.view-distance", 6)));
            this.simulationDistance = Math.min(32, Math.max(2, c.getInt("chunks.simulation-distance", 4)));
            this.maxChunksPerPlayer = Math.max(0, c.getInt("chunks.max-chunks-per-player", 289));
            this.unloadDelaySeconds = Math.max(5, c.getInt("chunks.unload-delay-seconds", 30));
            this.adaptive = c.getBoolean("chunks.adaptive", true);
        }
    }

    public static final class CleanupSettings {
        public final boolean enabled;
        public final int intervalSeconds;
        public final boolean removeDroppedItems;
        public final int itemMinAgeSeconds;
        public final boolean removeXpOrbs;
        public final int xpMinAgeSeconds;
        public final int maxEntitiesPerChunk;
        public final Set<EntityType> keepEntities;

        CleanupSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("cleanup.enabled", true);
            this.intervalSeconds = Math.max(0, c.getInt("cleanup.interval-seconds", 300));
            this.removeDroppedItems = c.getBoolean("cleanup.remove-dropped-items", true);
            this.itemMinAgeSeconds = Math.max(0, c.getInt("cleanup.item-min-age-seconds", 120));
            this.removeXpOrbs = c.getBoolean("cleanup.remove-xp-orbs", true);
            this.xpMinAgeSeconds = Math.max(0, c.getInt("cleanup.xp-min-age-seconds", 120));
            this.maxEntitiesPerChunk = Math.max(0, c.getInt("cleanup.max-entities-per-chunk", 64));

            Set<EntityType> keep = EnumSet.noneOf(EntityType.class);
            List<String> raw = c.getStringList("cleanup.keep-entities");
            for (String name : raw) {
                try {
                    keep.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown entity type: skip rather than break startup.
                }
            }
            if (keep.isEmpty()) {
                keep.addAll(EnumSet.of(EntityType.ITEM_FRAME, EntityType.ARMOR_STAND,
                        EntityType.VILLAGER, EntityType.ITEM_DISPLAY, EntityType.BLOCK_DISPLAY));
            }
            this.keepEntities = keep;
        }
    }

    public static final class HopperSettings {
        public final boolean enabled;
        public final int transferCooldownTicks;
        public final int maxPerChunk;
        public final boolean disableChains;
        public final boolean onlyNearPlayers;
        public final int playerRange;

        HopperSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("hoppers.enabled", true);
            this.transferCooldownTicks = Math.max(1, c.getInt("hoppers.transfer-cooldown-ticks", 8));
            this.maxPerChunk = Math.max(0, c.getInt("hoppers.max-per-chunk", 24));
            this.disableChains = c.getBoolean("hoppers.disable-chains", true);
            this.onlyNearPlayers = c.getBoolean("hoppers.only-near-players", true);
            this.playerRange = Math.max(16, c.getInt("hoppers.player-range", 96));
        }
    }

    public static final class DispenserSettings {
        public final boolean enabled;
        public final int cooldownTicks;
        public final int maxPerChunk;
        public final int maxPerSecondPerChunk;

        DispenserSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("dispensers.enabled", true);
            this.cooldownTicks = Math.max(0, c.getInt("dispensers.cooldown-ticks", 10));
            this.maxPerChunk = Math.max(0, c.getInt("dispensers.max-per-chunk", 16));
            this.maxPerSecondPerChunk = Math.max(0, c.getInt("dispensers.max-per-second-per-chunk", 8));
        }
    }

    public static final class TickSettings {
        public final boolean enabled;
        public final double tpsThreshold;
        public final int pressureViewDistance;
        public final double pressureCooldownMultiplier;
        public final boolean pressureSpawnThrottle;

        TickSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("tick.enabled", true);
            this.tpsThreshold = Math.max(1.0, c.getDouble("tick.tps-threshold", 17.0));
            this.pressureViewDistance = Math.max(2, c.getInt("tick.pressure-view-distance", 4));
            this.pressureCooldownMultiplier = Math.max(1.0, c.getDouble("tick.pressure-cooldown-multiplier", 2.0));
            this.pressureSpawnThrottle = c.getBoolean("tick.pressure-spawn-throttle", true);
        }
    }

    public static final class TntSettings {
        public final boolean enabled;
        public final int maxPerChunk;
        public final double mergeRadius;
        public final double maxExplosionPower;
        public final int maxBlocksPerExplosion;

        TntSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("tnt.enabled", true);
            this.maxPerChunk = Math.max(0, c.getInt("tnt.max-per-chunk", 16));
            this.mergeRadius = Math.max(0.0, c.getDouble("tnt.merge-radius", 4.0));
            this.maxExplosionPower = Math.max(0.5, c.getDouble("tnt.max-explosion-power", 6.0));
            this.maxBlocksPerExplosion = Math.max(0, c.getInt("tnt.max-blocks-per-explosion", 120));
        }
    }

    public static final class MobSettings {
        public final boolean enabled;
        public final int globalCap;
        public final int perChunkCap;
        public final boolean autoRemoveExcess;
        public final int removeIntervalSeconds;
        public final boolean hostileOnly;
        public final boolean exemptNamed;
        public final boolean exemptTamed;
        public final boolean exemptPersistence;

        MobSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("mobs.enabled", true);
            this.globalCap = Math.max(0, c.getInt("mobs.global-cap", 400));
            this.perChunkCap = Math.max(0, c.getInt("mobs.per-chunk-cap", 12));
            this.autoRemoveExcess = c.getBoolean("mobs.auto-remove-excess", true);
            this.removeIntervalSeconds = Math.max(10, c.getInt("mobs.remove-interval-seconds", 60));
            this.hostileOnly = c.getBoolean("mobs.hostile-only", false);
            this.exemptNamed = c.getBoolean("mobs.exempt-named", true);
            this.exemptTamed = c.getBoolean("mobs.exempt-tamed", true);
            this.exemptPersistence = c.getBoolean("mobs.exempt-persistence", false);
        }
    }

    public static final class GuiSettings {
        public final boolean enabled;
        public final String title;

        GuiSettings(FileConfiguration c) {
            this.enabled = c.getBoolean("gui.enabled", true);
            this.title = c.getString("gui.title", "CubeCity Optimization");
        }
    }

    public static final class StorageSettings {
        public final String type;
        public final String sqliteFile;
        public final String host;
        public final int port;
        public final String database;
        public final String username;
        public final String password;
        public final boolean useSsl;
        public final int saveIntervalSeconds;
        public final int retentionDays;

        StorageSettings(FileConfiguration c) {
            this.type = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
            this.sqliteFile = c.getString("storage.sqlite.file", "cubecity.db");
            this.host = c.getString("storage.mysql.host", "localhost");
            this.port = c.getInt("storage.mysql.port", 3306);
            this.database = c.getString("storage.mysql.database", "cubecity");
            this.username = c.getString("storage.mysql.username", "root");
            this.password = c.getString("storage.mysql.password", "");
            this.useSsl = c.getBoolean("storage.mysql.use-ssl", false);
            this.saveIntervalSeconds = Math.max(15, c.getInt("storage.save-interval-seconds", 60));
            this.retentionDays = Math.max(0, c.getInt("storage.retention-days", 30));
        }

        public boolean mysql() {
            return "mysql".equals(type);
        }
    }

    public static final class EconomySettings {
        public final boolean enabled;
        public final double cleanupCost;

        EconomySettings(FileConfiguration c) {
            this.enabled = c.getBoolean("economy.enabled", false);
            this.cleanupCost = Math.max(0.0, c.getDouble("economy.cleanup-cost", 0.0));
        }
    }
}
