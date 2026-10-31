package city.cube.optimization.util;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.config.PluginConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

/** Manual and scheduled cleanup: loose items, XP orbs, entity density, idle chunks. */
public final class CleanupService {

    private final CubeCityOptimization plugin;
    private BukkitTask task;

    public CleanupService(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        PluginConfig.CleanupSettings settings = plugin.settings().cleanup;
        if (!settings.enabled || settings.intervalSeconds <= 0) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> runAll("scheduled"),
                20L * settings.intervalSeconds, 20L * settings.intervalSeconds);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public CleanupReport runAll(String scope) {
        long started = System.currentTimeMillis();
        int items = 0;
        int orbs = 0;
        int entities = 0;

        for (World world : Bukkit.getWorlds()) {
            Part part = runWorld(world);
            items += part.items;
            orbs += part.orbs;
            entities += part.entities;
        }

        int chunks = plugin.chunkOptimizer().unloadIdleChunks();
        long elapsed = System.currentTimeMillis() - started;

        plugin.stats().inc(Stats.CLEANUPS);
        plugin.stats().add(Stats.ITEMS_CLEANED, items);
        plugin.stats().add(Stats.ORBS_CLEANED, orbs);
        plugin.stats().add(Stats.ENTITIES_CLEANED, entities);

        plugin.storage().action("cleanup", scope + ": " + items + " items, " + orbs + " orbs, "
                + entities + " entities, " + chunks + " chunks");

        return new CleanupReport(scope, items, orbs, entities, chunks, elapsed);
    }

    public CleanupReport run(World world) {
        long started = System.currentTimeMillis();
        Part part = runWorld(world);
        int chunks = plugin.chunkOptimizer().unloadIdleChunks(world);

        plugin.stats().inc(Stats.CLEANUPS);
        plugin.stats().add(Stats.ITEMS_CLEANED, part.items);
        plugin.stats().add(Stats.ORBS_CLEANED, part.orbs);
        plugin.stats().add(Stats.ENTITIES_CLEANED, part.entities);
        plugin.storage().action("cleanup", world.getName() + ": " + part.items + " items, "
                + part.orbs + " orbs, " + part.entities + " entities");

        return new CleanupReport(world.getName(), part.items, part.orbs, part.entities,
                chunks, System.currentTimeMillis() - started);
    }

    private Part runWorld(World world) {
        PluginConfig.CleanupSettings settings = plugin.settings().cleanup;
        Part part = new Part();

        int itemAge = settings.itemMinAgeSeconds * 20;
        int orbAge = settings.xpMinAgeSeconds * 20;
        List<Entity> remaining = new ArrayList<>();

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item item) {
                if (settings.removeDroppedItems && item.getTicksLived() > itemAge) {
                    item.remove();
                    part.items++;
                    continue;
                }
                remaining.add(entity);
            } else if (entity instanceof ExperienceOrb orb) {
                if (settings.removeXpOrbs && orb.getTicksLived() > orbAge) {
                    orb.remove();
                    part.orbs++;
                    continue;
                }
                remaining.add(entity);
            } else {
                remaining.add(entity);
            }
        }

        if (settings.maxEntitiesPerChunk > 0) {
            Map<Long, List<Entity>> byChunk = new HashMap<>();
            for (Entity entity : remaining) {
                if (entity instanceof Player || settings.keepEntities.contains(entity.getType())) {
                    continue;
                }
                byChunk.computeIfAbsent(Keys.chunk(entity.getLocation()), ignored -> new ArrayList<>()).add(entity);
            }
            for (List<Entity> entities : byChunk.values()) {
                if (entities.size() <= settings.maxEntitiesPerChunk) {
                    continue;
                }
                entities.sort(Comparator.comparingInt(Entity::getTicksLived).reversed());
                int excess = entities.size() - settings.maxEntitiesPerChunk;
                for (int i = 0; i < excess; i++) {
                    Entity entity = entities.get(i);
                    if (entity instanceof Villager) {
                        continue;
                    }
                    entity.remove();
                    part.entities++;
                }
            }
        }

        return part;
    }

    public record CleanupReport(String scope, int items, int orbs, int entities,
                                int chunksUnloaded, long millis) {
    }

    private static final class Part {
        int items;
        int orbs;
        int entities;
    }
}
