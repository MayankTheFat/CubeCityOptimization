package city.cube.optimization.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the config.yml shipped in the jar actually matches the values
 * documented in README.md, so a typo in the resource cannot silently fall back
 * to a hardcoded default.
 */
class ShippedConfigTest {

    private static final File CONFIG_FILE = new File("src/main/resources/config.yml");

    private static YamlConfiguration loadShipped() {
        return YamlConfiguration.loadConfiguration(CONFIG_FILE);
    }

    @Test
    void shippedConfigParsesToDocumentedDefaults() {
        PluginConfig.Settings settings = new PluginConfig.Settings(loadShipped());

        assertFalse(settings.debug, "general.debug");
        assertTrue(settings.chunk.enabled, "chunks.enabled");
        assertEquals(6, settings.chunk.viewDistance, "chunks.view-distance");
        assertEquals(289, settings.chunk.maxChunksPerPlayer, "chunks.max-chunks-per-player");
        assertEquals(8, settings.hopper.transferCooldownTicks, "hoppers.transfer-cooldown-ticks");
        assertEquals(24, settings.hopper.maxPerChunk, "hoppers.max-per-chunk");
        assertEquals(10, settings.dispenser.cooldownTicks, "dispensers.cooldown-ticks");
        assertEquals(16, settings.tnt.maxPerChunk, "tnt.max-per-chunk");
        assertEquals(4.0, settings.tnt.mergeRadius, 0.0001, "tnt.merge-radius");
        assertEquals(400, settings.mob.globalCap, "mobs.global-cap");
        assertEquals(12, settings.mob.perChunkCap, "mobs.per-chunk-cap");
        assertFalse(settings.mob.exemptPersistence, "mobs.exempt-persistence");
        assertEquals("sqlite", settings.storage.type, "storage.type");
        assertEquals("cubecity.db", settings.storage.sqliteFile, "storage.sqlite.file");
        assertEquals("CubeCity Optimization", settings.gui.title, "gui.title");
        assertFalse(settings.economy.enabled, "economy.enabled");
    }

    @Test
    void shippedCleanupKeepsProtectedEntityTypes() {
        PluginConfig.Settings settings = new PluginConfig.Settings(loadShipped());

        assertTrue(settings.cleanup.keepEntities.contains(EntityType.VILLAGER),
                "cleanup.keep-entities should contain VILLAGER");
        assertTrue(settings.cleanup.keepEntities.contains(EntityType.ARMOR_STAND),
                "cleanup.keep-entities should contain ARMOR_STAND");
    }

    @Test
    void overriddenValuesWinOverDocumentedDefaults() {
        YamlConfiguration config = loadShipped();
        config.set("general.debug", true);
        config.set("chunks.view-distance", 10);
        config.set("mobs.per-chunk-cap", 3);

        PluginConfig.Settings settings = new PluginConfig.Settings(config);

        assertTrue(settings.debug, "general.debug override");
        assertEquals(10, settings.chunk.viewDistance, "chunks.view-distance override");
        assertEquals(3, settings.mob.perChunkCap, "mobs.per-chunk-cap override");
    }

    @Test
    void shippedConfigHasNoUnknownRootKeys() {
        Set<String> expected = Set.of(
                "general", "chunks", "cleanup", "hoppers", "dispensers", "tick",
                "tnt", "mobs", "gui", "storage", "economy", "messages");

        assertEquals(expected, loadShipped().getKeys(false),
                "config.yml root keys drifted from the documented set");
    }
}
