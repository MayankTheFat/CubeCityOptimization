package city.cube.optimization.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void clampsInsideRange() {
        assertEquals(5, Numbers.clamp(5, 0, 10));
        assertEquals(0, Numbers.clamp(-3, 0, 10));
        assertEquals(10, Numbers.clamp(99, 0, 10));
    }

    @Test
    void formatsIntegersWithGrouping() {
        assertEquals("1,234,567", Numbers.ints(1_234_567L));
    }

    @Test
    void formatsOneDecimal() {
        assertEquals("19.9", Numbers.oneDecimal(19.94));
    }

    @Test
    void tpsTextUsesClosedColourTags() {
        assertEquals("<green>20.0</green>", Numbers.tpsText(20.0));
        assertEquals("<yellow>18.0</yellow>", Numbers.tpsText(18.0));
        assertEquals("<red>12.0</red>", Numbers.tpsText(12.0));
    }
}
