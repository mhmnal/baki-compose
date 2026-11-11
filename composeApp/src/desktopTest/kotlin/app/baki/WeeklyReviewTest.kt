package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyReviewTest {
    private val cfg = Profit(
        fuelPrice = 0.0, kmPerLitre = 1.0, wearPerKm = 0.0, avgSpeed = 0.0,
        grabInsurance = 0.0, targetRate = 18.0,
    )

    private fun shift(id: String, date: String, gross: Long) = Shift(
        id = id, date = date, start = "09:00", hours = 8.0, gross = gross, grabFee = 0,
    )

    @Test
    fun reviewUsesMondayToSundayAndFlagsBelowFloor() {
        val store = Store(
            settings = Settings(profit = cfg),
            // Monday 9 Nov .. Sunday 15 Nov 2026
            shifts = listOf(
                shift("low", "2026-11-09", 10_000),   // RM12.50/hr
                shift("high", "2026-11-15", 20_000),  // RM25/hr
                shift("next", "2026-11-16", 10_000),  // outside the week
            ),
        )
        val r = weeklyReview(store, "2026-11-09")!!
        assertEquals(2, r.days)
        assertEquals(16.0, r.hours)
        assertEquals(30_000, r.net)
        assertEquals(1, shiftsBelowTarget(store, "2026-11-09", 18.0).size)
    }
}
