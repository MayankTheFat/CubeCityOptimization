package city.cube.optimization.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotJsonTest {

    @Test
    void emptyMapBecomesEmptyObject() {
        assertEquals("{}", SnapshotJson.counters(Map.of()));
    }

    @Test
    void countersAreSerializedAsNumbers() {
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("chunks-unloaded", 12L);
        counters.put("tnt-merged", 3L);
        assertEquals("{\"chunks-unloaded\":12,\"tnt-merged\":3}", SnapshotJson.counters(counters));
    }

    @Test
    void keysAreEscaped() {
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("quote\"key", 1L);
        counters.put("back\\slash", 2L);
        assertEquals("{\"quote\\\"key\":1,\"back\\\\slash\":2}", SnapshotJson.counters(counters));
    }
}
