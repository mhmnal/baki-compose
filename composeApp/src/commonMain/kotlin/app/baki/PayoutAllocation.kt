package app.baki

import kotlin.math.roundToLong

/** Allocate a statement payout across the unpaid gaps of one week, in integer cents. */
fun allocateStatementPayouts(
    shifts: List<Shift>,
    weekStart: String,
    amountCents: Long,
    now: Long = nowMillis(),
): List<Shift> {
    if (amountCents <= 0L) return shifts
    val relevant = shifts.filter { it.date >= weekStart && it.date < addDays(weekStart, 7) }
    if (relevant.any { shift -> shift.payouts.any { it.source == "statement:$weekStart" } }) return shifts
    val gaps = relevant.associate { shift ->
        shift.id to (shift.gross + shift.incentive - shift.payouts.sumOf { it.c }).coerceAtLeast(0L)
    }
    val totalGap = gaps.values.sum()
    if (totalGap <= 0L) return shifts
    val allocatable = amountCents.coerceAtMost(totalGap)
    val exact = gaps.mapValues { (_, gap) -> gap.toDouble() * allocatable / totalGap }
    val cents = exact.mapValues { (_, v) -> v.toLong() }.toMutableMap()
    var remainder = allocatable - cents.values.sum()
    // Give leftover cents to the largest fractional remainders first.
    exact.entries.sortedByDescending { it.value - it.value.toLong() }.forEach { (id, _) ->
        if (remainder <= 0L) return@forEach
        cents[id] = (cents[id] ?: 0L) + 1L
        remainder--
    }
    return shifts.map { shift ->
        val add = cents[shift.id] ?: 0L
        if (add <= 0L) shift else shift.copy(
            payouts = shift.payouts + Payout(add, now, "statement:$weekStart"),
            updated = nowIso(),
        )
    }
}

/** The statement-funded amount a week can still absorb. */
fun statementAllocationCapacity(store: Store, weekStart: String): Long {
    val end = addDays(weekStart, 7)
    return store.shifts
        .filter { it.date >= weekStart && it.date < end }
        .sumOf { (it.gross + it.incentive - it.payouts.sumOf { p -> p.c }).coerceAtLeast(0L) }
}
