package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cost per km and monthly envelopes. */
class CostBudgetTest {

    private val cfg = Profit(
        fuelPrice = 2.00, kmPerLitre = 10.0, wearPerKm = 0.10,
        avgSpeed = 0.0, grabInsurance = 0.0,
    )

    @Test
    fun runningCostIsFuelPlusWearPerKm() {
        val store = Store(
            shifts = listOf(Shift(id = "a", date = "2026-11-03", start = "08:00", hours = 4.0, km = 100.0, gross = 40000, grabFee = 0)),
            settings = Settings(profit = cfg),
        )
        val c = monthCost(store, "2026-11")
        assertEquals(100.0, c.km, 0.01)
        // 2.00/10 = 0.20 fuel + 0.10 wear = 0.30/km
        assertEquals(30.0, c.running, 0.01)
        assertEquals(0.30, c.perKm, 0.001)
    }

    @Test
    fun upkeepSpendingLandsOnTop() {
        val store = Store(
            shifts = listOf(Shift(id = "a", date = "2026-11-03", start = "08:00", hours = 4.0, km = 100.0, gross = 40000, grabFee = 0)),
            entries = listOf(
                Entry(id = "s", date = "2026-11-05", type = "expense", cat = "maintenance", amount = 2000, updated = "x"),
                Entry(id = "f", date = "2026-11-05", type = "expense", cat = "food", amount = 5000, updated = "x"),
            ),
            settings = Settings(profit = cfg),
        )
        val c = monthCost(store, "2026-11")
        assertEquals(20.0, c.upkeep, 0.01, "only maintenance counts as upkeep, not food")
        assertEquals(0.50, c.perKm, 0.001)
    }

    @Test
    fun aMonthWithoutDistanceDoesNotDivideByZero() {
        val c = monthCost(Store(settings = Settings(profit = cfg)), "2026-11")
        assertEquals(0.0, c.perKm, 0.001)
    }

    @Test
    fun envelopesTrackSpendingAndOverrun() {
        val store = Store(
            entries = listOf(
                Entry(id = "1", date = "2026-11-02", type = "expense", cat = "food", amount = 25000, updated = "x"),
                Entry(id = "2", date = "2026-11-03", type = "expense", cat = "fuel", amount = 10000, updated = "x"),
            ),
            settings = Settings(budgets = mapOf("food" to 200.0, "fuel" to 400.0)),
        )
        val use = budgetUsage(store, "2026-11")
        val food = use.first { it.cat == "food" }
        val fuel = use.first { it.cat == "fuel" }
        assertEquals(250.0, food.spent, 0.01)
        assertTrue(food.over, "food is over its cap")
        assertEquals(-50.0, food.remaining, 0.01)
        assertFalse(fuel.over)
        assertEquals(0.25, fuel.fraction, 0.001)
        // worst first
        assertEquals("food", use.first().cat)
    }

    @Test
    fun noBudgetsMeansNothingToShow() {
        assertTrue(budgetUsage(Store(), "2026-11").isEmpty())
    }
}
