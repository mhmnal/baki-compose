package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompareTest {

    private fun store(): Store {
        val e = listOf(
            Entry(id = "a1", date = "2026-10-03", type = "expense", cat = "food", amount = 1000, updated = "x"),
            Entry(id = "a2", date = "2026-10-04", type = "income", cat = "income", amount = 5000, updated = "x"),
            Entry(id = "b1", date = "2026-11-03", type = "expense", cat = "food", amount = 2000, updated = "x"),
            Entry(id = "b2", date = "2026-11-04", type = "expense", cat = "fuel", amount = 3000, updated = "x"),
        )
        val shifts = listOf(
            Shift(id = "s1", date = "2026-10-03", start = "09:00", hours = 4.0, km = 100.0, gross = 8000, updated = "x"),
            Shift(id = "s2", date = "2026-11-03", start = "09:00", hours = 6.0, km = 150.0, gross = 12000, updated = "x"),
        )
        return Store(entries = e, shifts = shifts)
    }

    @Test
    fun periodStatsAddUp() {
        val s = periodStats(store(), "2026-10")
        assertEquals(4.0, s.hours, 0.001)
        assertEquals(100.0, s.km, 0.001)
        assertEquals(2, s.entries)
        assertEquals(1, s.shifts)
    }

    @Test
    fun changeIsRelativeToB() {
        // A = 100, B = 50 -> (A-B)/B = +100%
        val (text, positive) = relativeChange(100, 50)
        assertEquals("+100%", text)
        assertTrue(positive == true)
        // A = 50, B = 100 -> -50%
        assertEquals("-50%", relativeChange(50, 100).first)
    }

    @Test
    fun anEmptyBIsNewNotMinusAHundred() {
        assertEquals("new", relativeChange(4000, 0).first)
        assertEquals("—", relativeChange(0, 0).first)
        // rates have no meaningful "new": they read as a dash
        assertEquals("—", rawChange(26.0, 0.0))
    }

    @Test
    fun goodnessFollowsTheMetric() {
        // A = October, B = November. October earned 50.00 and spent 10.00; November earned nothing
        // and spent 50.00.
        val octoberFirst = compareMonths(store(), "2026-10", "2026-11")
        assertEquals(true, octoberFirst.first { it.label == "In" }.good)   // income is new: good
        assertEquals(true, octoberFirst.first { it.label == "Out" }.good)  // spending fell: good

        // flip the periods: now spending rose against the baseline, which is bad
        val novemberFirst = compareMonths(store(), "2026-11", "2026-10")
        assertEquals(false, novemberFirst.first { it.label == "In" }.good)  // income gone: bad
        assertEquals(false, novemberFirst.first { it.label == "Out" }.good) // spending rose: bad

        assertNull(octoberFirst.first { it.label == "Kilometres" }.good)
    }

    @Test
    fun everyRowIsPresent() {
        val rows = compareMonths(store(), "2026-10", "2026-11")
        assertEquals(9, rows.size)
        assertTrue(rows.all { it.changeLabel != null })
    }

    @Test
    fun periodKmUsesTheSameOdometerAwareDistanceAsShiftMetrics() {
        val s = Store(
            shifts = listOf(Shift(id = "s", date = "2026-11-01", start = "09:00", hours = 4.0, km = 0.0, odoStart = 1000.0, odoEnd = 1120.0, gross = 5000)),
        )
        assertEquals(120.0, periodStats(s, "2026-11").km, 0.001)
    }

    @Test
    fun categoriesAreRankedBySpend() {
        val cats = categoryTotals(store(), "2026-11")
        assertEquals("fuel", cats.first().first)
        assertTrue(cats.zipWithNext().all { (a, b) -> a.second >= b.second })
    }
}
