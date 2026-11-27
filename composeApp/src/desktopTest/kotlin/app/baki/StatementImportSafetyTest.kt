package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementImportSafetyTest {
    private fun statement(weekly: Double = 387.70, incentives: Double = 16.80) = Statement(
        weekStart = "2026-11-16", income = 4177.0, deductions = 30.0, weekly = weekly,
        incentives = incentives, tips = 4.0, netGsf = 101.71, importedAt = "a",
    )

    @Test fun timestampOnlyIsNotAConflict() {
        assertTrue(sameStatement(statement(), statement().copy(importedAt = "b")))
    }

    @Test fun moneyChangeProducesAComparison() {
        val changes = statementChanges(statement(), statement(weekly = 400.00, incentives = 20.0))
        assertEquals(2, changes.size)
        assertEquals("Weekly payout", changes[0].label)
        assertFalse(sameStatement(statement(), statement(weekly = 400.0)))
    }
}
