package app.baki

import kotlin.math.roundToLong

private const val AUTO_PREFIX = "statement-auto-"

fun autoStatementEntryIds(weekStart: String): Set<String> = setOf(
    "$AUTO_PREFIX$weekStart-incentive",
    "$AUTO_PREFIX$weekStart-tips",
    "$AUTO_PREFIX$weekStart-delivery",
)

/**
 * Post statement incentives/tips as ordinary income entries without duplicating them.
 *
 * The PDF's income total already includes fares plus incentives/tips. A shift may also carry an
 * explicit incentive, so only the part not already recorded on a shift is posted here. Net GSF is
 * never posted: it is a fee reference, not additional income.
 */
fun postStatementIncome(
    store: Store,
    statement: Statement,
    now: String = nowIso(),
): Store {
    val weekStart = statement.weekStart
    val weekEnd = addDays(weekStart, 6)
    val shiftIncentives = store.shifts
        .filter { it.date >= weekStart && it.date <= weekEnd }
        .sumOf { it.incentive }
    val incentiveCents = ((statement.incentives * 100).roundToLong() - shiftIncentives).coerceAtLeast(0)
    val tipsCents = (statement.tips * 100).roundToLong().coerceAtLeast(0)
    val deliveryCents = (statement.deliveryIncentive * 100).roundToLong().coerceAtLeast(0)
    val incomeCats = store.settings.cats.catsIn.map { it.id }.toSet()
    fun cat(preferred: String): String =
        when {
            preferred in incomeCats -> preferred
            "otherin" in incomeCats -> "otherin"
            incomeCats.isNotEmpty() -> incomeCats.first()
            else -> preferred
        }

    val existing = store.entries.associateBy { it.id }
    fun entry(id: String, category: String, amount: Long, note: String): Entry {
        val old = existing[id]
        if (old != null && old.date == weekStart && old.amount == amount && old.cat == category && old.note == note) {
            return old
        }
        return Entry(
            id = id, date = weekStart, type = "income", cat = category, amount = amount,
            note = note, updated = now, loggedAt = now,
        )
    }
    val generated = buildList {
        if (incentiveCents > 0) add(
            entry("$AUTO_PREFIX$weekStart-incentive", cat("bonus"), incentiveCents, "Statement W$weekStart · incentives"),
        )
        if (tipsCents > 0) add(
            entry("$AUTO_PREFIX$weekStart-tips", cat("tips"), tipsCents, "Statement W$weekStart · tips"),
        )
        if (deliveryCents > 0) add(
            entry("$AUTO_PREFIX$weekStart-delivery", cat("bonus"), deliveryCents, "Statement W$weekStart · delivery incentive"),
        )
    }

    val ids = autoStatementEntryIds(weekStart)
    return store.copy(
        entries = store.entries.filterNot { it.id in ids } + generated,
        updatedAt = now,
    )
}
