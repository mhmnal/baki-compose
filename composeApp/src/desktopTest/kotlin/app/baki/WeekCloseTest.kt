package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekCloseTest {
    @Test
    fun weekCloseUsesMondayToSundayAndRecordedPayouts() {
        val shift = Shift(
            id = "shift-week-close",
            date = "2026-11-16",
            start = "09:00",
            hours = 2.0,
            km = 20.0,
            gross = 10_000,
            payouts = listOf(Payout(5_000, 1)),
        )
        val store = Store(
            shifts = listOf(shift),
            statements = mapOf(
                "2026-11-16" to Statement(
                    weekStart = "2026-11-16",
                    rangeText = "16–22 Nov",
                    income = 100.0,
                    deductions = -10.0,
                    weekly = 90.0,
                    insuranceCount = 2,
                ),
            ),
        )
        val view = weekCloseView(store, "2026-11-16")
        assertEquals("2026-11-22", view.weekEnd)
        assertEquals(1, view.shiftCount)
        assertEquals(10_000, view.recordedGross)
        assertEquals(5_000, view.recordedPayouts)
        assertEquals(5_000, view.received)
        assertEquals(0, view.payoutVariance)
        assertEquals(5_000 - 9_000, view.cashVariance)
        assertTrue(view.hasActivity)
        assertTrue(!view.isClosed)

        val options = weekOptions(store, "2026-11-20")
        assertTrue(options.any { it.weekStart == "2026-11-16" && it.shiftCount == 1 && it.hasStatement })
        assertTrue(options.any { it.weekStart == weekStartOf("2026-11-20") })
    }
}
