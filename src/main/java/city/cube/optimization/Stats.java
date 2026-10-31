package city.cube.optimization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counters for everything the plugin actually changed. */
public final class Stats {

    public static final String CHUNKS_UNLOADED = "chunks-unloaded";
    public static final String CLEANUPS = "cleanups";
    public static final String ENTITIES_CLEANED = "entities-cleaned";
    public static final String ITEMS_CLEANED = "items-cleaned";
    public static final String ORBS_CLEANED = "orbs-cleaned";
    public static final String HOPPERS_THROTTLED = "hoppers-throttled";
    public static final String HOPPERS_BLOCKED = "hoppers-blocked";
    public static final String DISPENSERS_THROTTLED = "dispensers-throttled";
    public static final String DISPENSERS_BLOCKED = "dispensers-blocked";
    public static final String DISPENSER_SPAM = "dispenser-spam-blocked";
    public static final String TNT_MERGED = "tnt-merged";
    public static final String TNT_BLOCKED = "tnt-blocked";
    public static final String EXPLOSIONS_OPTIMIZED = "explosions-optimized";
    public static final String BLOCKS_SAVED = "blocks-saved";
    public static final String MOBS_REMOVED = "mobs-removed";
    public static final String SPAWNS_BLOCKED = "spawns-blocked";

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void inc(String key) {
        add(key, 1);
    }

    public void add(String key, long delta) {
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(delta);
    }

    public long get(String key) {
        AtomicLong value = counters.get(key);
        return value == null ? 0L : value.get();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new LinkedHashMap<>();
        counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.put(entry.getKey(), entry.getValue().get()));
        return out;
    }
}
