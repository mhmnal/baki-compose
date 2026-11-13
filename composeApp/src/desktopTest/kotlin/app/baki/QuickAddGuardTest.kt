package app.baki

import kotlin.test.Test
import kotlin.test.assertTrue

class QuickAddGuardTest {
    @Test
    fun grabfeeIsNeverAQuickAddChip() {
        val store = Store(
            entries = listOf(
                Entry(id = "f1", date = "2026-11-10", cat = "grabfee", amount = 750),
                Entry(id = "f2", date = "2026-11-11", cat = "grabfee", amount = 750),
                Entry(id = "fo1", date = "2026-11-10", cat = "food", amount = 1200),
                Entry(id = "fo2", date = "2026-11-11", cat = "food", amount = 1200),
            ),
        )
        val quick = quickAdds(store)
        assertTrue(quick.none { it.cat == "grabfee" })
        assertTrue(quick.any { it.cat == "food" })
    }
}
