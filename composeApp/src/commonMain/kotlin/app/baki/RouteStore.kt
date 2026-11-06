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
        val raw = LocalStore.readText(FILE)
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

    /** Simplify on completion: keeps the drawing faithful while the file stays small. */
    suspend fun finalize(shiftId: String) = mutex.withLock {
        val file = loadUnlocked()
        val route = file.routes[shiftId] ?: return@withLock
        val simplified = route.copy(points = simplifyRoute(route.points))
        writeUnlocked(file.copy(routes = file.routes + (shiftId to simplified)))
        pruneUnlocked(file.copy(routes = file.routes + (shiftId to simplified)))
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
