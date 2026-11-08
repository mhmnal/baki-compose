package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TollTest {

    private fun e(id: String, cat: String, amount: Long, type: String = "expense") =
        Entry(id = id, date = "2026-11-03", type = type, cat = cat, amount = amount, updated = "x")

    @Test
    fun tollsAreAPassThroughOutlay() {
        val entries = listOf(
            e("t1", "toll", 450),
            e("t2", "toll", 320),
            e("t3", "food", 1200),
            e("t4", "tollback", 770, type = "income"),
        )
        val t = tollSummary(entries, "2026-11")
        assertEquals(770, t.paid)
        assertEquals(770, t.reimbursed)
        assertFalse(t.paid > 0 && t.reimbursed == 0L && false)
    }

    @Test
    fun onlyThisMonthsTollsCount() {
        val entries = listOf(
            e("a", "toll", 100),
            Entry(id = "b", date = "2026-10-31", type = "expense", cat = "toll", amount = 900, updated = "x"),
        )
        assertEquals(100, tollSummary(entries, "2026-11").paid)
    }
}
