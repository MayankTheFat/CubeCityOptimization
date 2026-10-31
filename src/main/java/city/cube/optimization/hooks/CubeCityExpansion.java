package city.cube.optimization.hooks;

import city.cube.optimization.CubeCityOptimization;
import city.cube.optimization.Stats;
import city.cube.optimization.util.Numbers;
import city.cube.optimization.util.Totals;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** %cci_*% placeholders. Registered only when PlaceholderAPI is installed. */
public final class CubeCityExpansion extends PlaceholderExpansion {

    private final CubeCityOptimization plugin;

    public CubeCityExpansion(CubeCityOptimization plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cci";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CubeCity";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Totals totals = plugin.totals();
        Stats stats = plugin.stats();

        return switch (key) {
            case "tps" -> Numbers.oneDecimal(plugin.tps().tps());
            case "tps_avg" -> Numbers.oneDecimal(plugin.tps().averageTps());
            case "tps_colored" -> Numbers.tpsText(plugin.tps().averageTps());
            case "mspt" -> Numbers.oneDecimal(plugin.tps().mspt()) + "ms";
            case "pressure" -> plugin.tps().pressure() ? "true" : "false";
            case "status" -> plugin.tps().pressure() ? "pressure" : "normal";
            case "players" -> Integer.toString(totals.players());
            case "worlds" -> Integer.toString(totals.worlds());
            case "chunks" -> Numbers.ints(totals.chunks());
            case "entities" -> Numbers.ints(totals.entities());
            case "mobs" -> Numbers.ints(totals.mobs());
            case "hoppers" -> Numbers.ints(totals.hoppers());
            case "dispensers" -> Numbers.ints(totals.dispensers());
            case "tnt" -> Numbers.ints(totals.tnt());
            case "mobs_removed" -> Numbers.ints(stats.get(Stats.MOBS_REMOVED));
            case "hoppers_throttled" -> Numbers.ints(stats.get(Stats.HOPPERS_THROTTLED));
            case "dispensers_throttled" -> Numbers.ints(stats.get(Stats.DISPENSERS_THROTTLED));
            case "tnt_merged" -> Numbers.ints(stats.get(Stats.TNT_MERGED));
            case "blocks_saved" -> Numbers.ints(stats.get(Stats.BLOCKS_SAVED));
            case "chunks_unloaded" -> Numbers.ints(stats.get(Stats.CHUNKS_UNLOADED));
            case "cleanups" -> Numbers.ints(stats.get(Stats.CLEANUPS));
            case "storage" -> plugin.storage().ready() ? plugin.storage().describe() : "disabled";
            default -> null;
        };
    }
}
