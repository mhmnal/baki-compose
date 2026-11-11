package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

class CashTruthTest {

    // Zero running rates keep these tests about cash classification, not fuel estimates.
    private val noRunningCost = Profit(
        fuelPrice = 0.0, kmPerLitre = 1.0, wearPerKm = 0.0, avgSpeed = 0.0, grabInsurance = 0.0,
    )

    // Statement covers through 15 Nov, so the 10 Nov shift is settled.
    private val settled = mapOf("2026-11-09" to Statement(weekStart = "2026-11-09"))

    private fun shift(
        id: String = "s1",
        gross: Long = 10_000,
        extra: Long = 0,
        reimbursed: Boolean = false,
        payouts: List<Payout> = emptyList(),
    ) = Shift(
        id = id, date = "2026-11-10", start = "09:00", hours = 8.0,
        gross = gross, extra = extra, reimbursedTolls = reimbursed,
        grabFee = 0, payouts = payouts,
    )

    @Test
    fun recordedCashAndOperatingStartFromTheSameShift() {
        val store = Store(
            settings = Settings(profit = noRunningCost),
            statements = settled,
            shifts = listOf(shift()),
        )
        val t = cashTruth(store)
        assertEquals(10_000, t.recordedNet)
        assertEquals(10_000, t.cashNet)
        assertEquals(10_000, t.operatingNet)
        assertEquals(0, t.unpaid)
    }

    @Test
    fun partialPayoutsAreCashButNotRecordedTwice() {
        val store = Store(
            settings = Settings(profit = noRunningCost),
            shifts = listOf(shift(payouts = listOf(Payout(6_000, 0L)))),
        )
        val t = cashTruth(store)
        assertEquals(10_000, t.recordedNet)
        assertEquals(6_000, t.cashNet)
        assertEquals(4_000, t.unpaid)
    }

    @Test
    fun coverIsCashOnceAndOperatingOnce() {
        val store = Store(
            settings = Settings(profit = noRunningCost),
            statements = settled,
            entries = listOf(Entry(id = "fee", date = "2026-11-10", cat = "grabfee", amount = 750)),
            shifts = listOf(shift().copy(grabFee = 750)),
        )
        val t = cashTruth(store)
        assertEquals(9_250, t.recordedNet)
        assertEquals(9_250, t.cashNet)
        // operating = 100 - 7.50 cover (fuel/wear are zero for 0 km)
        assertEquals(9_250, t.operatingNet)
        assertEquals(monthTotals(store, "2026-11").net, t.recordedNet)
    }

    @Test
    fun reimbursedTollDoesNotReduceEitherTruth() {
        val store = Store(
            settings = Settings(profit = noRunningCost),
            statements = settled,
            shifts = listOf(shift(extra = 500, reimbursed = true)),
        )
        val t = cashTruth(store)
        assertEquals(10_000, t.recordedNet)
        assertEquals(10_000, t.cashNet)
    }

    @Test
    fun afterTheStatementAnUnpaidShiftIsNotCash() {
        val store = Store(
            settings = Settings(profit = noRunningCost),
            statements = mapOf("2026-11-02" to Statement(weekStart = "2026-11-02")),
            shifts = listOf(shift()),
        )
        val t = cashTruth(store)
        assertEquals(10_000, t.recordedNet)
        assertEquals(0, t.cashNet)
        assertEquals(10_000, t.unpaid)
        assertEquals("2026-11-08", t.payoutAnchor)
    }

    @Test
    fun billPrepaymentIsCashNowAndPrepaidAhead() {
        val store = Store(
            entries = listOf(Entry(id = "e1", date = "2026-11-10", cat = "rent", amount = 27_000)),
            billPayments = listOf(
                BillPayment(billId = "b1", period = "2026-12", amount = 27_000, entryId = "e1", paidAt = "2026-11-10"),
            ),
        )
        val t = cashTruth(store, "2026-11")
        assertEquals(-27_000, t.recordedNet)
        assertEquals(-27_000, t.cashNet)
        assertEquals(27_000, t.prepaidBills)
    }
}
