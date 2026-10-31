package city.cube.optimization.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownGateTest {

    private static final long SECOND = 1000L;

    @Test
    void firstCallIsAllowedAndSecondInsideWindowIsNot() {
        CooldownGate gate = new CooldownGate();
        assertTrue(gate.allow("hopper:a", 10_000L, SECOND));
        assertFalse(gate.allow("hopper:a", 10_500L, SECOND));
        assertEquals(1, gate.size());
    }

    @Test
    void allowedAgainAfterInterval() {
        CooldownGate gate = new CooldownGate();
        assertTrue(gate.allow("hopper:a", 10_000L, SECOND));
        assertTrue(gate.allow("hopper:a", 11_000L, SECOND));
        assertEquals(11_000L, gate.lastAllowed("hopper:a"));
    }

    @Test
    void keysAreIndependent() {
        CooldownGate gate = new CooldownGate();
        assertTrue(gate.allow("a", 0L, SECOND));
        assertTrue(gate.allow("b", 0L, SECOND));
        assertFalse(gate.allow("a", 1L, SECOND));
        assertFalse(gate.allow("b", 1L, SECOND));
    }

    @Test
    void zeroIntervalNeverBlocks() {
        CooldownGate gate = new CooldownGate();
        assertTrue(gate.allow("x", 5L, 0L));
        assertTrue(gate.allow("x", 5L, 0L));
        assertTrue(gate.allow("x", 5L, -10L));
        assertNull(gate.lastAllowed("x"));
    }

    @Test
    void pruneDropsEntriesOlderThanCutoff() {
        CooldownGate gate = new CooldownGate();
        gate.allow("old", 1_000L, 1L);
        gate.allow("new", 20_000L, 1L);
        assertEquals(2, gate.size());

        gate.prune(5_000L);
        assertNull(gate.lastAllowed("old"));
        assertEquals(1, gate.size());

        gate.prune(25_000L);
        assertEquals(0, gate.size());
    }
}
