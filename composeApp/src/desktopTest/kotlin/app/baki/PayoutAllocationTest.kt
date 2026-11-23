package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PayoutAllocationTest {
    private val week = "2026-11-02"
    private fun shift(id: String, gross: Long, paid: Long = 0) = Shift(
        id = id, date = "2026-11-03", start = "09:00", hours = 1.0, km = 10.0,
        gross = gross, payouts = if (paid > 0) listOf(Payout(paid, 1)) else emptyList(), updated = "a",
    )

    @Test
    fun allocationUsesExactCentsAndLabelsTheSource() {
        val shifts = listOf(shift("a", 100), shift("b", 100), shift("c", 100))
        val result = allocateStatementPayouts(shifts, week, 100)
        val added = result.sumOf { s -> s.payouts.sumOf { it.c } }
        assertEquals(100L, added)
        assertTrue(result.all { s -> s.payouts.all { it.source == "statement:$week" } })
        assertEquals(100L, result.sumOf { it.payouts.sumOf { p -> p.c } })
    }

    @Test
    fun existingAllocationIsNotDuplicated() {
        val first = allocateStatementPayouts(listOf(shift("a", 100)), week, 50)
        val second = allocateStatementPayouts(first, week, 50)
        assertEquals(first, second)
        assertEquals(50L, second.single().payouts.single().c)
    }

    @Test
    fun capacityStopsAtOutstandingGaps() {
        val store = Store(shifts = listOf(shift("a", 100, 40), shift("b", 50)))
        assertEquals(110L, statementAllocationCapacity(store, week))
    }
}
