package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataHealthTest {
    @Test
    fun reportFindsIncompleteRecordsWithoutJudgingThem() {
        val day = "2026-11-23"
        val shift = Shift(
            id = "s1", date = day, start = "09:00", hours = 2.0, km = 20.0,
            gross = 10_000, updated = "2026-11-23T09:00:00",
        )
        val bill = Bill(id = "b1", name = "Phone", amount = 5_000, category = "phone")
        val inactive = BillPayment("b1", "2026-11", 5_000, "e1", day, day, active = false)
        val store = Store(
            entries = listOf(
                Entry(id = "f1", date = day, type = "expense", cat = "fuel", amount = 4_000),
                Entry(id = "f2", date = day, type = "expense", cat = "fuel", amount = 4_500),
            ),
            shifts = listOf(shift),
            bills = listOf(bill, bill.copy(id = "goal", name = "Optional goal", optional = true)),
            billPayments = listOf(inactive),
        )
        val report = dataHealth(
            store = store,
            lastBackupAt = null,
            lastBackupEntries = 0,
            now = 1_800_000_000_000L,
            today = day,
        )
        assertEquals(1, report.payoutGaps.size)
        assertEquals(10_000L, report.payoutGapTotal)
        assertEquals(1, report.missingDueBills.size)
        assertEquals("Phone", report.missingDueBills.first().name)
        assertEquals(1, report.inactiveBills.size)
        assertEquals(0, report.odometerFills)
        assertEquals(2, report.fuelFills)
        assertTrue(report.needsOdometer)
        assertTrue(report.backupStale)
        assertEquals(1, report.openWeek?.shiftCount)
        assertTrue(report.hasIssues)
    }

    @Test
    fun activeLinkSupersedesInactiveHistory() {
        val bill = Bill(id = "b1", name = "Phone", amount = 5_000, category = "phone", dueDay = 7)
        val store = Store(
            bills = listOf(bill),
            billPayments = listOf(
                BillPayment("b1", "2026-11", 5_000, "e1", "2026-11-20", "2026-11-20", active = true),
                BillPayment("b1", "2026-12", 5_000, "e2", "2026-11-21", "2026-11-21", active = false),
            ),
        )
        val report = dataHealth(store, lastBackupAt = 1_800_000_000_000L, lastBackupEntries = 0, now = 1_800_000_000_000L, today = "2026-11-23")
        assertEquals(0, report.inactiveBills.size)
    }

    @Test
    fun cleanReportHasNoIssues() {
        val report = dataHealth(Store(), lastBackupAt = 1_800_000_000_000L, lastBackupEntries = 0, now = 1_800_000_000_000L, today = "2026-11-23")
        assertFalse(report.hasIssues)
        assertEquals(0, report.issueCount)
    }
}
