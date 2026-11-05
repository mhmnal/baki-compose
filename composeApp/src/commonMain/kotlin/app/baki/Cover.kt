package app.baki

import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.math.roundToLong

/**
 * Grab bills the daily insurance once per rolling 24h window. A shift is charged only when
 * no earlier shift started within the previous 24 hours.
 *
 * Ported from coverChargedIds()/recomputeCover()/syncShiftFee() in index.html, including the
 * linked `fee-<shiftId>` expense entry, because the cash total depends on it.
 */
const val DAY_MS: Long = 86_400_000L

@OptIn(ExperimentalTime::class)
fun shiftStartMs(sh: Shift, tz: TimeZone = TimeZone.currentSystemDefault()): Long {
    val date = runCatching { LocalDate.parse(sh.date) }.getOrNull() ?: return 0L
    val midnight = date.atStartOfDayIn(tz).toEpochMilliseconds()
    val parts = sh.start.split(":")
    val minutes = if (parts.size == 2) {
        (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    } else 0
    return midnight + minutes * 60_000L
}

fun coverChargedIds(shifts: List<Shift>, tz: TimeZone = TimeZone.currentSystemDefault()): Set<String> {
    val sorted = shifts.sortedBy { shiftStartMs(it, tz) }
    val charged = mutableSetOf<String>()
    var last = Long.MIN_VALUE
    for (sh in sorted) {
        val t = shiftStartMs(sh, tz)
        if (last == Long.MIN_VALUE || t - last >= DAY_MS) {
            charged += sh.id
            last = t
        }
    }
    return charged
}

/** Would this (unsaved) shift be charged? Used by the shift form. */
fun draftCoverCharge(store: Store, date: String, start: String): Long {
    val fee = (store.settings.profit.grabInsurance * 100).roundToLong()
    val pseudo = Shift(id = "__draft", date = date, start = start)
    val others = store.shifts.filter { it.id != pseudo.id }
    return if (coverChargedIds(others + pseudo).contains(pseudo.id)) fee else 0L
}

/**
 * Re-derives every shift's fee and keeps the linked expense entries in step.
 * Returns the new store plus whether anything actually changed.
 */
fun recomputeCover(store: Store, now: String = nowIso()): Pair<Store, Boolean> {
    val fee = (store.settings.profit.grabInsurance * 100).roundToLong()
    val charged = coverChargedIds(store.shifts)
    var changed = false

    val shifts = store.shifts.map { sh ->
        val want = if (sh.id in charged) fee else 0L
        if ((sh.grabFee ?: 0L) != want) { changed = true; sh.copy(grabFee = want) } else sh
    }

    val byId = store.entries.associateBy { it.id }.toMutableMap()
    for (sh in shifts) {
        val id = "fee-${sh.id}"
        val current = byId[id]
        val want = sh.grabFee ?: 0L
        if (want <= 0) {
            if (current != null) { byId.remove(id); changed = true }
            continue
        }
        val row = Entry(
            id = id, date = sh.date, type = "expense", cat = "grabfee", amount = want,
            note = "Grab insurance", updated = current?.updated ?: now,
        )
        if (current == null || current.amount != row.amount || current.date != row.date) {
            byId[id] = row
            changed = true
        }
    }

    if (!changed) return store to false
    val updated = store.copy(
        shifts = shifts,
        entries = byId.values.toList(),
        updatedAt = now,
    )
    return updated to true
}

/**
 * When the paid 24h insurance window ends: the last charged shift start + 24h.
 * A shift started before that moment costs nothing extra.
 */
fun coverPaidUntil(store: Store, tz: TimeZone = TimeZone.currentSystemDefault()): Long? {
    val charged = coverChargedIds(store.shifts, tz)
    val last = store.shifts.filter { it.id in charged }.maxByOrNull { shiftStartMs(it, tz) } ?: return null
    return shiftStartMs(last, tz) + DAY_MS
}

/** "00:00" / "14:30" local clock for a moment. */
@OptIn(kotlin.time.ExperimentalTime::class)
fun clockOf(ms: Long, tz: TimeZone = TimeZone.currentSystemDefault()): String =
    startClock(ms)
