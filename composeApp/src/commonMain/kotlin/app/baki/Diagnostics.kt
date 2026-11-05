package app.baki

/** What the location tracker has actually been doing, so a silent failure becomes visible. */
data class TrackerInfo(
    val fixes: Int = 0,
    val rejected: Int = 0,
    val lastFixAt: Long = 0,
    val km: Double = 0.0,
    val provider: String = "—",
)

expect fun trackerInfo(): TrackerInfo

/** One place to read the health of the app, shown in More. */
@androidx.compose.runtime.Immutable
data class Health(
    val status: String,
    val lastSyncAt: Long?,
    val lastPushAt: Long?,
    val lastError: String?,
    val pendingPush: Boolean,
    val readOnly: Boolean,
    val entries: Int,
    val shifts: Int,
    val tracker: TrackerInfo,
    val live: LiveShift?,
    val gpsCorrection: Double,
)

fun healthOf(repo: BakiRepo): Health = Health(
    status = repo.status,
    lastSyncAt = repo.lastSyncAt,
    lastPushAt = repo.lastPushAt,
    lastError = repo.lastError,
    pendingPush = repo.pendingPush,
    readOnly = repo.readOnly,
    entries = repo.store.entries.size,
    shifts = repo.store.shifts.size,
    tracker = trackerInfo(),
    live = repo.live,
    gpsCorrection = repo.store.settings.gpsCorrection,
)

/** "12s ago", "3m ago", "2h ago" — short enough for a status row. */
fun ago(now: Long, then: Long?): String {
    if (then == null || then <= 0) return "never"
    val s = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

private fun hhmmss(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
}
