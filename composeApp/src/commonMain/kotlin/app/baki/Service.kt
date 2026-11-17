package app.baki

/** Last logged service and the user-set next target. No interval is inferred. */
data class ServiceInfo(
    val last: Entry?,
    val latestOdo: Int?,
    val targetKm: Int?,
    val targetOn: String?,
) {
    val remainingKm: Int? get() = if (targetKm != null && latestOdo != null) targetKm - latestOdo else null
    val overdue: Boolean get() = (remainingKm != null && remainingKm!! <= 0) ||
        (targetOn != null && targetOn < todayIso())
}

fun serviceInfo(store: Store, baseOdo: Int? = null, baseOn: String? = null): ServiceInfo {
    val last = store.entries
        .filter { it.cat == "maintenance" }
        .maxWithOrNull(compareBy<Entry> { it.date }.thenBy { it.loggedAt.ifBlank { it.updated } })
    val latestOdo = (listOfNotNull(baseOdo?.takeIf { it > 0 }) + store.entries
        .filter { baseOn == null || it.date >= baseOn }
        .mapNotNull { it.odo?.takeIf { o -> o > 0 } }).maxOrNull()
    return ServiceInfo(
        last = last,
        latestOdo = latestOdo,
        targetKm = store.settings.nextServiceKm,
        targetOn = store.settings.nextServiceOn,
    )
}
