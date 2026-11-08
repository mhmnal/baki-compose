package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaxTest {

    private fun e(id: String, cat: String, amount: Long, type: String = "expense", date: String = "2026-11-05") =
        Entry(id = id, date = date, type = type, cat = cat, amount = amount, updated = "x")

    private fun store(): Store = Store(
        entries = listOf(
            e("tip", "tips", 2000, type = "income"),
            e("fuel", "fuel", 10000),
            e("phone", "phone", 3000),
            e("food", "food", 5000),
            e("toll", "toll", 1000),
            e("old", "fuel", 99999, date = "2025-12-31"),
        ),
        shifts = listOf(
            Shift(id = "s1", date = "2026-11-05", start = "10:00", hours = 4.0, km = 100.0, gross = 50000),
        ),
    )

    @Test
    fun incomeAndDeductionsFollowTheBusinessUseShare() {
        val t = taxYear(store(), 2026, totalKmInput = 200.0)
        assertEquals(52000, t.income)                    // shift 500 + tip 20
        assertEquals(100.0, t.businessKm, 0.001)
        assertEquals(0.5, t.businessUse, 0.001)
        // vehicle (fuel 100 + toll 10) halved = 55, phone 30 at 100% = 85
        assertEquals(8500, t.deductible)
        assertEquals(43500, t.netBusinessIncome)
    }

    @Test
    fun blankTotalKmMeansAllBusiness() {
        val t = taxYear(store(), 2026, totalKmInput = null)
        assertEquals(1.0, t.businessUse, 0.001)
        assertEquals(11000 + 3000, t.deductible)         // all vehicle + phone
    }

    @Test
    fun foodIsListedButNeverDeductible() {
        val t = taxYear(store(), 2026, totalKmInput = 200.0)
        val food = t.lines.first { it.id == "food" }
        assertEquals(5000, food.amount)
        assertTrue(!food.deductible)
    }

    @Test
    fun previousYearsAreExcluded() {
        val t = taxYear(store(), 2026, totalKmInput = null)
        assertTrue(t.lines.none { it.amount == 99999L })
    }

    @Test
    fun thePackCarriesItsFigures() {
        val t = taxYear(store(), 2026, totalKmInput = 200.0)
        val html = taxPackHtml(t)
        assertTrue(html.contains("Borang B"))
        assertTrue(html.contains("435.00"), "net business income missing from the pack")
        val csv = taxPackCsv(t)
        assertTrue(csv.lines().any { it.startsWith("total,Net business income,435.0") })
    }
}
