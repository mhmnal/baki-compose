package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(3, s.entries + s.shifts) // two entries plus one shift
    }

    @Test
    fun ratesUseWorkedHoursAndDrivenKm() {
        val s = periodStats(store(), "2026-11")
        // net comes from monthTotals; per-hour must divide that by the shifts' hours
        assertEquals(s.net / 100.0 / 6.0, s.perHour, 0.01)
        assertEquals(s.net / 100.0 / 150.0, s.perKm, 0.01)
    }

    @Test
    fun comparisonHasEveryRowAndSignedDeltas() {
        val rows = compareMonths(store(), "2026-10", "2026-11")
        assertEquals(8, rows.size)
        val net = rows.first { it.label == "Net" }
        val expected = periodStats(store(), "2026-11").net - periodStats(store(), "2026-10").net
        assertEquals(expected, net.delta)
        assertTrue(rows.first { it.label == "RM / hour" }.delta == null, "rate rows show a raw change, not a delta")
    }

    @Test
    fun categoriesAreRankedBySpend() {
        val cats = categoryTotals(store(), "2026-11")
        assertEquals("fuel", cats.first().first)
        assertTrue(cats.zipWithNext().all { (a, b) -> a.second >= b.second })
    }
}
