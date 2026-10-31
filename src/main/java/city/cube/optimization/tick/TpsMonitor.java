package city.cube.optimization.tick;

import city.cube.optimization.CubeCityOptimization;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Samples TPS/MSPT and decides when the plugin should enter "pressure mode".
 * Pressure mode is load shedding: shorter view distance, slower hoppers and
 * dispensers, no natural spawns. It exits only after the server recovers.
 */
public final class TpsMonitor {

    private static final int WINDOW = 15;
    private static final int ENTER_AFTER = 3;
    private static final int EXIT_AFTER = 5;

    private final CubeCityOptimization plugin;
    private final Deque<Double> history = new ArrayDeque<>();

    private volatile double tps = 20.0;
    private volatile double averageTps = 20.0;
    private volatile double mspt = 0.0;
    private volatile boolean pressure = false;
    private int lowSamples = 0;
    private int highSamples = 0;

    public TpsMonitor(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    public void sample() {
        double[] all = plugin.getServer().getTPS();
        this.tps = clamp(all.length > 0 ? all[0] : 20.0);
        this.mspt = Math.max(0.0, plugin.getServer().getAverageTickTime());

        history.addLast(tps);
        while (history.size() > WINDOW) {
            history.removeFirst();
        }
        this.averageTps = history.stream().mapToDouble(Double::doubleValue).average().orElse(tps);

        if (!plugin.settings().tick.enabled) {
            pressure = false;
            lowSamples = 0;
            highSamples = 0;
            return;
        }

        double threshold = plugin.settings().tick.tpsThreshold;
        if (averageTps < threshold) {
            lowSamples++;
            highSamples = 0;
            if (lowSamples >= ENTER_AFTER) {
                pressure = true;
            }
        } else if (averageTps >= threshold + 0.75) {
            highSamples++;
            lowSamples = 0;
            if (highSamples >= EXIT_AFTER) {
                pressure = false;
            }
        }
    }

    public double tps() {
        return tps;
    }

    public double averageTps() {
        return averageTps;
    }

    public double mspt() {
        return mspt;
    }

    public boolean pressure() {
        return pressure;
    }

    /** Cooldown multiplier for hoppers/dispensers under load. */
    public double cooldownMultiplier() {
        return pressure ? Math.max(1.0, plugin.settings().tick.pressureCooldownMultiplier) : 1.0;
    }

    public int effectiveCooldown(int baseTicks) {
        return (int) Math.round(baseTicks * cooldownMultiplier());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(20.0, value));
    }
}
