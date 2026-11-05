package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Measured economy, hour buckets and break-even. */
class InsightsTest {

    private fun fuel(date: String, rm: Double, odo: Int) = Entry(
        id = "f-$date", date = date, type = "expense", cat = "fuel",
        amount = (rm * 100).toLong(), odo = odo, updated = date + "T00:00:00Z",
    )

    @Test
    fun economyIsMeasuredBetweenFillUps() {
        val store = Store(
            entries = listOf(
                fuel("2026-11-01", 40.0, 45000),
                fuel("2026-11-04", 40.0, 45400),
                fuel("2026-11-08", 45.0, 45900),
            ),
            settings = Settings(profit = Profit(fuelPrice = 2.00)),
        )
        val m = measuredEconomy(store)!!
        // 900 km between the first and last reading
        assertEquals(900.0, m.km, 0.01)
        // litres bought after the first fill: 40/2 + 45/2 = 42.5
        assertEquals(42.5, m.litres, 0.01)
        assertEquals(900.0 / 42.5, m.kml, 0.01)
        assertEquals(3, m.fills)
    }

    @Test
    fun oneFillUpIsNotEnough() {
        val store = Store(entries = listOf(fuel("2026-11-01", 40.0, 45000)))
        assertNull(measuredEconomy(store))
    }

    @Test
    fun heatmapSplitsAShiftAcrossBlocks() {
        // Tuesday 18:00 for 4 hours -> 18:00-20:00 and 20:00-22:00 blocks
        val store = Store(
            shifts = listOf(Shift(id = "a", date = "2026-11-03", start = "18:00", hours = 4.0, gross = 40000, grabFee = 0, km = 40.0)),
            settings = Settings(profit = Profit(avgSpeed = 0.0, grabInsurance = 0.0)),
        )
        val blocks = hourHeatmap(store)
        assertTrue(blocks.isNotEmpty())
        // 2026-11-03 is a Tuesday -> day index 1
        assertEquals(1, blocks.first().day)
        assertEquals(2, blocks.size, "two 4-hour blocks were touched")
        assertEquals(4.0, blocks.sumOf { it.hours }, 0.01, "all hours accounted for")
    }

    @Test
    fun breakEvenCountsRunningCoverAndOverhead() {
        val store = Store(settings = Settings(profit = Profit(avgSpeed = 0.0, grabInsurance = 0.0)))
        val start = 1_000_000L
        val live = LiveShift(start = start, km = 50.0, payouts = listOf(Payout(2000, start)))
        // 50 km at 1.99/11 + 0.09 per km
        val be = liveBreakEven(store, live, start + 3_600_000L)
        assertNotNull(be)
        val expectedRunning = 50.0 * (1.99 / 11.0 + 0.09)
        assertEquals(expectedRunning, be.costs, 0.01)
        assertEquals(20.0, be.earned, 0.01)
        assertTrue(be.minutesToGo == null || be.minutesToGo!! > 0)
    }
}
