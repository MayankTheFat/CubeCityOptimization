package city.cube.optimization.util;

import java.util.List;

/** Aggregate counters across all loaded worlds, refreshed on a timer. */
public record Totals(int worlds, int chunks, int entities, int mobs, int hoppers,
                     int dispensers, int tnt, int players) {

    public static final Totals EMPTY = new Totals(0, 0, 0, 0, 0, 0, 0, 0);

    public static Totals of(List<WorldSnapshot> snapshots) {
        int worlds = snapshots.size();
        int chunks = 0;
        int entities = 0;
        int mobs = 0;
        int hoppers = 0;
        int dispensers = 0;
        int tnt = 0;
        int players = 0;

        for (WorldSnapshot snapshot : snapshots) {
            chunks += snapshot.chunks;
            entities += snapshot.entities;
            mobs += snapshot.mobs;
            hoppers += snapshot.hoppers;
            dispensers += snapshot.dispensers;
            tnt += snapshot.primedTnt;
            players += snapshot.players;
        }
        return new Totals(worlds, chunks, entities, mobs, hoppers, dispensers, tnt, players);
    }
}
