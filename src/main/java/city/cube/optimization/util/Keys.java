package city.cube.optimization.util;

import org.bukkit.Chunk;
import org.bukkit.Location;

/** Compact map keys for chunk/block lookups. */
public final class Keys {

    private Keys() {
    }

    public static long chunk(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static long chunk(Chunk chunk) {
        return chunk(chunk.getX(), chunk.getZ());
    }

    public static long chunk(Location location) {
        return chunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public static String block(Location location) {
        return location.getWorld().getName()
                + ':' + location.getBlockX()
                + ':' + location.getBlockY()
                + ':' + location.getBlockZ();
    }
}
