package city.cube.optimization.hooks;

import city.cube.optimization.CubeCityOptimization;
import org.bukkit.Bukkit;

/** Guards optional integrations so a missing plugin never breaks startup. */
public final class Hooks {

    private Hooks() {
    }

    public static void registerPlaceholders(CubeCityOptimization plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            if (new CubeCityExpansion(plugin).register()) {
                plugin.getLogger().info("PlaceholderAPI expansion registered (%cci_*%).");
            }
        } catch (Throwable err) {
            plugin.getLogger().warning("PlaceholderAPI hook failed: " + err.getMessage());
        }
    }
}
