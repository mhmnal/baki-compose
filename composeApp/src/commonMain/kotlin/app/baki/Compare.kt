package app.baki

/**
 * The numbers a period comparison needs. Money is in cents, distances in kilometres.
 *
 * Lives in common code so the phone can grow the same view later without a second implementation.
 */
data class PeriodStats(
    val month: String,
    val net: Long,
    val income: Long,
    val expense: Long,
    val hours: Double,
    val km: Double,
    val entries: Int,
    val shifts: Int,
) {
    /** Ringgit per hour actually worked; 0 when there were no shifts. */
    val perHour: Double get() = if (hours > 0.01) net / 100.0 / hours else 0.0

    /** Ringgit earned per kilometre driven. */
    val perKm: Double get() = if (km > 0.01) net / 100.0 / km else 0.0
}

/** Totals for one month, from the same primitives the rest of the app uses. */
fun periodStats(store: Store, month: String): PeriodStats {
    val t = monthTotals(store, month)
    val shifts = store.shifts.filter { it.date.startsWith(month) }
    return PeriodStats(
        month = month,
        net = t.net,
        income = t.income,
        expense = t.expense,
        hours = shifts.sumOf { it.hours },
        km = shifts.sumOf { it.km },
        entries = t.entryCount,
        shifts = t.shiftCount,
    )
}

/** One row of a comparison table: a label, both period values, and their difference. */
data class CompareRow(
    val label: String,
    val a: String,
    val b: String,
    /** b − a; null when a difference makes no sense for this row. */
    val delta: Long?,
    val deltaLabel: String?,
    /** true when a rise is good, false when it is bad, null when neutral. */
    val higherIsBetter: Boolean?,
)

/** Expense totals per category for a month, biggest first. */
fun categoryTotals(store: Store, month: String): List<Pair<String, Long>> {
    val cats = store.settings.cats.catsOut.associateBy { it.id }
    return store.entries
        .filter { it.date.startsWith(month) && it.type != "income" }
        .groupBy { it.cat }
        .map { (id, list) -> (cats[id]?.name ?: id) to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
}

private fun pct(a: Long, b: Long): String {
    if (a == 0L) return if (b == 0L) "—" else "+∞"
    val change = (b - a) * 100.0 / kotlin.math.abs(a)
    return (if (change >= 0) "+" else "") + "%.0f".format(change) + "%"
}

/**
 * The comparison table for two months. Money rows carry a signed delta and a percentage; rate rows
 * (per hour, per km) show the raw change because a percentage of a rate is noise.
 */
fun compareMonths(store: Store, aMonth: String, bMonth: String): List<CompareRow> {
    val a = periodStats(store, aMonth)
    val b = periodStats(store, bMonth)
    fun money(label: String, av: Long, bv: Long, higherBetter: Boolean) = CompareRow(
        label = label,
        a = "RM ${rm(av)}",
        b = "RM ${rm(bv)}",
        delta = bv - av,
        deltaLabel = pct(av, bv),
        higherIsBetter = higherBetter,
    )
    fun rate(label: String, av: Double, bv: Double, higherBetter: Boolean) = CompareRow(
        label = label,
        a = "%.2f".format(av),
        b = "%.2f".format(bv),
        delta = null,
        deltaLabel = (if (bv - av >= 0) "+" else "") + "%.2f".format(bv - av),
        higherIsBetter = higherBetter,
    )
    return listOf(
        money("Net", a.net, b.net, true),
        money("In", a.income, b.income, true),
        money("Out", a.expense, b.expense, false),
        CompareRow("Hours", "%.1f".format(a.hours), "%.1f".format(b.hours), null,
            (if (b.hours - a.hours >= 0) "+" else "") + "%.1f".format(b.hours - a.hours), true),
        CompareRow("Kilometres", "%.0f".format(a.km), "%.0f".format(b.km), null,
            (if (b.km - a.km >= 0) "+" else "") + "%.0f".format(b.km - a.km), null),
        rate("RM / hour", a.perHour, b.perHour, true),
        rate("RM / km", a.perKm, b.perKm, true),
        CompareRow("Entries", a.entries.toString(), b.entries.toString(), null,
            (if (b.entries - a.entries >= 0) "+" else "") + (b.entries - a.entries), null),
    )
}
