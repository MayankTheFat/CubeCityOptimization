package city.cube.optimization.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key time gate. Used by hopper and dispenser throttling: the first call is
 * always allowed, later calls inside the interval are rejected and do not extend
 * the window.
 */
public final class CooldownGate {

    private final Map<String, Long> lastAllowed = new ConcurrentHashMap<>();

    public boolean allow(String key, long nowMillis, long intervalMillis) {
        if (intervalMillis <= 0) {
            return true;
        }
        Long previous = lastAllowed.get(key);
        if (previous != null && nowMillis - previous < intervalMillis) {
            return false;
        }
        lastAllowed.put(key, nowMillis);
        return true;
    }

    public boolean allow(String key, long intervalMillis) {
        return allow(key, System.currentTimeMillis(), intervalMillis);
    }

    public Long lastAllowed(String key) {
        return lastAllowed.get(key);
    }

    public int size() {
        return lastAllowed.size();
    }

    public void prune(long cutoffMillis) {
        lastAllowed.entrySet().removeIf(entry -> entry.getValue() < cutoffMillis);
    }

    public void clear() {
        lastAllowed.clear();
    }
}
