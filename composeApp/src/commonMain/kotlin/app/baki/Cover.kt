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

/**
 * How many insurance windows each shift consumes.
 *
 * Grab bills a rolling 24h window. A shift that starts inside an already-paid window costs nothing
 * up front, but if it is still running when that window ends, a new one starts immediately — the
 * renewal belongs to the shift that is happening, not to the next shift. A 30h shift can consume two
 * renewals; the loop handles that without special cases.
 */
fun coverWindows(shifts: List<Shift>, tz: TimeZone = TimeZone.currentSystemDefault()): Map<String, Int> {
    val sorted = shifts.sortedBy { shiftStartMs(it, tz) }
    val windows = mutableMapOf<String, Int>()
    var paidUntil = Long.MIN_VALUE
    for (sh in sorted) {
        val start = shiftStartMs(sh, tz)
        val end = start + (sh.hours * 3_600_000.0).toLong()
        var count = 0
        if (paidUntil == Long.MIN_VALUE || start >= paidUntil) {
            count++
            paidUntil = start + DAY_MS
        }
        // still working when the paid window ends: renew, and keep renewing for very long shifts
        while (paidUntil < end) {
            count++
            paidUntil += DAY_MS
        }
        windows[sh.id] = count
    }
    return windows
}

/** Ids of shifts that pay at least one window. */
fun coverChargedIds(shifts: List<Shift>, tz: TimeZone = TimeZone.currentSystemDefault()): Set<String> =
    coverWindows(shifts, tz).filterValues { it > 0 }.keys

/** Windows the live shift has consumed by [now], including the one it will be charged at its start. */
fun liveCoverWindows(
    store: Store,
    startMs: Long,
    nowMs: Long,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val prior = coverPaidUntil(store, tz)
    var paidUntil = if (prior == null || startMs >= prior) startMs + DAY_MS else prior
    var count = if (prior == null || startMs >= prior) 1 else 0
    while (paidUntil < nowMs) {
        count++
        paidUntil += DAY_MS
    }
    return count
}

/** The part of the live insurance that has already renewed beyond the shift's up-front window. */
fun liveRenewedWindows(store: Store, startMs: Long, nowMs: Long): Int {
    val atStart = liveCoverWindows(store, startMs, startMs)
    return (liveCoverWindows(store, startMs, nowMs) - atStart).coerceAtLeast(0)
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
    val windows = coverWindows(store.shifts)
    var changed = false

    val shifts = store.shifts.map { sh ->
        val want = (windows[sh.id] ?: 0) * fee
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
            loggedAt = current?.loggedAt ?: now,
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
    if (store.shifts.isEmpty()) return null
    var paidUntil = Long.MIN_VALUE
    coverWindows(store.shifts, tz) // keep the loop and the rule in one place
    val sorted = store.shifts.sortedBy { shiftStartMs(it, tz) }
    for (sh in sorted) {
        val start = shiftStartMs(sh, tz)
        val end = start + (sh.hours * 3_600_000.0).toLong()
        if (paidUntil == Long.MIN_VALUE || start >= paidUntil) paidUntil = start + DAY_MS
        while (paidUntil < end) paidUntil += DAY_MS
    }
    return paidUntil
}

/** "00:00" / "14:30" local clock for a moment. */
@OptIn(kotlin.time.ExperimentalTime::class)
fun clockOf(ms: Long, tz: TimeZone = TimeZone.currentSystemDefault()): String =
    startClock(ms)
