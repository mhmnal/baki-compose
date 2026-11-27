package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementStatusTest {
    @Test fun statusIsDerivedFromAllocationMarkAndClose() {
        val week = "2026-11-16"
        val store = Store(
            shifts = listOf(
                Shift(
                    id = "s", date = "2026-11-17", start = "09:00", hours = 1.0, km = 1.0, gross = 100,
                    payouts = listOf(Payout(100, 1, "statement:$week")),
                ),
            ),
            statementMarks = mapOf(week to StatementMark(true, "2026-11-23T00:00:00")),
            weekCloses = mapOf(week to WeekClose(weekStart = week, closedAt = "2026-11-23T01:00:00", receivedCents = 100)),
        )
        val status = statementWeekStatus(store, week)
        assertTrue(status.allocated)
        assertTrue(status.reconciled)
        assertTrue(status.closed)
        assertFalse(status.readyToClose)
        assertEquals(status, statementWeekStatus(store, week))
    }
}
