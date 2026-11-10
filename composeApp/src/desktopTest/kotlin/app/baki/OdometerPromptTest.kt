package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OdometerPromptTest {
    @Test
    fun rejectsEmptyZeroAndLowerReadings() {
        assertNull(validOdometer("", null))
        assertNull(validOdometer("0", null))
        assertNull(validOdometer("-1", null))
        assertNull(validOdometer("100", 200))
        assertNull(validOdometer("200", 200))
    }

    @Test
    fun acceptsANewerReading() {
        assertEquals(201, validOdometer("201", 200))
        assertEquals(1, validOdometer("1", null))
    }
}
