package city.cube.optimization.storage;

/** One periodic row of server health plus cumulative plugin counters. */
public record StatsSnapshot(
        long createdAt,
        double tps,
        double mspt,
        int worlds,
        int chunks,
        int entities,
        int hoppers,
        int dispensers,
        int tnt,
        int mobs,
        String countersJson) {
}
