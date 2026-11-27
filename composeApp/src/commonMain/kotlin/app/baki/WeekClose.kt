package app.baki

import kotlin.math.roundToLong

/** Read-only view of one Monday–Sunday week for the close/reopen workflow. */
data class WeekCloseView(
    val weekStart: String,
    val weekEnd: String,
    val shiftCount: Int,
    val hours: Double,
    val recordedNet: Long,
    val recordedGross: Long,
    val recordedPayouts: Long,
    val statement: Statement?,
    val statementReconciled: Boolean,
    val close: WeekClose?,
    val received: Long,
    val isClosed: Boolean,
    val grossGap: Long?,
    val payoutVariance: Long,
    val cashVariance: Long?,
) {
    val hasActivity: Boolean get() = shiftCount > 0 || statement != null
    val closeDate: String? get() = close?.closedAt?.takeIf { it.isNotBlank() }
}

fun weekCloseView(store: Store, weekStart: String): WeekCloseView {
    val end = addDays(weekStart, 7)
    val shifts = store.shifts.filter { it.date >= weekStart && it.date < end }
    val statement = store.statements[weekStart]
    val close = store.weekCloses[weekStart]
    val prof = store.settings.profit
    val overhead = overheadPerHour(store, weekStart.take(7))
    val net = shifts.sumOf { shiftMetrics(it, prof, overhead).net * 100 }.roundToLong()
    val gross = shifts.sumOf { it.gross + it.incentive }
    val payouts = shifts.sumOf { sh -> sh.payouts.sumOf { it.c } }
    val received = close?.receivedCents ?: payouts
    val statementWeekly = statement?.let { (it.weekly * 100).roundToLong() }
    val statementGross = statement?.let { (it.income * 100).roundToLong() }
    return WeekCloseView(
        weekStart = weekStart,
        weekEnd = addDays(weekStart, 6),
        shiftCount = shifts.size,
        hours = shifts.sumOf { it.hours },
        recordedNet = net,
        recordedGross = gross,
        recordedPayouts = payouts,
        statement = statement,
        statementReconciled = store.statementMarks[weekStart]?.reconciled == true,
        close = close,
        received = received,
        isClosed = close?.closedAt?.isNotBlank() == true,
        grossGap = statementGross?.let { gross - it },
        payoutVariance = received - payouts,
        cashVariance = statementWeekly?.let { received - it },
    )
}

/** A selectable week for the close/history picker. */
data class WeekOption(
    val weekStart: String,
    val weekEnd: String,
    val shiftCount: Int,
    val hasStatement: Boolean,
    val isClosed: Boolean,
)

fun weekOptions(store: Store, today: String): List<WeekOption> {
    val weeks = linkedSetOf(weekStartOf(today))
    for (sh in store.shifts) weeks += weekStartOf(sh.date)
    for (week in store.statements.keys) weeks += week
    for (week in store.weekCloses.keys) weeks += week
    return weeks
        .filter { isValidIsoDate(it) && weekStartOf(it) == it }
        .sortedDescending()
        .map { week ->
            WeekOption(
                weekStart = week,
                weekEnd = addDays(week, 6),
                shiftCount = store.shifts.count { it.date >= week && it.date < addDays(week, 7) },
                hasStatement = store.statements.containsKey(week),
                isClosed = store.weekCloses[week]?.closedAt?.isNotBlank() == true,
            )
        }
}

/** What still blocks a confident weekly close. */
enum class WeekCloseStep { USE_STATEMENT, ALLOCATE, RECONCILE, CLOSE }

data class WeekReadiness(
    val receivedMatches: Boolean,
    val allocationComplete: Boolean,
    val reconciled: Boolean,
    val isClosed: Boolean,
    val nextStep: WeekCloseStep?,
) {
    val readyToClose: Boolean
        get() = !isClosed && receivedMatches && allocationComplete && reconciled
}

fun weekReadiness(
    view: WeekCloseView,
    enteredReceivedCents: Long,
    remainingCapacityCents: Long,
    alreadyAllocated: Boolean,
    closedReceivedCents: Long = 0L,
): WeekReadiness {
    val statementCents = view.statement?.let { (it.weekly * 100).roundToLong() }
    val received = enteredReceivedCents.takeIf { it > 0L } ?: closedReceivedCents
    val receivedMatches = statementCents != null && received == statementCents
    val allocationComplete = alreadyAllocated || remainingCapacityCents <= 0L
    val reconciled = view.statementReconciled
    val nextStep = when {
        view.isClosed || view.statement == null -> null
        !receivedMatches -> WeekCloseStep.USE_STATEMENT
        !allocationComplete -> WeekCloseStep.ALLOCATE
        !reconciled -> WeekCloseStep.RECONCILE
        else -> WeekCloseStep.CLOSE
    }
    return WeekReadiness(
        receivedMatches = receivedMatches,
        allocationComplete = allocationComplete,
        reconciled = reconciled,
        isClosed = view.isClosed,
        nextStep = nextStep,
    )
}
