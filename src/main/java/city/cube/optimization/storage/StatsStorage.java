package city.cube.optimization.storage;

import java.util.List;

/** Storage backend contract. Implementations must be safe to call from async threads. */
public interface StatsStorage {

    void init();

    void record(StatsSnapshot snapshot);

    void action(String action, String detail);

    List<String> recentActions(int limit);

    String describe();

    boolean ready();

    void close();
}
