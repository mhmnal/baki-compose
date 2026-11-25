package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatementIncomeTest {
    private val week = "2026-11-16"
    private val st = Statement(
        weekStart = week, incentives = 16.80, tips = 4.00,
        deliveryIncentive = 1.50, income = 417.70, deductions = -30.0, weekly = 387.70,
    )

    @Test fun postsIncentivesTipsAndDeliveryOnce() {
        val first = postStatementIncome(Store(settings = Store().settings.copy(cats = Cats(catsIn = listOf(Cat("bonus", "Bonus", ""), Cat("tips", "Tips", ""))))), st, "2026-11-25T00:00:00Z")
        assertEquals(3, first.entries.size)
        assertEquals(1_680L, first.entries.first { it.id.endsWith("incentive") }.amount)
        assertEquals(400L, first.entries.first { it.id.endsWith("tips") }.amount)
        assertEquals(150L, first.entries.first { it.id.endsWith("delivery") }.amount)
        val second = postStatementIncome(first, st, "2026-11-26T00:00:00Z")
        assertEquals(3, second.entries.size)
        assertEquals(3, second.entries.map { it.id }.toSet().size)
        assertEquals(first.entries, second.entries)
    }

    @Test fun explicitShiftIncentiveIsNotDoubled() {
        val shift = Shift(id = "s", date = week, start = "08:00", hours = 4.0, gross = 10_000, incentive = 500, updated = "a")
        val result = postStatementIncome(Store(shifts = listOf(shift)), st)
        assertEquals(1_180L, result.entries.first { it.id.endsWith("incentive") }.amount)
    }

    @Test fun zeroStatementIncomeRemovesOldAutomaticEntries() {
        val store = Store(entries = listOf(Entry("statement-auto-$week-tips", week, "income", amount = 400)))
        val result = postStatementIncome(store, st.copy(incentives = 0.0, tips = 0.0, deliveryIncentive = 0.0))
        assertTrue(result.entries.isEmpty())
    }
}
