package app.baki

import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.max

/** A shift in progress: device-local until you end it, exactly like the web app. */
@Serializable
data class LiveShift(
    val start: Long,
    val payouts: List<Payout> = emptyList(),
    val note: String = "",
    /** metres actually travelled, measured by the GPS tracker while the shift runs */
    val km: Double = 0.0,
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
fun liveElapsedMs(l: LiveShift, now: Long = nowMillis()): Long = max(0L, now - l.start)

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
