package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

class FundStatsTest {
    private val profit = Profit(fuelPrice = 2.0, kmPerLitre = 10.0, wearPerKm = 0.1)
    private val store = Store(
        settings = Settings(profit = profit),
        shifts = listOf(
            Shift(id = "oct", date = "2026-10-20", km = 100.0),
            Shift(id = "nov", date = "2026-11-02", km = 100.0),
        ),
        entries = listOf(
            Entry(id = "fuel-oct", date = "2026-10-20", cat = "fuel", amount = 500),
            Entry(id = "maint-oct", date = "2026-10-21", cat = "maintenance", amount = 2_000),
            Entry(id = "fuel-nov", date = "2026-11-03", cat = "petrol", amount = 5_000),
            Entry(id = "wash-nov", date = "2026-11-04", cat = "carwash", amount = 10_000),
        ),
    )

    @Test
    fun lifetimeFundsCarryPriorMonths() {
        val funds = fundStats(store)
        assertEquals(40.0, funds.fuel.accrued, 0.001)
        assertEquals(55.0, funds.fuel.spent, 0.001)
        assertEquals(-15.0, funds.fuel.balance, 0.001)
        assertEquals(20.0, funds.wear.accrued, 0.001)
        assertEquals(120.0, funds.wear.spent, 0.001)
        assertEquals(-100.0, funds.wear.balance, 0.001)
    }

    @Test
    fun selectedMonthDoesNotSeeLaterMonths() {
        val future = store.copy(
            shifts = store.shifts + Shift(id = "dec", date = "2026-12-02", km = 100.0),
            entries = store.entries + Entry(id = "fuel-dec", date = "2026-12-03", cat = "fuel", amount = 1_000),
        )
        val november = fundStats(future, "2026-11")
        assertEquals(-15.0, november.fuel.balance, 0.001)
        assertEquals(-100.0, november.wear.balance, 0.001)
        val all = fundStats(future)
        assertEquals(-5.0, all.fuel.balance, 0.001) // +20 accrued, +10 spent in Dec
    }

    @Test
    fun selectedMonthOnlyChangesTheMonthlyContributionLine() {
        val funds = fundStats(store, "2026-11")
        // Lifetime reserve still carries October.
        assertEquals(-15.0, funds.fuel.balance, 0.001)
        assertEquals(-100.0, funds.wear.balance, 0.001)
        // November contribution and withdrawal are separate.
        assertEquals(20.0, funds.fuel.monthAccrued, 0.001)
        assertEquals(50.0, funds.fuel.monthSpent, 0.001)
        assertEquals(-30.0, funds.fuel.monthNet, 0.001)
        assertEquals(10.0, funds.wear.monthAccrued, 0.001)
        assertEquals(100.0, funds.wear.monthSpent, 0.001)
        assertEquals(-90.0, funds.wear.monthNet, 0.001)
    }
}
