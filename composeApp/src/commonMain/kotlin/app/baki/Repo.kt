package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.random.Random

/** A collision-proof-enough id: timestamp + random suffix. */
fun newId(prefix: String = "c"): String {
    val t = (nowMillis()).toString(36)
    val r = (0 until 6).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
    return "$prefix-$t$r"
}

/**
 * Two-way sync with the tracker server.
 *
 * Read is safe; writes go through [addExpense] which saves locally first, then pushes the merged
 * store. The server merges by id with tombstones, so the phone and this app can both write.
 */
class BakiRepo(initial: ClientConfig = loadConfig()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var client = TrackerClient(initial.serverUrl)

    var config by mutableStateOf(initial)
        private set
    var store by mutableStateOf(loadLocal())
        private set
    var live by mutableStateOf(loadLive())
        private set
    var status by mutableStateOf("local")
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    val readOnly: Boolean get() = !config.allowWrites

    private fun blocked(): Boolean {
        if (config.allowWrites) return false
        lastError = "Read-only mode: writes are off (More → Allow writes)"
        return true
    }

    fun setAllowWrites(on: Boolean) {
        config = config.copy(allowWrites = on)
        saveConfig(config)
        lastError = null
        PlatformEffects.tapFeedback()
    }

    fun start() {
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
        // the location tracker writes to live.json from the service; pick it up in the UI
        scope.launch {
            while (true) {
                if (live != null) {
                    val disk = loadLive()
                    if (disk != null && disk.km != live?.km) live = disk
                }
                delay(4_000)
            }
        }
        scope.launch {
            while (true) {
                runCatching { sync() }
                delay(if (status == "offline") 5_000 else 20_000)
            }
        }
        scope.launch {
            while (true) {
                runCatching {
                    client.events().collect { sync() }
                }
                delay(3_000)          // server restarts / network blips: retry the stream
            }
        }
    }

    suspend fun sync() = mutex.withLock {
        status = "syncing"
        try {
            val remote = client.pull()
            val merged = mergeStores(store, remote)
            // push back so local edits reach the server; a no-op merge is harmless
            val saved = if (config.allowWrites) client.push(merged) else merged
            store = mergeStores(merged, saved)
            saveLocal(store)
            status = "synced"
            lastError = null
        } catch (e: Exception) {
            status = if (store.entries.isEmpty() && store.shifts.isEmpty()) "offline" else "cached"
            lastError = e.message
        }
    }

    suspend fun addExpense(amountCents: Long, cat: String, note: String, date: String, odo: Int? = null) =
        addEntry("expense", amountCents, cat, note, date, odo)

    suspend fun addEntry(
        type: String, amountCents: Long, cat: String, note: String, date: String, odo: Int? = null,
    ) = mutex.withLock {
        if (blocked()) return@withLock
        val entry = Entry(
            id = newId(), date = date, type = type, cat = cat, amount = amountCents,
            note = note, updated = nowIso(), odo = odo,
        )
        store = store.copy(entries = store.entries + entry, updatedAt = nowIso())
        saveLocal(store)                       // never lose a tap, even offline
        try {
            val saved = client.push(store)
            store = mergeStores(store, saved)
            saveLocal(store)
            status = "synced"
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
    }

    /** Log a past shift. The cover fee is derived from the 24h rule, never typed. */
    suspend fun addShift(
        date: String, start: String, hours: Double, km: Double,
        grossCents: Long, extraCents: Long = 0, note: String = "",
        odoStart: Double? = null, odoEnd: Double? = null,
    ) = mutex.withLock {
        if (blocked()) return@withLock
        val charge = draftCoverCharge(store, date, start)
        val shift = Shift(
            id = newId("s"), date = date, start = start, hours = hours, km = km,
            gross = grossCents, extra = extraCents, grabFee = charge, note = note,
            odoStart = odoStart, odoEnd = odoEnd, updated = nowIso(),
        )
        // recompute over the whole timeline so earlier shifts stay correct
        val withShift = store.copy(shifts = store.shifts + shift, updatedAt = nowIso())
        store = recomputeCover(withShift).first
        saveLocal(store)
        try {
            val saved = client.push(store)
            store = mergeStores(store, saved)
            saveLocal(store)
            status = "synced"
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
    }

    fun startLive() {
        if (live != null) return
        live = LiveShift(start = nowMillis())
        saveLive(live)
        PlatformEffects.successFeedback()
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    fun addPayout(cents: Long) {
        val current = live ?: return
        if (cents <= 0) return
        live = current.copy(payouts = current.payouts + Payout(cents, nowMillis()))
        saveLive(live)
        PlatformEffects.tapFeedback()
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    /** Ends the live shift and writes it as an ordinary shift, cover fee derived by the rule. */
    suspend fun endLive(now: Long = nowMillis()) = mutex.withLock {
        if (blocked()) return@withLock
        val current = live ?: return@withLock
        val total = liveTotalCents(current)
        val date = startDate(current.start)
        val start = startClock(current.start)
        val hours = max(0.05, liveElapsedMs(current, now) / 3_600_000.0)
        // real distance when the tracker saw enough movement, else the old hours × average guess
        val measuredKm = current.km

        live = null
        saveLive(null)
        PlatformEffects.liveChanged(null, 0)
        PlatformEffects.successFeedback()
        if (total <= 0) return@withLock       // nothing earned: nothing to write

        val charge = draftCoverCharge(store, date, start)
        val shift = Shift(
            id = newId("s"), date = date, start = start, hours = hours,
            km = if (measuredKm >= 0.3) measuredKm else 0.0,
            gross = total, grabFee = charge, updated = nowIso(),
            payouts = current.payouts,
        )
        store = recomputeCover(store.copy(shifts = store.shifts + shift, updatedAt = nowIso())).first
        saveLocal(store)
        try {
            val saved = client.push(store)
            store = mergeStores(store, saved)
            saveLocal(store)
            status = "synced"
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
    }

    fun discardLive() {
        live = null
        saveLive(null)
        PlatformEffects.liveChanged(null, 0)
    }

    /** Edit an existing entry in place; a newer `updated` wins the merge. */
    suspend fun updateEntry(entry: Entry, type: String, amountCents: Long, cat: String, note: String, date: String) =
        mutex.withLock {
            if (blocked()) return@withLock
            val edited = entry.copy(
                type = type, amount = amountCents, cat = cat, note = note, date = date, updated = nowIso(),
            )
            store = store.copy(
                entries = store.entries.map { if (it.id == entry.id) edited else it },
                updatedAt = nowIso(),
            )
            saveLocal(store)
            pushWithTombs(emptyList())
        }

    /** Edit a shift; the cover rule re-runs because the date or start may have moved. */
    suspend fun updateShift(
        shift: Shift, date: String, start: String, hours: Double, km: Double,
        grossCents: Long, extraCents: Long, note: String, kmPerLitre: Double?,
        odoStart: Double? = null, odoEnd: Double? = null,
    ) = mutex.withLock {
        if (blocked()) return@withLock
        val edited = shift.copy(
            date = date, start = start, hours = hours, km = km, gross = grossCents, extra = extraCents,
            note = note, kmPerLitre = kmPerLitre, odoStart = odoStart, odoEnd = odoEnd, updated = nowIso(),
        )
        store = store.copy(
            shifts = store.shifts.map { if (it.id == shift.id) edited else it },
            updatedAt = nowIso(),
        )
        store = recomputeCover(store).first
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    /** Vehicle costs and target rate — the numbers every shift calculation uses. */
    suspend fun updateProfit(profit: Profit) = mutex.withLock {
        if (blocked()) return@withLock
        store = store.copy(
            settings = store.settings.copy(profit = profit),
            updatedAt = nowIso(),
        )
        store = recomputeCover(store).first
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    /** Removing a shift also removes its linked insurance entry and re-runs the cover rule. */
    suspend fun deleteShift(id: String) = mutex.withLock {
        if (blocked()) return@withLock
        val feeId = "fee-$id"
        val next = store.copy(
            shifts = store.shifts.filter { it.id != id },
            entries = store.entries.filter { it.id != feeId },
            // tombstone both ids so no other client can bring them back
            tomb = Tomb(ids = (store.tomb.ids + id + feeId).distinct(), cats = store.tomb.cats),
            updatedAt = nowIso(),
        )
        store = recomputeCover(next).first
        saveLocal(store)
        pushWithTombs(listOf(id, feeId))
    }

    suspend fun deleteEntry(id: String) = mutex.withLock {
        if (blocked()) return@withLock
        val next = store.copy(
            entries = store.entries.filter { it.id != id },
            tomb = Tomb(ids = (store.tomb.ids + id).distinct(), cats = store.tomb.cats),
            updatedAt = nowIso(),
        )
        store = next
        saveLocal(store)
        pushWithTombs(listOf(id))
    }

    private suspend fun pushWithTombs(ids: List<String>) {
        try {
            val saved = client.push(store, deletedIds = ids)
            store = mergeStores(store, saved)
            saveLocal(store)
            status = "synced"
            PlatformEffects.successFeedback()
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
    }

    suspend fun addCategory(name: String) = mutex.withLock {
        if (blocked()) return@withLock
        if (name.isBlank()) return@withLock
        val id = name.lowercase().replace(" ", "-").filter { it.isLetterOrDigit() || it == '-' }
        if (id.isBlank()) return@withLock
        if (store.settings.cats.catsOut.any { it.id == id }) return@withLock
        val cat = Cat(id = id, name = name.trim(), icon = "🏷️")
        store = store.copy(settings = store.settings.copy(cats = store.settings.cats.copy(catsOut = store.settings.cats.catsOut + cat)))
        saveLocal(store)
        runCatching { client.push(store) }.onSuccess { store = mergeStores(store, it); saveLocal(store) }
    }

    suspend fun removeCategory(id: String) = mutex.withLock {
        if (blocked()) return@withLock
        // refuse while entries still reference it, so history never loses its label
        if (store.entries.any { it.cat == id }) return@withLock
        val cats = store.settings.cats
        store = store.copy(settings = store.settings.copy(cats = cats.copy(catsOut = cats.catsOut.filter { it.id != id })))
        saveLocal(store)
        runCatching { client.push(store) }.onSuccess { store = mergeStores(store, it); saveLocal(store) }
    }

    /** Merge a backup file into what is already here (never destructive). */
    suspend fun importJson(text: String): Int = mutex.withLock {
        if (blocked()) return@withLock 0
        val incoming = runCatching { BakiJson.decodeFromString(Store.serializer(), text) }.getOrElse {
            lastError = "That file isn't a Baki backup"
            return@withLock 0
        }
        val before = store.entries.size + store.shifts.size
        store = mergeStores(store, incoming)
        saveLocal(store)
        pushWithTombs(emptyList())
        (store.entries.size + store.shifts.size) - before
    }

    /** Forget the local copy and pull again from the server. */
    suspend fun resetLocal() = mutex.withLock {
        store = Store()
        saveLocal(store)
        sync()
    }

    fun setGlassMode(mode: String) {
        config = config.copy(glassMode = mode)
        saveConfig(config)
        PlatformEffects.tapFeedback()
    }

    fun setThemeMode(mode: String) {
        config = config.copy(themeMode = mode)
        saveConfig(config)
        PlatformEffects.tapFeedback()
    }

    fun setMatchWallpaper(on: Boolean) {
        config = config.copy(matchWallpaper = on)
        saveConfig(config)
        PlatformEffects.tapFeedback()
    }

    fun setServer(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        config = config.copy(serverUrl = clean)
        saveConfig(config)
        client = TrackerClient(clean)
        scope.launch { runCatching { sync() } }
    }
}
