package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveYieldTest {
    @Test
    fun liveYieldUsesPayoutsMinusRunCostsAndTrackedKm() {
        val now = 1_800_000_000_000L
        val store = Store(
            settings = Settings(
                profit = Profit(
                    fuelPrice = 0.0, kmPerLitre = 1.0, wearPerKm = 0.0,
                    avgSpeed = 30.0, grabInsurance = 0.0, targetRate = 18.0,
                ),
            ),
        )
        val live = LiveShift(start = now - 4 * 3_600_000L, payouts = listOf(Payout(10_000, now)), km = 100.0)
        val be = liveBreakEven(store, live, now)
        assertEquals(100.0, be.earned)
        assertEquals(0.0, be.costs)
        assertEquals(25.0, be.netPerHour!!, 0.0001)
        assertNotNull(be.grossPerKm)
        assertEquals(1.0, be.grossPerKm!!, 0.0001)

        val line = liveNotificationLine(store, live, now)
        assertTrue(line.contains("4h 00m worked"))
        assertTrue(line.contains("100.0 km"))
        assertTrue(line.contains("RM 25.00/h net"))
    }
}
