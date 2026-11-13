package app.baki

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cover window must use wall-clock end, including saved breaks. */
class CoverBreakTest {

    private val tz = TimeZone.UTC
    private fun shift(id: String, date: String, start: String, hours: Double, breakMinutes: Int = 0) =
        Shift(id = id, date = date, start = start, hours = hours, breakMinutes = breakMinutes, gross = 10_000)

    @Test
    fun aBreakCanPushTheEndPastTheCoverBoundary() {
        val prior = shift("prior", "2026-11-01", "14:00", 4.0)
        // starts 23h later, inside the paid window; 30m worked + 2h break ends at 15:30,
        // past the 14:00 renewal, so this shift owns the renewal
        val next = shift("next", "2026-11-02", "13:00", 0.5, breakMinutes = 120)
        assertEquals(1, coverWindows(listOf(prior, next), tz).getValue("next"))
    }

    @Test
    fun withoutTheBreakTheSameShiftDoesNotRenew() {
        val prior = shift("prior", "2026-11-01", "14:00", 4.0)
        val next = shift("next", "2026-11-02", "13:00", 0.5)
        assertEquals(0, coverWindows(listOf(prior, next), tz).getValue("next"))
    }

    @Test
    fun recomputeCreatesOneLinkedFeeForTheBreakCrossing() {
        val store = Store(shifts = listOf(
            shift("prior", "2026-11-01", "14:00", 4.0),
            shift("next", "2026-11-02", "13:00", 0.5, breakMinutes = 120),
        ))
        val (fixed, changed) = recomputeCover(store)
        assertEquals(true, changed)
        assertEquals(750L, fixed.shifts.first { it.id == "next" }.grabFee)
        assertEquals(1, fixed.entries.count { it.id == "fee-next" })
    }

    @Test
    fun breakMinutesArePartOfTheWallClockEnd() {
        val s = shift("s", "2026-11-01", "10:00", 2.0, breakMinutes = 90)
        assertEquals(
            shiftStartMs(s, tz) + 2 * 3_600_000L + 90 * 60_000L,
            shiftEndMs(s, tz),
        )
    }
}
