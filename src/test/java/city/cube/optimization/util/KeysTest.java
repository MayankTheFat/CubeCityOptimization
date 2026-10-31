package city.cube.optimization.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeysTest {

    @Test
    void chunkKeyIsStableForSameCoordinates() {
        assertEquals(Keys.chunk(12, -34), Keys.chunk(12, -34));
    }

    @Test
    void chunkKeyDiffersForNeighbouringChunks() {
        assertTrue(Keys.chunk(0, 0) != Keys.chunk(1, 0));
        assertTrue(Keys.chunk(0, 0) != Keys.chunk(0, 1));
        assertTrue(Keys.chunk(-1, -1) != Keys.chunk(-1, 0));
    }

    @Test
    void chunkKeyHandlesLargeAndNegativeValues() {
        assertEquals(Keys.chunk(2_000_000, -2_000_000), Keys.chunk(2_000_000, -2_000_000));
        assertTrue(Keys.chunk(Integer.MAX_VALUE, Integer.MIN_VALUE)
                != Keys.chunk(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
}
