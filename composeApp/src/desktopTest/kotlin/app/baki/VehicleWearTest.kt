package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleWearTest {
    private fun fuel(date: String, odo: Int, id: String) = Entry(
        id = id, date = date, type = "expense", cat = "fuel", amount = 10_000, odo = odo,
    )

    private fun shift(
        id: String, date: String, odoStart: Double? = null, odoEnd: Double? = null,
        km: Double = 0.0,
    ) = Shift(
        id = id, date = date, start = "08:00", hours = 4.0, km = km,
        odoStart = odoStart, odoEnd = odoEnd, gross = 10_000,
    )

    @Test fun splitsAnchoredDistanceIntoWorkAndPersonal() {
        val store = Store(
            entries = listOf(fuel("2026-11-01", 1000, "f1"), fuel("2026-11-10", 1200, "f2")),
            shifts = listOf(
                shift("s1", "2026-11-05", odoStart = 1050.0, odoEnd = 1100.0),
                shift("s2", "2026-11-06", km = 30.0),
            ),
            settings = Settings(profit = Profit(wearPerKm = 0.10)),
        )
        val wear = odometerWear(store)
        assertTrue(wear.hasCoverage)
        assertEquals(4, wear.anchors)
        assertEquals(200.0, wear.coveredKm, 0.001)
        assertEquals(80.0, wear.workKm, 0.001)
        assertEquals(120.0, wear.personalKm, 0.001)
        assertEquals(20.0, wear.totalWear, 0.001)
        assertEquals(8.0, wear.workWear, 0.001)
        assertEquals(12.0, wear.personalWear, 0.001)
        assertEquals("2026-11-01", wear.firstDate)
        assertEquals("2026-11-10", wear.lastDate)
    }

    @Test fun shiftOnlyDaysAreActiveDays() {
        val store = Store(shifts = listOf(shift("s1", "2026-11-05", km = 50.0)))
        val days = monthDays(store, "2026-11")
        assertEquals(1, days.size)
        assertEquals(100.0, days.single().second.income / 100.0, 0.001)
    }

    @Test fun doesNotInventPersonalDistanceWithoutAnchors() {
        val store = Store(shifts = listOf(shift("s1", "2026-11-05", km = 50.0)))
        val wear = odometerWear(store)
        assertTrue(!wear.hasCoverage)
        assertEquals(0, wear.anchors)
        assertEquals(0.0, wear.coveredKm, 0.001)
    }

    @Test fun reportsRollbackInsteadOfCountingNegativeKm() {
        val store = Store(
            entries = listOf(fuel("2026-11-01", 1000, "f1"), fuel("2026-11-02", 900, "f2"), fuel("2026-11-03", 1100, "f3")),
            settings = Settings(profit = Profit(wearPerKm = 0.10)),
        )
        val wear = odometerWear(store)
        assertTrue(wear.unknownIntervals >= 1)
        assertEquals(200.0, wear.coveredKm, 0.001) // 900 -> 1100 remains usable after the rollback
    }

    @Test fun capsWorkAtTheOdometerDistance() {
        val store = Store(
            entries = listOf(fuel("2026-11-01", 1000, "f1"), fuel("2026-11-02", 1050, "f2")),
            shifts = listOf(shift("s1", "2026-11-02", km = 200.0)),
            settings = Settings(profit = Profit(wearPerKm = 0.10)),
        )
        val wear = odometerWear(store)
        assertEquals(50.0, wear.coveredKm, 0.001)
        assertEquals(50.0, wear.workKm, 0.001)
        assertEquals(0.0, wear.personalKm, 0.001)
        assertEquals(1, wear.unknownIntervals)
    }
}
