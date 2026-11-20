package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunningTotalsTest {
    @Test
    fun monthAndAllTimeRunningTotalsAccumulateRecordedNet() {
        val store = Store(
            entries = listOf(
                Entry(id = "income-oct", date = "2026-10-02", type = "income", cat = "other", amount = 10_000, updated = "x"),
                Entry(id = "expense-oct", date = "2026-10-03", type = "expense", cat = "food", amount = 1_500, updated = "x"),
                Entry(id = "income-nov", date = "2026-11-04", type = "income", cat = "other", amount = 2_000, updated = "x"),
            ),
        )
        val october = monthRunningPoints(store, "2026-10")
        assertEquals(8_500, october.last().total)
        assertEquals("2026-10-31", october.last().label)

        val all = allTimeRunningPoints(store)
        assertTrue(all.size >= 2)
        assertEquals("2026-10", all.first().label)
        assertEquals(10_500, all.last().total)
    }
}
