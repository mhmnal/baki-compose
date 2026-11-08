package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Incentives are earnings, tolls are costs unless reimbursed, and operating totals ignore cash fee rows. */
class OperatingTotalsTest {

    private val free = Profit(
        fuelPrice = 0.0, kmPerLitre = 11.0, wearPerKm = 0.0, targetRate = 0.0,
        avgSpeed = 30.0, grabInsurance = 0.0, loanMonthly = 0.0,
        roadTaxYearly = 0.0, carInsuranceYearly = 0.0,
    )

    private fun shift(
        reimbursed: Boolean = false,
        grabFee: Long = 0,
    ) = Shift(
        id = "s1", date = "2026-11-08", start = "08:00", hours = 2.0, km = 10.0,
        gross = 10_000, incentive = 2_000, extra = 1_500, reimbursedTolls = reimbursed,
        grabFee = grabFee, updated = "2026-11-08T12:00:00Z",
    )

    private fun store(
        reimbursed: Boolean = false,
        grabFee: Long = 0,
        withFeeEntry: Boolean = false,
    ): Store {
        val sh = shift(reimbursed, grabFee)
        val entries = if (withFeeEntry) listOf(
            Entry(id = "fee-s1", date = sh.date, type = "expense", cat = "grabfee", amount = grabFee, note = "Grab insurance"),
        ) else emptyList()
        return Store(shifts = listOf(sh), entries = entries, settings = Settings(profit = free))
    }

    @Test
    fun incentivesAreIncomeInEveryCashRollup() {
        val s = store()
        val day = dayTotals(s, "2026-11-08")
        assertEquals(12_000, day.income, "gross + incentive")
        assertEquals(1_500, day.expense, "tolls")
        assertEquals(10_500, day.net)

        val month = monthTotals(s, "2026-11")
        assertEquals(day.income, month.income)
        assertEquals(day.expense, month.expense)

        val week = weekTotals(s, "2026-11-02")
        assertEquals(day.income, week.income)
        assertEquals(day.expense, week.expense)
    }

    @Test
    fun reimbursedTollsArePassThroughInCashAndOperating() {
        val paid = store()
        val back = store(reimbursed = true)

        assertEquals(1_500, dayTotals(paid, "2026-11-08").expense)
        assertEquals(0, dayTotals(back, "2026-11-08").expense)

        assertEquals(10_500, dayOperating(paid, "2026-11-08").net)
        assertEquals(12_000, dayOperating(back, "2026-11-08").net)
        assertEquals(1_500, dayOperating(paid, "2026-11-08").costs)
        assertEquals(0, dayOperating(back, "2026-11-08").costs)
    }

    @Test
    fun operatingTotalsDoNotDoubleCountTheLinkedInsuranceFee() {
        // grabFee is the operating charge; fee-s1 is the cash row created by Cover.kt.
        val s = store(grabFee = 750, withFeeEntry = true)
        val op = dayOperating(s, "2026-11-08")
        assertEquals(12_000, op.earnings)
        assertEquals(2_250, op.costs, "tolls 15 + insurance 7.50")
        assertEquals(9_750, op.net)

        // The cash roll-up also nets to 9.75 here: 120 income - 15 tolls - 7.50 fee.
        assertEquals(9_750, dayTotals(s, "2026-11-08").net)

        // If the cash row is missing, operating still charges grabFee; the cash roll-up cannot.
        val missing = store(grabFee = 750, withFeeEntry = false)
        assertEquals(9_750, dayOperating(missing, "2026-11-08").net)
        assertEquals(10_500, dayTotals(missing, "2026-11-08").net)
    }

    @Test
    fun operatingTotalsCarryHoursAndPerHour() {
        val op = dayOperating(store(), "2026-11-08")
        assertEquals(2.0, op.hours)
        assertEquals(1, op.shiftCount)
        assertEquals(52.50, op.perHour!!, 0.001)
    }

    @Test
    fun reimbursedTollsRoundTripThroughJson() {
        val json = BakiJson.encodeToString(Shift.serializer(), shift(reimbursed = true))
        assertTrue(json.contains("\"reimbursedTolls\":true"))
        val decoded = BakiJson.decodeFromString(Shift.serializer(), json)
        assertTrue(decoded.reimbursedTolls)
    }
}
