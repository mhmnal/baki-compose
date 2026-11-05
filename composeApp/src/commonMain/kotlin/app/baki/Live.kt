package app.baki

import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.max

/** A shift in progress: device-local until you end it, exactly like the web app. */
@Serializable
data class LivePause(val from: Long, val to: Long? = null)

@Serializable
data class LiveShift(
    val start: Long,
    val payouts: List<Payout> = emptyList(),
    val note: String = "",
    /** metres actually travelled, measured by the GPS tracker while the shift runs */
    val km: Double = 0.0,
    /** breaks taken during the shift; time parked at lunch is not working time */
    val pauses: List<LivePause> = emptyList(),
)

private const val LIVE_FILE = "live.json"

fun lifeOf(l: LiveShift?): LiveShift? = l
fun loadLive(): LiveShift? {
    val raw = LocalStore.readText(LIVE_FILE) ?: return null
    return runCatching { BakiJson.decodeFromString<LiveShift>(raw) }.getOrNull()
}
fun saveLive(l: LiveShift?) {
    if (l == null) LocalStore.writeText(LIVE_FILE, "") else
        LocalStore.writeText(LIVE_FILE, BakiJson.encodeToString(LiveShift.serializer(), l))
}

fun liveTotalCents(l: LiveShift): Long = l.payouts.sumOf { it.c }
/** Wall-clock time since the shift started, breaks included. */
fun liveElapsedMs(l: LiveShift, now: Long = nowMillis()): Long = max(0L, now - l.start)

/** True while a break is open (no `to` yet). */
fun isPaused(l: LiveShift): Boolean = l.pauses.lastOrNull()?.to == null && l.pauses.isNotEmpty()

/** Total break time so far, counting the open break up to [now]. */
fun pausedMs(l: LiveShift, now: Long = nowMillis()): Long =
    l.pauses.sumOf { max(0L, (it.to ?: now) - it.from) }

/** Time actually on the clock: elapsed minus breaks. */
fun liveWorkedMs(l: LiveShift, now: Long = nowMillis()): Long =
    max(0L, liveElapsedMs(l, now) - pausedMs(l, now))

/** "3h 12m" — the same shape the web app's clock uses. */
fun liveClock(elapsedMs: Long): String {
    val totalMinutes = elapsedMs / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

/** Local wall-clock of the live start, e.g. "14:37". */
@OptIn(ExperimentalTime::class)
fun startClock(startMs: Long): String {
    val dt = Instant.fromEpochMilliseconds(startMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

/** Local date of the live start, e.g. "2026-11-05". */
@OptIn(ExperimentalTime::class)
fun startDate(startMs: Long): String =
    Instant.fromEpochMilliseconds(startMs).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
