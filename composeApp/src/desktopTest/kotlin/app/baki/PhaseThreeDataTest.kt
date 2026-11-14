package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseThreeDataTest {
    private val profit = Profit(fuelPrice = 2.0, kmPerLitre = 10.0, wearPerKm = 0.1)

    @Test
    fun carryUsesPreviousMonthAndMovesArePerMonth() {
        val store = Store(
            settings = Settings(
                profit = profit,
                budgets = mapOf("food" to 100.0),
                envelopeCarry = true,
            ),
            entries = listOf(
                Entry(id = "prev", date = "2026-10-10", cat = "food", amount = 6_000),
                Entry(id = "now", date = "2026-11-10", cat = "food", amount = 2_000),
            ),
            envelopeMoves = mapOf("2026-11" to mapOf("food" to 10.0)),
        )
        val use = budgetUsage(store, "2026-11").single()
        assertEquals(100.0, use.base, 0.001)
        assertEquals(40.0, use.carried, 0.001)
        assertEquals(10.0, use.moved, 0.001)
        assertEquals(20.0, use.spent, 0.001)
        assertEquals(130.0, use.remaining, 0.001)
    }

    @Test
    fun manualReserveMovementsChangeBalanceButNotAccrual() {
        val store = Store(
            settings = Settings(profit = profit),
            shifts = listOf(Shift(id = "s", date = "2026-11-02", km = 100.0)),
            reserveTxns = listOf(
                ReserveTxn("a", "fuel", 1_000, "2026-11-03", "top up", "2026-11-03T10:00:00Z"),
                ReserveTxn("b", "fuel", -200, "2026-11-04", "withdraw", "2026-11-04T10:00:00Z"),
            ),
        )
        val fuel = fundStats(store, "2026-11").fuel
        assertEquals(20.0, fuel.accrued, 0.001)
        assertEquals(8.0, fuel.manual, 0.001)
        assertEquals(28.0, fuel.balance, 0.001)
        assertEquals(8.0, fuel.monthManual, 0.001)
    }

    @Test
    fun oldRemoteSettingsDoNotEraseLocalEnvelopeSettings() {
        val local = Store(settings = Settings(budgets = mapOf("food" to 100.0), envelopeCarry = true, reserveTargets = mapOf("fuel" to 250.0)))
        val oldRemote = Store(settings = Settings())
        val merged = mergeStores(local, oldRemote)
        assertEquals(100.0, merged.settings.budgets.getValue("food"), 0.001)
        assertTrue(merged.settings.envelopeCarry)
        assertEquals(250.0, merged.settings.reserveTargets.getValue("fuel"), 0.001)
    }

    @Test
    fun envelopeMovesAndReserveTxnsMergeSafely() {
        val a = Store(
            envelopeMoves = mapOf("2026-11" to mapOf("food" to 10.0)),
            reserveTxns = listOf(ReserveTxn("x", "fuel", 500, "2026-11-01", "", "2026-11-01T10:00:00Z")),
        )
        val b = Store(
            envelopeMoves = mapOf("2026-11" to mapOf("fuel" to -5.0)),
            reserveTxns = listOf(ReserveTxn("x", "fuel", 900, "2026-11-01", "", "2026-11-01T11:00:00Z")),
        )
        val merged = mergeStores(a, b)
        assertEquals(10.0, merged.envelopeMoves.getValue("2026-11").getValue("food"), 0.001)
        assertEquals(-5.0, merged.envelopeMoves.getValue("2026-11").getValue("fuel"), 0.001)
        assertEquals(900, merged.reserveTxns.single().amount)
        assertTrue(merged.settings.envelopeCarry == false)
    }
}
