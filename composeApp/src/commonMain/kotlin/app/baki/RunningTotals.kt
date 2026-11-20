package app.baki

/** A cumulative point for the running-net visual. Label is an ISO date or month. */
data class RunningPoint(val label: String, val total: Long)

private fun theMonthAfter(month: String): String = addDays(endOfMonth(month), 1).take(7)

/** Every calendar day of [month], accumulated in recorded-net order. */
fun monthRunningPoints(store: Store, month: String): List<RunningPoint> {
    val today = todayIso()
    val days = monthGrid(month).filterNotNull().let { days ->
        if (month == today.take(7)) days.filter { it <= today } else days
    }
    var total = 0L
    return days.map { date ->
        total += dayTotals(store, date).net
        RunningPoint(date, total)
    }
}

/**
 * All-time recorded net at each month end, starting with the first month that has data.
 * Months without activity are included as flat steps so the line stays chronological.
 */
fun allTimeRunningPoints(store: Store): List<RunningPoint> {
    val dates = (store.entries.map { it.date } + store.shifts.map { it.date })
        .filter { it.length >= 7 }
    if (dates.isEmpty()) return emptyList()
    val first = dates.minOf { it.take(7) }
    val current = todayIso().take(7)
    var month = first
    var total = 0L
    val points = mutableListOf<RunningPoint>()
    while (month <= current) {
        total += monthTotals(store, month).net
        points += RunningPoint(month, total)
        month = theMonthAfter(month)
    }
    return points
}
