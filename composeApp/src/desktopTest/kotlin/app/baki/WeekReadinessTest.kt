package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekReadinessTest {
    private val statement = Statement(weekStart = "2026-11-16", income = 4177.0, deductions = 30.0, weekly = 387.70)
    private fun view(reconciled: Boolean = false, closed: Boolean = false) = WeekCloseView(
        weekStart = "2026-11-16", weekEnd = "2026-11-22", shiftCount = 1, hours = 2.0,
        recordedNet = 100, recordedGross = 100, recordedPayouts = 100, statement = statement,
        statementReconciled = reconciled, close = null, received = if (closed) 38_770 else 100,
        isClosed = closed, grossGap = 0, payoutVariance = 0, cashVariance = 0,
    )

    @Test fun statementsAreNotReadyUntilReceivedMatches() {
        val r = weekReadiness(view(), enteredReceivedCents = 0, remainingCapacityCents = 0, alreadyAllocated = true)
        assertFalse(r.receivedMatches)
        assertEquals(WeekCloseStep.USE_STATEMENT, r.nextStep)
        assertFalse(r.readyToClose)
    }

    @Test fun allocationIsTheNextStepWhenMissing() {
        val r = weekReadiness(view(), enteredReceivedCents = 38_770, remainingCapacityCents = 50, alreadyAllocated = false)
        assertTrue(r.receivedMatches)
        assertFalse(r.allocationComplete)
        assertEquals(WeekCloseStep.ALLOCATE, r.nextStep)
    }

    @Test fun reconciledAndMatchedWeekIsReady() {
        val r = weekReadiness(view(reconciled = true), enteredReceivedCents = 38_770, remainingCapacityCents = 0, alreadyAllocated = true)
        assertTrue(r.readyToClose)
        assertEquals(WeekCloseStep.CLOSE, r.nextStep)
    }

    @Test fun closedWeekHasNoNextStep() {
        val r = weekReadiness(view(reconciled = true, closed = true), enteredReceivedCents = 0, remainingCapacityCents = 0, alreadyAllocated = true, closedReceivedCents = 38_770)
        assertFalse(r.readyToClose)
        assertEquals(null, r.nextStep)
    }
}
