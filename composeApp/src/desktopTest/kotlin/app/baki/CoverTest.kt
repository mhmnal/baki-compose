package app.baki

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cover rule must be *idempotent* on the real store: the web app already computed these
 * fees and fee-* entries, so re-deriving them in Kotlin must want to change nothing.
 */
class CoverTest {

    private fun resource(name: String) =
        CoverTest::class.java.classLoader.getResourceAsStream(name)?.bufferedReader()?.readText()
            ?: error("missing $name")

    private val store: Store by lazy { BakiJson.decodeFromString<Store>(resource("store.json")) }
    private val tz = TimeZone.currentSystemDefault()

    @Test
    fun reDerivingTheRealStoreIsIdempotent() {
        // the rule changed (a shift now renews when it runs past the window), so the first pass may
        // legitimately adjust; a second pass must not want to change anything
        val (once, _) = recomputeCover(store)
        val (twice, changed) = recomputeCover(once)
        assertFalse(changed, "recompute is not idempotent")
        assertEquals(once.shifts.map { it.grabFee }, twice.shifts.map { it.grabFee })
        assertEquals(once.entries.size, twice.entries.size)
    }

    @Test
    fun aShiftThatRunsPastTheWindowRenewsWhileWorking() {
        val day = Shift(id = "day", date = "2026-11-01", start = "14:00", hours = 4.0, gross = 10000)
        // starts 23h later (inside the window) and runs past its end at 14:00
        val late = Shift(id = "late", date = "2026-11-02", start = "13:00", hours = 6.0, gross = 10000)
        val windows = coverWindows(listOf(day, late), tz)
        assertEquals(1, windows.getValue("day"))
        assertEquals(1, windows.getValue("late"))   // no charge at start, one renewal mid-shift
    }

    @Test
    fun aVeryLongShiftCanRenewTwice() {
        val first = Shift(id = "a", date = "2026-11-01", start = "00:00", hours = 1.0, gross = 5000)
        // covers 01 20:00 → 03 02:00, crossing 02 00:00 and 03 00:00
        val long = Shift(id = "b", date = "2026-11-01", start = "20:00", hours = 30.0, gross = 50000)
        assertEquals(2, coverWindows(listOf(first, long), tz).getValue("b"))
    }

    @Test
    fun liveRenewalIsOnlyCountedAfterTheWindowEnds() {
        val s = store.copy(
            entries = emptyList(),
            shifts = listOf(Shift(id = "s1", date = "2026-11-01", start = "14:00", hours = 4.0, gross = 10000)),
        )
        val start = shiftStartMs(s.shifts.first(), tz) + 23 * 3_600_000L   // inside the paid window
        val before = liveRenewedWindows(s, start, start + 30 * 60_000L)
        val after = liveRenewedWindows(s, start, start + 2 * 3_600_000L)   // past 14:00 next day
        assertEquals(0, before)
        assertEquals(1, after)
    }

    @Test
    fun theRuleIsA24HourWindowThatRenewsWhileWorking() {
        val a = Shift(id = "a", date = "2026-11-01", start = "14:00", hours = 4.0, gross = 14026)
        // starts 23.5h later, inside the paid window, but is still driving at 14:00 → renews
        val b = Shift(id = "b", date = "2026-11-02", start = "13:30", hours = 6.0, gross = 16495)
        // next window runs from 2 Nov 14:00; 3 Nov 17:00 is outside it → charged at start
        val c = Shift(id = "c", date = "2026-11-03", start = "17:00", hours = 3.0, gross = 7568)
        assertEquals(1, coverWindows(listOf(a, b, c), tz).getValue("b"))
        assertEquals(setOf("a", "b", "c"), coverChargedIds(listOf(a, b, c), tz))
    }

    @Test
    fun exactlyTwentyFourHoursIsCharged() {
        val a = Shift(id = "a", date = "2026-11-01", start = "15:00", hours = 4.0)
        val b = Shift(id = "b", date = "2026-11-02", start = "15:00", hours = 4.0)
        assertEquals(setOf("a", "b"), coverChargedIds(listOf(a, b), tz))
    }

    @Test
    fun aNewShiftOutsideTheWindowWouldBeCharged() {
        val store = store
        // 4 Nov 03:30 is inside the 3 Nov 17:00 window; 5 Nov 06:00 is not
        assertEquals(0L, draftCoverCharge(store, "2026-11-04", "03:30"))
        assertTrue(draftCoverCharge(store, "2026-11-06", "06:00") > 0L)
    }

    @Test
    fun coverProducesTheLinkedExpenseEntry() {
        val bare = store.copy(
            entries = emptyList(),
            shifts = listOf(Shift(id = "s1", date = "2026-11-01", start = "14:00", hours = 4.0, gross = 10000)),
        )
        val (updated, changed) = recomputeCover(bare)
        assertTrue(changed)
        val fee = updated.entries.first { it.id == "fee-s1" }
        assertEquals("grabfee", fee.cat)
        assertEquals(750L, fee.amount)
        assertEquals("2026-11-01", fee.date)
    }
}
