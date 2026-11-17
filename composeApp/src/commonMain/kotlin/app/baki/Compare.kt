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
        km = shifts.sumOf { shiftKm(it, store.settings.profit) },
        entries = t.entryCount,
        shifts = t.shiftCount,
    )
}

/**
 * One row of a comparison table. The change is relative to period **B**: change = (A − B) / B, shown
 * as a signed percentage. `B` empty is not "−100%", it is "new"; both empty is "—".
 *
 * [good] says whether the change is desirable for *that* metric: more income is good, more spending
 * is bad, kilometres are neither.
 */
data class CompareRow(
    val label: String,
    val a: String,
    val b: String,
    val changeLabel: String?,
    val good: Boolean?,
    val higherIsBetter: Boolean?,
)

/** A percentage change from B to A, or the honest "new"/"—" wording when B has nothing. */
internal fun relativeChange(a: Long, b: Long): Pair<String?, Boolean?> = when {
    b == 0L && a == 0L -> "—" to null
    b == 0L -> "new" to true
    else -> {
        val pct = (a - b) * 100.0 / b
        ((if (pct >= 0) "+" else "") + "%.0f".format(pct) + "%") to (pct >= 0)
    }
}

/** A raw difference for rate rows (RM/h, km); percentages of rates are noise. */
internal fun rawChange(a: Double, b: Double, decimals: Int = 2): String? =
    if (b == 0.0 && a == 0.0) "—"
    else if (b == 0.0) "—"
    else {
        val pattern = "%." + decimals + "f"
        (if (a - b >= 0) "+" else "") + pattern.format(a - b)
    }

/** Goodness combines direction of the change with whether more is better for this metric. */
internal fun isGood(changePositive: Boolean?, higherIsBetter: Boolean?): Boolean? = when {
    changePositive == null || higherIsBetter == null -> null
    else -> changePositive == higherIsBetter
}

/** Expense totals per category for a month, biggest first. */
fun categoryTotals(store: Store, month: String): List<Pair<String, Long>> {
    val cats = store.settings.cats.catsOut.associateBy { it.id }
    return store.entries
        .filter { it.date.startsWith(month) && it.type != "income" }
        .groupBy { it.cat }
        .map { (id, list) -> (cats[id]?.name ?: id) to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
}


/**
 * The comparison table for two months. Money rows compare A against B with a relative change; rate
 * rows show a raw difference. Colour is decided by whether the movement is good for that metric.
 */
fun compareMonths(store: Store, aMonth: String, bMonth: String): List<CompareRow> {
    val a = periodStats(store, aMonth)
    val b = periodStats(store, bMonth)

    fun money(label: String, av: Long, bv: Long, higherBetter: Boolean?): CompareRow {
        val (text, positive) = relativeChange(av, bv)
        return CompareRow(
            label = label,
            a = "RM ${rm(av)}",
            b = "RM ${rm(bv)}",
            changeLabel = text,
            good = if (text == "new") higherBetter else isGood(positive, higherBetter),
            higherIsBetter = higherBetter,
        )
    }

    fun rate(label: String, av: Double, bv: Double, higherBetter: Boolean?): CompareRow {
        val text = rawChange(av, bv)
        val positive = if (text == null || text == "—") null else !text.startsWith("-")
        return CompareRow(
            label = label,
            a = rm(av),
            b = rm(bv),
            changeLabel = text,
            good = isGood(positive, higherBetter),
            higherIsBetter = higherBetter,
        )
    }

    return listOf(
        money("Net", a.net, b.net, true),
        money("In", a.income, b.income, true),
        money("Out", a.expense, b.expense, false),
        CompareRow(
            label = "Hours",
            a = "%.1f".format(a.hours), b = "%.1f".format(b.hours),
            changeLabel = rawChange(a.hours, b.hours, 1),
            good = isGood(if (a.hours == b.hours) null else a.hours > b.hours, true),
            higherIsBetter = true,
        ),
        CompareRow(
            label = "Kilometres",
            a = "%.0f".format(a.km), b = "%.0f".format(b.km),
            changeLabel = rawChange(a.km, b.km, 0),
            good = null,
            higherIsBetter = null,
        ),
        rate("RM / hour", a.perHour, b.perHour, true),
        rate("RM / km", a.perKm, b.perKm, true),
        CompareRow(
            label = "Entries",
            a = a.entries.toString(), b = b.entries.toString(),
            changeLabel = (b.entries - a.entries).let { if (a.entries == b.entries) "—" else (if (a.entries > b.entries) "+" else "") + (a.entries - b.entries) },
            good = null,
            higherIsBetter = null,
        ),
    )
}
