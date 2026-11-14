package app.baki

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
private data class RouteFile(
    val v: Int = 1,
    val routes: Map<String, ShiftRoute> = emptyMap(),
)

/**
 * Routes live in their own encrypted file and are **never synced**: a movement history is the most
 * sensitive thing this app holds, and putting thousands of points into `store.json` would also
 * bloat every upload. Retention is bounded so the file cannot grow without limit.
 */
object RouteStore {

    private const val FILE = "routes.json"
    const val KEEP_DAYS = 90

    private val mutex = Mutex()

    /** In-memory copy so a batch append does not re-read and re-decrypt the file. */
    private var cache: RouteFile? = null

    private fun loadUnlocked(): RouteFile {
        cache?.let { return it }
        val raw = try {
            LocalStore.readText(FILE)
        } catch (e: Exception) {
            runCatching { LocalStore.quarantine(FILE) }
            StoreHealth.loadError = "Route history could not be opened (${e.message ?: "decrypt failed"})."
            null
        }
        val loaded = if (raw.isNullOrBlank()) {
            RouteFile()
        } else {
            runCatching { BakiJson.decodeFromString(RouteFile.serializer(), raw) }.getOrElse { RouteFile() }
        }
        cache = loaded
        return loaded
    }

    private fun writeUnlocked(file: RouteFile) {
        cache = file
        LocalStore.writeText(FILE, BakiJson.encodeToString(RouteFile.serializer(), file))
    }

    /** Append a batch of fixes to one shift's route. Called from the tracker, off the main thread. */
    suspend fun append(shiftId: String, date: String, points: List<RoutePoint>) {
        if (shiftId.isBlank() || points.isEmpty()) return
        mutex.withLock {
            val file = loadUnlocked()
            val existing = file.routes[shiftId]
            val merged = if (existing == null) {
                ShiftRoute(shiftId = shiftId, date = date, points = points)
            } else {
                existing.copy(points = existing.points + points)
            }
            writeUnlocked(file.copy(routes = file.routes + (shiftId to merged)))
        }
    }

    /** Replace one shift's route wholesale — used when a trace arrives from another device. */
    suspend fun put(route: ShiftRoute) {
        if (route.shiftId.isBlank() || route.points.size < 2) return
        mutex.withLock {
            val file = loadUnlocked()
            writeUnlocked(file.copy(routes = file.routes + (route.shiftId to route)))
        }
    }

    /** Simplify on completion: keeps the drawing faithful while the file stays small. */
    suspend fun finalize(shiftId: String) = mutex.withLock {
        val file = loadUnlocked()
        val route = file.routes[shiftId] ?: return@withLock
        val simplified = route.copy(points = simplifyRoute(route.points))
        writeUnlocked(file.copy(routes = file.routes + (shiftId to simplified)))
        pruneUnlocked(file.copy(routes = file.routes + (shiftId to simplified)))
    }

    /** Move a path onto the id of the shift that was just created for it. */
    suspend fun rename(from: String, to: String) = mutex.withLock {
        if (from == to || from.isBlank() || to.isBlank()) return@withLock
        val file = loadUnlocked()
        val route = file.routes[from] ?: return@withLock
        writeUnlocked(
            file.copy(
                routes = (file.routes - from) + (to to route.copy(shiftId = to)),
            ),
        )
    }

    /** Drop paths whose shift no longer exists (deleted before the rename, or legacy keys). */
    suspend fun pruneOrphans(liveShiftIds: Set<String>) = mutex.withLock {
        val file = loadUnlocked()
        val kept = file.routes.filterValues { it.shiftId in liveShiftIds }
        if (kept.size != file.routes.size) writeUnlocked(file.copy(routes = kept))
    }

    suspend fun route(shiftId: String): ShiftRoute? = mutex.withLock {
        val file = loadUnlocked()
        file.routes[shiftId]
    }

    /** Every recorded route for one day, for the day map. */
    suspend fun routesForDate(date: String): List<ShiftRoute> = mutex.withLock {
        val file = loadUnlocked()
        file.routes.values.filter { it.date == date }
    }

    /** Every stored path from [fromDate] onwards, for export. */
    suspend fun routesSince(fromDate: String): List<ShiftRoute> = mutex.withLock {
        loadUnlocked().routes.values.filter { it.date >= fromDate }.sortedBy { it.date }
    }

    suspend fun delete(shiftId: String) = mutex.withLock {
        val file = loadUnlocked()
        if (file.routes.containsKey(shiftId)) {
            writeUnlocked(file.copy(routes = file.routes - shiftId))
        }
    }

    suspend fun prune(keepDays: Int = KEEP_DAYS) = mutex.withLock {
        pruneUnlocked(loadUnlocked(), keepDays)
    }

    private fun pruneUnlocked(file: RouteFile, keepDays: Int = KEEP_DAYS) {
        val cutoff = addDays(todayIso(), -keepDays)
        val kept = file.routes.filterValues { it.date >= cutoff }
        if (kept.size != file.routes.size) writeUnlocked(file.copy(routes = kept))
    }

    /** Test/diagnostics helper: how much we are holding, and for how many days. */
    suspend fun stats(): Pair<Int, Int> = mutex.withLock {
        val file = loadUnlocked()
        file.routes.size to file.routes.values.sumOf { it.points.size }
    }
}
