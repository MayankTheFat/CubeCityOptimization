package city.cube.optimization.util;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TNTPrimed;

/** Point-in-time counters for a world, used by /cci status and the placeholders. */
public final class WorldSnapshot {

    public final String world;
    public final int chunks;
    public final int entities;
    public final int mobs;
    public final int hoppers;
    public final int dispensers;
    public final int primedTnt;
    public final int players;

    private WorldSnapshot(String world, int chunks, int entities, int mobs, int hoppers,
                          int dispensers, int primedTnt, int players) {
        this.world = world;
        this.chunks = chunks;
        this.entities = entities;
        this.mobs = mobs;
        this.hoppers = hoppers;
        this.dispensers = dispensers;
        this.primedTnt = primedTnt;
        this.players = players;
    }

    public static WorldSnapshot of(World world) {
        int chunks = 0;
        int hoppers = 0;
        int dispensers = 0;

        for (Chunk chunk : world.getLoadedChunks()) {
            chunks++;
            for (BlockState state : chunk.getTileEntities()) {
                Material type = state.getType();
                if (type == Material.HOPPER) {
                    hoppers++;
                } else if (type == Material.DISPENSER || type == Material.DROPPER) {
                    dispensers++;
                }
            }
        }

        int entities = 0;
        int mobs = 0;
        int primed = 0;
        for (Entity entity : world.getEntities()) {
            entities++;
            if (entity instanceof Mob) {
                mobs++;
            }
            if (entity instanceof TNTPrimed) {
                primed++;
            }
        }

        return new WorldSnapshot(world.getName(), chunks, entities, mobs, hoppers,
                dispensers, primed, world.getPlayers().size());
    }

}
