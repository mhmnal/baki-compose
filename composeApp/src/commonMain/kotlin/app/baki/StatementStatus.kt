package app.baki

/** Derived status of one statement week; no extra persisted state. */
data class StatementWeekStatus(
    val allocated: Boolean,
    val reconciled: Boolean,
    val closed: Boolean,
) {
    val readyToClose: Boolean get() = allocated && reconciled && closed.not()
}

fun statementWeekStatus(store: Store, weekStart: String): StatementWeekStatus =
    StatementWeekStatus(
        allocated = store.shifts.any { shift ->
            weekStartOf(shift.date) == weekStart && shift.payouts.any { it.source == "statement:$weekStart" }
        },
        reconciled = store.statementMarks[weekStart]?.reconciled == true,
        closed = store.weekCloses[weekStart]?.closedAt?.isNotBlank() == true,
    )
