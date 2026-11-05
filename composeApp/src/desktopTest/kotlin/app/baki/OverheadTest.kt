package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixed costs: the loan and annual bills must actually reduce the RM/hr figure. */
class OverheadTest {

    private val cfg = Profit(
        fuelPrice = 1.99, kmPerLitre = 11.0, wearPerKm = 0.09,
        targetRate = 18.0, avgSpeed = 30.0, grabInsurance = 7.5,
        loanMonthly = 1200.0, roadTaxYearly = 300.0, carInsuranceYearly = 1500.0,
    )

    @Test
    fun annualBillsAreSpreadMonthly() {
        // 1200 loan + 25 road tax + 125 insurance
        assertEquals(1350.0, monthlyOverhead(cfg), 0.001)
    }

    @Test
    fun overheadIsSplitOverHoursDriven() {
        val store = Store(
            shifts = listOf(
                Shift(id = "a", date = "2026-11-02", start = "08:00", hours = 8.0, gross = 40000),
                Shift(id = "b", date = "2026-11-03", start = "08:00", hours = 12.0, gross = 60000),
            ),
            settings = Settings(profit = cfg),
        )
        assertEquals(20.0, monthDrivenHours(store, "2026-11"), 0.001)
        assertEquals(67.5, overheadPerHour(store, "2026-11"), 0.001)
    }

    @Test
    fun ashiftPaysItsShareOfTheFixedCosts() {
        val store = Store(
            shifts = listOf(
                Shift(id = "a", date = "2026-11-02", start = "08:00", hours = 8.0, gross = 40000),
                Shift(id = "b", date = "2026-11-03", start = "08:00", hours = 12.0, gross = 60000),
            ),
            settings = Settings(profit = cfg),
        )
        // avgSpeed 0 so the shift's own running cost is zero and only overhead is in play
        val flat = cfg.copy(avgSpeed = 0.0)
        val shift = Shift(id = "c", date = "2026-11-04", start = "08:00", hours = 4.0, km = 0.0, gross = 40000, grabFee = 0)
        val withOverhead = shiftMetrics(shift, flat, overheadPerHour(store, "2026-11"))
        // 400 gross - 0 running - 0 extra - 0 cover - 67.5 * 4h
        assertEquals(400.0 - 270.0, withOverhead.net, 0.01)
        // and the old signature still behaves as before, which is why the golden tests still pass
        assertEquals(400.0, shiftMetrics(shift, flat).net, 0.01)
    }

    @Test
    fun aMonthWithNoShiftsDoesNotDivideByZero() {
        val store = Store(settings = Settings(profit = cfg))
        assertEquals(0.0, overheadPerHour(store, "2026-12"), 0.001)
    }

    @Test
    fun payoutDueCountsOnlyWhatIsNotSettled() {
        val store = Store(
            shifts = listOf(
                Shift(id = "settled", date = "2026-10-28", start = "08:00", hours = 4.0, gross = 10000),
                Shift(id = "pending1", date = "2026-11-03", start = "08:00", hours = 4.0, gross = 25000),
                Shift(id = "pending2", date = "2026-11-05", start = "08:00", hours = 4.0, gross = 15070),
            ),
            statements = mapOf("2026-10-26" to Statement(weekStart = "2026-10-26", income = 500.0)),
        )
        assertEquals("2026-11-01", statementCoveredUntil(store))
        assertEquals(400.70, payoutDue(store)!!, 0.001)
        assertNull(payoutDue(Store()))
    }
}
