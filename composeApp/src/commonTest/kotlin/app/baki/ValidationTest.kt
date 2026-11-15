package app.baki

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun isoDatesAcceptRealDatesOnly() {
        assertTrue(isValidIsoDate("2026-11-15"))
        assertTrue(isValidIsoDate("2024-02-29"))
        assertFalse(isValidIsoDate("2026-02-29"))
        assertFalse(isValidIsoDate("2026-13-01"))
        assertFalse(isValidIsoDate("15 November 2026"))
        assertFalse(isValidIsoDate(""))
    }

    @Test
    fun clockAccepts24HourTimesOnly() {
        assertTrue(isValidClock("00:00"))
        assertTrue(isValidClock("23:59"))
        assertFalse(isValidClock("24:00"))
        assertFalse(isValidClock("9:05"))
        assertFalse(isValidClock("12:60"))
        assertFalse(isValidClock(""))
    }
}
