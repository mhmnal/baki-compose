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
import kotlin.math.abs
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToLong
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
    private var client = TrackerClient(initial.serverUrl, initial.serverToken)

    var config by mutableStateOf(initial)
        private set
    var store by mutableStateOf(loadLocal())
        private set
    var live by mutableStateOf(loadLive())
        private set
    var status by mutableStateOf("local")
        private set
    var lastError by mutableStateOf<String?>(null)
    /** when the last successful pull finished */
    var lastSyncAt by mutableStateOf<Long?>(null)
    /** when the last successful upload finished */
    var lastPushAt by mutableStateOf<Long?>(null)
        private set
    /** records that differed on both sides during the last pull; newest edit wins. */
    var lastMergeConflicts by mutableStateOf(0)
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

    /** Tolls are a pass-through (Grab reimburses instantly), but the category must exist to log them. */
    fun ensureTollCategory() {
        val cats = store.settings.cats
        if (cats.catsOut.any { it.id == "toll" }) return
        store = store.copy(
            settings = store.settings.copy(
                cats = cats.copy(catsOut = cats.catsOut + Cat("toll", "Toll", "\uD83D\uDEE3")),
            ),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        scope.launch { runCatching { pushWithTombs(emptyList()) } }
    }

    fun start() {
        ensureTollCategory()
        // enforce retention and drop paths whose shift no longer exists, even if no shift is ended.
        // The id of a shift in progress is NOT in store.shifts yet: sweeping without it deletes the
        // path being recorded right now, which is what made a drive appear to restart after a stop.
        scope.launch {
            runCatching {
                RouteStore.prune()
                RouteStore.pruneOrphans(routeKeysInUse())
            }
        }
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
                runCatching { sync(showProgress = false) }
                delay(if (status == "offline") 5_000 else 20_000)
            }
        }
        scope.launch {
            while (true) {
                runCatching {
                    client.events().collect { sync(showProgress = false) }
                }
                delay(3_000)          // server restarts / network blips: retry the stream
            }
        }
    }

    suspend fun sync(showProgress: Boolean = true) = mutex.withLock {
        if (showProgress) status = "syncing"
        try {
            val remote = client.pull()
            if (remote == null) {
                // 304: the server has nothing new, but our outbox may still hold local edits.
                // Keep the last conflict count; a 304 is not a new pull to clear it with.
                if (config.allowWrites && pendingPush) pushAll()
                runCatching { pushRoutes() }
                runCatching { pullRoutes(addDays(todayIso(), -45)) }
                status = "synced"
                lastError = null
                lastSyncAt = nowMillis()
                return@withLock
            }
            lastMergeConflicts = mergeReport(store, remote).total
            val merged = mergeStores(store, remote, keepLocalSettings = pendingPush)
            // no change means no disk write: serialising + encrypting the store is not free
            // compare the whole store: a change that only touched bills, payments, statements or
            // settings used to be merged and then discarded because the old test looked at four fields
            val changed = merged != store
            if (changed) {
                store = merged
                // A server merge is not a local edit; marking it dirty creates a push/SSE loop.
                saveLocal(store, markDirty = false)
            }
            // upload only when we hold something the server did not get last time
            if (config.allowWrites && pendingPush) pushAll()
            runCatching { pushRoutes() }
            runCatching { pullRoutes(addDays(todayIso(), -45)) }
            status = "synced"
            lastError = null
            lastSyncAt = nowMillis()
        } catch (e: Exception) {
            status = if (store.entries.isEmpty() && store.shifts.isEmpty()) "offline" else "cached"
            lastError = e.message
        }
    }

    /** Statements are keyed by week, so re-importing a corrected statement replaces it. */
    suspend fun saveStatement(statement: Statement) = mutex.withLock {
        if (blocked()) return@withLock
        store = store.copy(
            statements = store.statements + (statement.weekStart to statement),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        runCatching { pushWithTombs(emptyList()) }
    }

    /** Adds a bill, or updates one by id. Amounts are cents; an empty id means "new". */
    suspend fun saveBill(bill: Bill): Boolean = mutex.withLock {
        if (config.allowWrites.not()) return false
        val id = bill.id.ifBlank { newId("bill") }
        val clean = bill.copy(
            id = id,
            name = bill.name.trim().take(60),
            amount = bill.amount.coerceAtLeast(0),
            category = bill.category.ifBlank { "other" },
            anchorMonth = bill.anchorMonth?.takeIf { it in 1..12 },
            updated = nowIso(),
            active = true,
        )
        if (clean.name.isBlank()) return false
        store = store.copy(
            bills = store.bills.filterNot { it.id == id } + clean,
            updatedAt = nowIso(),
        )
        saveLocal(store)
        runCatching { pushWithTombs(emptyList()) }
        true
    }

    /** Soft-deletes: the server keeps the record (with the flag) so the removal syncs. */
    suspend fun deleteBill(id: String): Boolean = mutex.withLock {
        if (config.allowWrites.not()) return false
        if (store.billPayments.any { it.billId == id }) return false   // keep its history
        val existing = store.bills.firstOrNull { it.id == id } ?: return false
        store = store.copy(
            bills = store.bills.map { if (it.id == id) existing.copy(active = false, updated = nowIso()) else it },
            updatedAt = nowIso(),
        )
        saveLocal(store)
        runCatching { pushWithTombs(emptyList()) }
        true
    }

    /**
     * Removes the link between a bill period and its expense. The money stays in the ledger; only the
     * "covered" claim goes away, and the change syncs because the row is flagged inactive.
     */
    suspend fun unlinkBillPayment(billId: String, period: String): Boolean = mutex.withLock {
        if (config.allowWrites.not()) return false
        val link = store.billPayments.firstOrNull { it.active && it.billId == billId && it.period == period }
            ?: return false
        store = store.copy(
            billPayments = store.billPayments.map {
                if (it.billId == billId && it.period == period) it.copy(active = false, updated = nowIso()) else it
            },
            updatedAt = nowIso(),
        )
        saveLocal(store)
        runCatching { pushWithTombs(emptyList()) }
        true
    }

    /** Pays one bill for a period: an expense entry dated today plus the link that marks it covered. */
    suspend fun payBill(billId: String, period: String, date: String = todayIso(), note: String = ""): Boolean =
        mutex.withLock { payBillLocked(billId, period, date, note) }

    /** Undo a bill payment. Auto-created expenses are removed; manually linked ones stay. */
    suspend fun undoBillPayment(billId: String, period: String, removeEntry: Boolean): Boolean = mutex.withLock {
        if (config.allowWrites.not()) return@withLock false
        val link = store.billPayments.firstOrNull { it.active && it.billId == billId && it.period == period }
            ?: return@withLock false
        val entryId = link.entryId
        val nextEntries = if (removeEntry) store.entries.filterNot { it.id == entryId } else store.entries
        val nextTomb = if (removeEntry) store.tomb.copy(ids = (store.tomb.ids + entryId).distinct()) else store.tomb
        store = store.copy(
            entries = nextEntries,
            billPayments = store.billPayments.map {
                if (it.billId == billId && it.period == period) it.copy(active = false, updated = nowIso()) else it
            },
            tomb = nextTomb,
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(if (removeEntry) listOf(entryId) else emptyList())
        true
    }

    private fun payBillLocked(billId: String, period: String, date: String, note: String): Boolean {
        if (config.allowWrites.not()) return false
        val bill = store.bills.firstOrNull { it.id == billId } ?: return false
        if (store.billPayments.any { it.billId == billId && it.period == period }) return false
        val now = nowIso()
        val entry = Entry(
            id = newId("e"), date = date, type = "expense", cat = bill.category,
            amount = bill.amount, note = note.ifBlank { "${bill.name} · $period" },
            updated = now, loggedAt = now,
        )
        store = store.copy(
            entries = store.entries + entry,
            billPayments = store.billPayments + BillPayment(billId, period, bill.amount, entry.id, date, now),
            updatedAt = now,
        )
        saveLocal(store)
        return true
    }

    /**
     * Marks a period covered using an expense that already exists (the rent you logged by hand, the
     * phone bill you entered as "Phone"). No new money is recorded.
     */
    suspend fun linkBillPayment(billId: String, period: String, entryId: String): Boolean = mutex.withLock {
        if (config.allowWrites.not()) return false
        val bill = store.bills.firstOrNull { it.id == billId } ?: return false
        if (store.billPayments.any { it.billId == billId && it.period == period }) return false
        val entry = store.entries.firstOrNull { it.id == entryId && it.type != "income" } ?: return false
        val now = nowIso()
        store = store.copy(
            billPayments = store.billPayments + BillPayment(billId, period, entry.amount, entry.id, entry.date, now),
            updatedAt = now,
        )
        saveLocal(store)
        runCatching { pushWithTombs(emptyList()) }
        true
    }

    /** Settles every committed bill still open for [monthLabel]. Returns how many were paid. */
    suspend fun settleBills(monthLabel: String, date: String = todayIso()): Int = mutex.withLock {
        if (config.allowWrites.not()) return 0
        val open = Bills.unpaid(store, monthLabel)
        var n = 0
        open.forEach { bill ->
            if (payBillLocked(bill.id, Bills.periodOf(bill, monthLabel), date, "")) n++
        }
        if (n > 0) runCatching { pushWithTombs(emptyList()) }
        n
    }

    suspend fun addExpense(amountCents: Long, cat: String, note: String, date: String, odo: Int? = null) =
        addEntry("expense", amountCents, cat, note, date, odo)

    /** Returns the new entry's id so a quick-add can offer undo. */
    suspend fun addEntry(
        type: String, amountCents: Long, cat: String, note: String, date: String, odo: Int? = null,
    ): String? = mutex.withLock {
        if (blocked()) return@withLock null
        if (!isValidIsoDate(date)) return@withLock null
        // Grab insurance is derived from the shift timeline. A manual duplicate would double the fee.
        if (isDerivedCategory(cat)) return@withLock null
        val stamp = nowIso()
        val entry = Entry(
            id = newId(), date = date, type = type, cat = cat, amount = amountCents,
            note = note, updated = stamp, loggedAt = stamp, odo = odo,
        )
        store = store.copy(entries = store.entries + entry, updatedAt = stamp)
        saveLocal(store)                       // never lose a tap, even offline
        try {
            pushWithAllTombs()
            saveLocal(store, markDirty = false)
            pendingPush = false
            status = "synced"
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
        entry.id
    }

    /**
     * Commits several entries parsed from one sentence, in a single store write and one sync.
     * Returns the new ids so the caller can offer one undo for the whole sentence.
     */
    suspend fun addMany(entries: List<QuickEntry>, date: String): List<String> = mutex.withLock {
        if (blocked() || entries.isEmpty()) return@withLock emptyList()
        if (!isValidIsoDate(date)) return@withLock emptyList()
        val stamp = nowIso()
        val made = entries.map { q ->
            Entry(
                id = newId(), date = date, type = "expense", cat = q.cat, amount = q.cents,
                note = "", updated = stamp, loggedAt = stamp,
            )
        }
        store = store.copy(entries = store.entries + made, updatedAt = stamp)
        saveLocal(store)
        try {
            pushWithAllTombs()
            saveLocal(store, markDirty = false)
            pendingPush = false
            status = "synced"
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
        made.map { it.id }
    }

    /** Every route key that must survive a sweep: stored shifts plus the one being recorded. */
    private fun routeKeysInUse(): Set<String> =
        store.shifts.mapTo(mutableSetOf()) { it.id } + listOfNotNull(live?.id)

    /** Log a past shift. The cover fee is derived from the 24h rule, never typed. */
    suspend fun addShift(
        date: String, start: String, hours: Double, km: Double,
        grossCents: Long, extraCents: Long = 0, note: String = "",
        odoStart: Double? = null, odoEnd: Double? = null,
        reimbursedTolls: Boolean = false,
    ) = mutex.withLock {
        if (blocked()) return@withLock
        if (!isValidIsoDate(date) || !isValidClock(start)) return@withLock
        val charge = draftCoverCharge(store, date, start)
        val shift = Shift(
            id = newId("s"), date = date, start = start, hours = hours, km = km,
            gross = grossCents, extra = extraCents, reimbursedTolls = reimbursedTolls,
            grabFee = charge, note = note, odoStart = odoStart, odoEnd = odoEnd, updated = nowIso(),
        )
        // recompute over the whole timeline so earlier shifts stay correct
        val withShift = store.copy(shifts = store.shifts + shift, updatedAt = nowIso())
        store = recomputeCover(withShift).first
        saveLocal(store)
        try {
            pushWithAllTombs()
            saveLocal(store, markDirty = false)
            pendingPush = false
            status = "synced"
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
    }

    fun startLive() {
        if (live != null) return
        live = LiveShift(id = newId("r"), start = nowMillis())
        saveLive(live)
        PlatformEffects.successFeedback()
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    private var lastCoverCheck = 0L

    /**
     * Insurance renews while a shift is running, at the moment the paid window ends — not at the next
     * shift's start. The fee that has not been written yet (the shift only becomes an entry when it
     * ends) is shown immediately as a live expense, so the money never lags behind the driving.
     */
    fun ensureLiveCover(now: Long = nowMillis()) {
        val current = live ?: return
        if (now - lastCoverCheck < 10_000) return
        lastCoverCheck = now
        val fee = (store.settings.profit.grabInsurance * 100).roundToLong()
        if (fee <= 0) return
        val renewed = liveRenewedWindows(store, current.start, now)
        val id = "fee-live-${current.id}"
        val existing = store.entries.firstOrNull { it.id == id }
        val want = renewed * fee
        if (want <= 0) {
            if (existing != null) {
                store = store.copy(entries = store.entries.filterNot { it.id == id }, updatedAt = nowIso())
                saveLocal(store)
            }
            return
        }
        if (existing?.amount == want) return
        val stamp = nowIso()
        val entry = Entry(
            id = id, date = startDate(current.start), type = "expense", cat = "grabfee",
            amount = want, note = "Grab insurance (renewed)", updated = stamp, loggedAt = stamp,
        )
        store = store.copy(
            entries = store.entries.filterNot { it.id == id } + entry,
            updatedAt = nowIso(),
        )
        saveLocal(store)
    }

    /** Drop the provisional live fee; the finished shift's fee entry replaces it. */
    private fun clearLiveCover(id: String) {
        val entryId = "fee-live-$id"
        if (store.entries.none { it.id == entryId }) return
        store = store.copy(entries = store.entries.filterNot { it.id == entryId }, updatedAt = nowIso())
    }

    fun pauseLive() {
        val current = live ?: return
        if (isPaused(current)) return
        live = current.copy(pauses = current.pauses + LivePause(from = nowMillis()))
        saveLive(live)
        PlatformEffects.tapFeedback()
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    fun resumeLive() {
        val current = live ?: return
        if (!isPaused(current)) return
        val now = nowMillis()
        live = current.copy(
            pauses = current.pauses.mapIndexed { i, p ->
                if (i == current.pauses.lastIndex && p.to == null) p.copy(to = now) else p
            },
        )
        saveLive(live)
        PlatformEffects.tapFeedback()
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    /** Correct a payout that was entered wrong, before the shift ends. */
    suspend fun updatePayout(index: Int, cents: Long) = mutex.withLock {
        val current = live ?: return@withLock
        if (index !in current.payouts.indices || cents <= 0) return@withLock
        val list = current.payouts.toMutableList()
        list[index] = list[index].copy(c = cents)
        live = current.copy(payouts = list)
        saveLive(live)
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    /** Remove a payout; returns it so the caller can offer undo. */
    suspend fun removePayout(index: Int): Payout? = mutex.withLock {
        val current = live ?: return@withLock null
        if (index !in current.payouts.indices) return@withLock null
        val list = current.payouts.toMutableList()
        val removed = list.removeAt(index)
        live = current.copy(payouts = list)
        saveLive(live)
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
        removed
    }

    /** Put a removed payout back where it was (undo). */
    suspend fun insertPayout(index: Int, payout: Payout) = mutex.withLock {
        val current = live ?: return@withLock
        val list = current.payouts.toMutableList()
        list.add(index.coerceIn(0, list.size), payout)
        live = current.copy(payouts = list)
        saveLive(live)
        live?.let { PlatformEffects.liveChanged(it.start, liveTotalCents(it)) }
    }

    // ---- payouts on a shift that has already ended -------------------------
    // The breakdown is synced, so a correction here reaches the other device too.
    // Gross is the recorded/expected earning. Payouts are cash received; editing them must not
    // rewrite the recorded amount or the outstanding balance would always collapse to zero.

    suspend fun updateShiftPayout(shiftId: String, index: Int, cents: Long) = mutex.withLock {
        if (blocked()) return@withLock
        val shift = store.shifts.firstOrNull { it.id == shiftId } ?: return@withLock
        if (index !in shift.payouts.indices || cents <= 0) return@withLock
        val list = shift.payouts.toMutableList()
        list[index] = list[index].copy(c = cents)
        storeShiftPayouts(shift, list)
    }

    suspend fun removeShiftPayout(shiftId: String, index: Int): Payout? = mutex.withLock {
        if (blocked()) return@withLock null
        val shift = store.shifts.firstOrNull { it.id == shiftId } ?: return@withLock null
        if (index !in shift.payouts.indices) return@withLock null
        val list = shift.payouts.toMutableList()
        val removed = list.removeAt(index)
        storeShiftPayouts(shift, list)
        removed
    }

    suspend fun insertShiftPayout(shiftId: String, index: Int, payout: Payout) = mutex.withLock {
        if (blocked()) return@withLock
        val shift = store.shifts.firstOrNull { it.id == shiftId } ?: return@withLock
        val list = shift.payouts.toMutableList()
        list.add(index.coerceIn(0, list.size), payout)
        storeShiftPayouts(shift, list)
    }

    suspend fun addShiftPayout(shiftId: String, cents: Long) = mutex.withLock {
        if (blocked() || cents <= 0) return@withLock
        val shift = store.shifts.firstOrNull { it.id == shiftId } ?: return@withLock
        // a payout discovered after the fact is stamped with the shift's own start, not "now",
        // so the week review is not told you were idle at 3am
        val at = shiftStartMs(shift)
        storeShiftPayouts(shift, shift.payouts + Payout(c = cents, at = at))
    }

    private suspend fun storeShiftPayouts(shift: Shift, payouts: List<Payout>) {
        val edited = shift.copy(payouts = payouts, updated = nowIso())
        store = recomputeCover(
            store.copy(shifts = store.shifts.map { if (it.id == shift.id) edited else it }, updatedAt = nowIso()),
        ).first
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    fun addPayout(cents: Long) {
        // a payout while paused is fine; it does not resume the break
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
        // breaks do not count as working time, but they do extend the wall-clock cover window
        val hours = max(0.05, liveWorkedMs(current, now) / 3_600_000.0)
        val breakMinutes = ((pausedMs(current, now) + 30_000L) / 60_000L).toInt().coerceIn(0, 24 * 60)
        // real distance when the tracker saw enough movement, else the old hours × average guess.
        // The correction makes up for chords cutting corners on a curving route.
        val measuredKm = current.km * store.settings.gpsCorrection

        live = null
        saveLive(null)
        clearLiveCover(current.id)
        PlatformEffects.liveChanged(null, 0)
        PlatformEffects.successFeedback()
        if (total <= 0) return@withLock       // nothing earned: nothing to write

        val charge = draftCoverCharge(store, date, start)
        // The path is filed under the live shift key while driving; on completion it moves onto the
        // id of the shift that ends up in the store. Nothing about routes is ever synced — the
        // server whitelists shift fields, so a synced key would simply be stripped.
        val shiftId = newId("s")
        val routeKey = current.id.ifBlank { shiftId }
        // keep the raw track: if this shift's km is later corrected to the car's number, the pair
        // teaches the drift factor (see Calibration), so no odometer is needed every shift
        runCatching { Calibration.rememberRaw(shiftId, current.km) }
        val pending = drainRouteBuffer()
        if (pending.isNotEmpty()) RouteStore.append(routeKey, date, pending)
        val shift = Shift(
            id = shiftId, date = date, start = start, hours = hours, breakMinutes = breakMinutes,
            km = if (measuredKm >= 0.3) measuredKm else 0.0,
            gross = total, grabFee = charge, updated = nowIso(),
            payouts = current.payouts,
        )
        store = recomputeCover(store.copy(shifts = store.shifts + shift, updatedAt = nowIso())).first
        saveLocal(store)
        // move the path onto the shift id, simplify it, then enforce retention
        runCatching {
            RouteStore.rename(routeKey, shiftId)
            RouteStore.finalize(shiftId)
            RouteStore.pruneOrphans(routeKeysInUse())
        }
        try {
            pushWithAllTombs()
            saveLocal(store, markDirty = false)
            pendingPush = false
            status = "synced"
            // route is finalized above; try immediately rather than waiting for the next tick
            runCatching { pushRoutes() }
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
        }
    }

    fun discardLive() {
        val id = live?.id
        live = null
        saveLive(null)
        if (id != null) {
            clearLiveCover(id)
            saveLocal(store)
        }
        PlatformEffects.liveChanged(null, 0)
    }

    /** Edit an existing entry in place; a newer `updated` wins the merge. */
    suspend fun updateEntry(
        entry: Entry, type: String, amountCents: Long, cat: String, note: String, date: String,
        odo: Int? = null,
    ) =
        mutex.withLock {
            if (blocked()) return@withLock
            if (!isValidIsoDate(date)) return@withLock
            val edited = entry.copy(
                type = type, amount = amountCents, cat = cat, note = note, date = date,
                odo = odo ?: entry.odo, updated = nowIso(),
            )
            store = store.copy(
                entries = store.entries.map { if (it.id == entry.id) edited else it },
                updatedAt = nowIso(),
            )
            saveLocal(store)
            pushWithTombs(emptyList())
        }

    /** Persist a compressed receipt JPEG (base64) on an entry; blank means remove it. */
    suspend fun setEntryReceipt(id: String, receipt: String): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        if (receipt.length > 180_000) return@withLock false
        val entry = store.entries.firstOrNull { it.id == id } ?: return@withLock false
        if (entry.receipt == receipt) return@withLock true
        val stamp = nowIso()
        store = store.copy(
            entries = store.entries.map {
                if (it.id == id) it.copy(receipt = receipt, updated = stamp) else it
            },
            updatedAt = stamp,
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    /** Write the note shown on a day page. */
    suspend fun setDayNote(date: String, text: String): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        val clean = text.trim().take(500)
        val stamp = nowIso()
        store = store.copy(
            dayNotes = store.dayNotes + (date to DayNote(clean, stamp)),
            updatedAt = stamp,
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    /** Mark a day page reviewed, or reopen it. Edits remain allowed either way. */
    suspend fun setDayClosed(date: String, closed: Boolean): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        val stamp = nowIso()
        store = store.copy(
            dayCloses = store.dayCloses + (date to DayClose(closed, stamp)),
            updatedAt = stamp,
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    /** Mark a statement week reconciled, or reopen it. */
    suspend fun setStatementReconciled(weekStart: String, reconciled: Boolean): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        if (weekStart !in store.statements) return@withLock false
        val stamp = nowIso()
        store = store.copy(
            statementMarks = store.statementMarks + (weekStart to StatementMark(reconciled, stamp)),
            updatedAt = stamp,
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    /** Attach an odometer to an already-saved entry without changing its amount or date. */
    suspend fun setEntryOdometer(id: String, odo: Int): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        val entry = store.entries.firstOrNull { it.id == id } ?: return@withLock false
        if (entry.odo == odo) return@withLock true
        val stamp = nowIso()
        store = store.copy(
            entries = store.entries.map {
                if (it.id == id) it.copy(odo = odo, updated = stamp) else it
            },
            updatedAt = stamp,
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    /** Edit a shift; the cover rule re-runs because the date or start may have moved. */
    suspend fun updateShift(
        shift: Shift, date: String, start: String, hours: Double, km: Double,
        grossCents: Long, extraCents: Long, note: String, kmPerLitre: Double?,
        odoStart: Double? = null, odoEnd: Double? = null, reimbursedTolls: Boolean = false,
    ) = mutex.withLock {
        if (blocked()) return@withLock
        if (!isValidIsoDate(date) || !isValidClock(start)) return@withLock
        // the driver replaced the tracked km with the car's own figure: learn from it
        var learned: Double? = null
        if (km > 0.0 && shift.km > 0.0 && abs(km - shift.km) / shift.km > 0.02) {
            learned = runCatching { Calibration.learn(shift.id, km) }.getOrNull()
        }
        val edited = shift.copy(
            date = date, start = start, hours = hours, km = km, gross = grossCents, extra = extraCents,
            reimbursedTolls = reimbursedTolls, note = note, kmPerLitre = kmPerLitre,
            odoStart = odoStart, odoEnd = odoEnd, updated = nowIso(),
        )
        store = store.copy(
            shifts = store.shifts.map { if (it.id == shift.id) edited else it },
            settings = learned?.let { factor ->
                store.settings.copy(gpsCorrection = factor)
            } ?: store.settings,
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
        PlatformEffects.haptic(Haptic.Warn)
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
        // a deleted shift must not leave its movement history behind
        runCatching { RouteStore.delete(id) }
        pushWithTombs(listOf(id, feeId))
    }

    suspend fun deleteEntry(id: String) = mutex.withLock {
        if (blocked()) return@withLock
        PlatformEffects.haptic(Haptic.Warn)
        val next = store.copy(
            entries = store.entries.filter { it.id != id },
            tomb = Tomb(ids = (store.tomb.ids + id).distinct(), cats = store.tomb.cats),
            updatedAt = nowIso(),
        )
        store = next
        saveLocal(store)
        pushWithTombs(listOf(id))
    }

    /**
     * Durable outbox bit. Set after every local store save, cleared only after the server accepted a
     * push. It lives in the device config so an edit made offline still uploads after a restart.
     */
    var pendingPush: Boolean
        get() = config.pendingPush
        private set(value) {
            if (config.pendingPush == value) return
            config = config.copy(pendingPush = value)
            saveConfig(config)
        }

    init {
        StoreWrite.onLocalSave = { markDirty() }
        StoreHealth.loadError?.let { lastError = it }
    }

    private fun markDirty() {
        if (!config.pendingPush) pendingPush = true
    }

    /**
     * The only place that talks to the push endpoint. It always sends the union of the explicit
     * tombstones and everything already in [Store.tomb], so no successful write can clear the outbox
     * while an older deletion is still unknown to the server.
     */
    private suspend fun pushWithAllTombs(extraIds: List<String> = emptyList()): Store {
        // A fee entry can be legitimately re-derived after it was tombstoned (the cover rule changed,
        // or a renewal now applies). Tombstones are authoritative on the server, so a re-created id
        // must be undeleted or the ledger silently loses that fee.
        val neededFees = store.entries.filter { it.cat == "grabfee" }.map { it.id }
        val resurrect = neededFees.filter { it in store.tomb.ids }
        val deleted = (extraIds + store.tomb.ids).distinct()
        val saved = client.push(store, deletedIds = deleted, undeleteIds = resurrect)
        store = mergeStores(store, saved, keepLocalSettings = true)
        if (resurrect.isNotEmpty()) {
            store = store.copy(tomb = store.tomb.copy(ids = store.tomb.ids - resurrect.toSet()))
        }
        return saved
    }

    /** Full-store push used by the retry loop. */
    private suspend fun pushAll() {
        pushWithAllTombs()
        saveLocal(store, markDirty = false)
        pendingPush = false
        status = "synced"
        lastError = null
        lastPushAt = nowMillis()
    }

    private suspend fun pushWithTombs(ids: List<String>) {
        try {
            pushWithAllTombs(ids)
            saveLocal(store, markDirty = false)
            status = "synced"
            pendingPush = false
            lastPushAt = nowMillis()
            PlatformEffects.successFeedback()
        } catch (e: Exception) {
            status = "cached"
            lastError = e.message
            pendingPush = true
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
        runCatching { pushWithAllTombs() }.onSuccess {
            saveLocal(store, markDirty = false)
            pendingPush = false
        }
    }

    /** Renames a category in place; the id never changes, so history keeps pointing at it. */
    suspend fun renameCategory(id: String, name: String) = mutex.withLock {
        if (blocked()) return@withLock
        val clean = name.trim().take(24)
        if (clean.isBlank()) return@withLock
        val cats = store.settings.cats
        val out = cats.catsOut.map { if (it.id == id) it.copy(name = clean) else it }
        val inn = cats.catsIn.map { if (it.id == id) it.copy(name = clean) else it }
        if (out == cats.catsOut && inn == cats.catsIn) return@withLock
        store = store.copy(
            settings = store.settings.copy(cats = Cats(inn, out)),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        runCatching { pushWithTombs(emptyList()) }
    }

    suspend fun removeCategory(id: String) = mutex.withLock {
        if (blocked()) return@withLock
        // refuse while entries still reference it, so history never loses its label
        if (store.entries.any { it.cat == id }) return@withLock
        val cats = store.settings.cats
        store = store.copy(settings = store.settings.copy(cats = cats.copy(catsOut = cats.catsOut.filter { it.id != id })))
        saveLocal(store)
        runCatching { pushWithAllTombs() }.onSuccess {
            saveLocal(store, markDirty = false)
            pendingPush = false
        }
    }

    /** Remember that a portable backup was created, so Settings can report backup health. */
    fun markBackupExported(json: String) {
        config = config.copy(
            lastBackupAt = nowMillis(),
            lastBackupEntries = store.entries.size,
            lastBackupShifts = store.shifts.size,
            lastBackupBytes = json.encodeToByteArray().size.toLong(),
        )
        saveConfig(config)
    }

    /** Merge a backup file into what is already here (never destructive). */
    suspend fun importJson(text: String): Int = mutex.withLock {
        // Restoring a local backup is deliberately allowed in read-only mode. It mutates this
        // device only; the upload below is still gated by allowWrites.
        val bundle = runCatching {
            BakiJson.decodeFromString(BakiBackupBundle.serializer(), text)
        }.getOrNull()
        val incoming = bundle?.store ?: runCatching {
            BakiJson.decodeFromString(Store.serializer(), text)
        }.getOrElse {
            lastError = "That file isn't a Baki backup"
            return@withLock 0
        }
        val before = store.entries.size + store.shifts.size
        store = mergeStores(store, incoming)
        saveLocal(store)
        val added = (store.entries.size + store.shifts.size) - before
        bundle?.config?.let { saved ->
            // A restore must never silently turn writes on. The user re-enables explicitly.
            config = saved.copy(
                allowWrites = false,
                pendingPush = true,
                lastRestoreAt = nowMillis(),
                lastRestoreAdded = added,
            )
            saveConfig(config)
            client = TrackerClient(config.serverUrl, config.serverToken)
        }
        bundle?.calibration?.let { runCatching { Calibration.restore(it) } }
        bundle?.routes?.forEach { runCatching { RouteStore.put(it) } }
        if (bundle?.config == null) {
            config = config.copy(lastRestoreAt = nowMillis(), lastRestoreAdded = added)
            saveConfig(config)
        }
        if (config.allowWrites) pushWithTombs(emptyList())
        added
    }

    /** Forget the local copy and pull again from the server. */
    suspend fun resetLocal() = mutex.withLock {
        store = Store()
        saveLocal(store)
        sync()
    }

    /** Learn the GPS/odometer ratio from a shift where both are known. */
    suspend fun setGpsCorrection(factor: Double) = mutex.withLock {
        if (blocked()) return@withLock
        if (factor <= 0.5 || factor >= 2.0) return@withLock   // refuse nonsense
        store = store.copy(
            settings = store.settings.copy(gpsCorrection = factor),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    fun setServiceBaseline(km: Int?, date: String?) {
        config = config.copy(serviceOdoBase = km?.takeIf { it > 0 }, serviceOdoBaseOn = date)
        saveConfig(config)
        PlatformEffects.tapFeedback()
        ActionFeedback.show(if (km != null) "Service baseline set" else "Service baseline cleared")
    }

    /** Set or clear (limit <= 0) a monthly cap for one category. */
    suspend fun setBudget(cat: String, limit: Double) = mutex.withLock {
        if (blocked()) return@withLock
        val next = store.settings.budgets.toMutableMap()
        if (limit <= 0.0) next.remove(cat) else next[cat] = limit
        store = store.copy(
            settings = store.settings.copy(budgets = next),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    suspend fun setBudgets(all: Map<String, Double>) = mutex.withLock {
        if (blocked()) return@withLock
        store = store.copy(
            settings = store.settings.copy(budgets = all.filterValues { it > 0.0 }),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        ActionFeedback.show("Budgets saved")
    }

    suspend fun setEnvelopeCarry(on: Boolean) = mutex.withLock {
        if (blocked()) return@withLock
        store = store.copy(
            settings = store.settings.copy(envelopeCarry = on),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithAllTombs()
    }

    /** Move envelope money within one month without changing the base allocation. */
    suspend fun moveEnvelope(month: String, from: String, to: String, amount: Double): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        if (from == to || amount <= 0.0) return@withLock false
        val current = store.envelopeMoves[month].orEmpty()
        val updated = current + (from to (current[from] ?: 0.0) - amount) + (to to (current[to] ?: 0.0) + amount)
        store = store.copy(
            envelopeMoves = store.envelopeMoves + (month to updated),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    suspend fun setReserveTarget(fund: String, target: Double) = mutex.withLock {
        if (blocked()) return@withLock
        if (fund !in setOf("fuel", "wear")) return@withLock
        store = store.copy(
            settings = store.settings.copy(
                reserveTargets = if (target > 0.0) store.settings.reserveTargets + (fund to target)
                else store.settings.reserveTargets - fund,
            ),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    /** Positive amount adds to the virtual reserve; negative withdraws from it. */
    suspend fun addReserveTxn(fund: String, amountCents: Long, note: String = "", date: String = todayIso()): Boolean = mutex.withLock {
        if (blocked()) return@withLock false
        if (fund !in setOf("fuel", "wear") || amountCents == 0L) return@withLock false
        val stamp = nowIso()
        val tx = ReserveTxn(newId("res"), fund, amountCents, date, note.trim().take(120), stamp)
        store = store.copy(reserveTxns = store.reserveTxns + tx, updatedAt = stamp)
        saveLocal(store)
        pushWithTombs(emptyList())
        true
    }

    /**
     * Upload sealed traces for recent shifts. Opt-in and silent: a failure here must never disturb a
     * ledger sync, so the caller wraps it.
     */
    suspend fun pushRoutes(days: Int = 45) {
        val secret = config.routeSecret
        if (!config.syncRoutes || secret.isBlank()) return
        val cutoff = addDays(todayIso(), -days)
        val items = mutableListOf<RouteBlob>()
        store.shifts.filter { it.date >= cutoff }.forEach { shift ->
            val route = RouteStore.route(shift.id) ?: return@forEach
            if (route.points.size < 2) return@forEach
            val plain = BakiJson.encodeToString(ShiftRoute.serializer(), route).encodeToByteArray()
            val sealed = RouteCrypto.seal(secret, plain)
            items += RouteBlob(shift.id, shift.date, RouteBase64.encode(sealed))
        }
        if (items.isNotEmpty()) {
            client.pushRoutes(items)
            lastPushAt = nowMillis()
        }
    }

    /** Pull and open traces since [since]; returns how many were stored. */
    suspend fun pullRoutes(since: String): Int {
        val secret = config.routeSecret
        if (secret.isBlank()) return 0
        var stored = 0
        client.pullRoutes(since).forEach { item ->
            runCatching {
                val plain = RouteCrypto.open(secret, RouteBase64.decode(item.blob))
                val route = BakiJson.decodeFromString(ShiftRoute.serializer(), plain.decodeToString())
                RouteStore.put(route)
                stored++
            }
        }
        return stored
    }

    fun setRouteSync(enabled: Boolean, secret: String) {
        config = config.copy(syncRoutes = enabled, routeSecret = secret.trim())
        saveConfig(config)
        if (enabled && secret.isNotBlank()) scope.launch { runCatching { pushRoutes() } }
    }

    /** A device-wide daily food guardrail; null clears it. */
    suspend fun setDailyFoodCap(cents: Long?) = mutex.withLock {
        if (blocked()) return@withLock
        store = store.copy(
            settings = store.settings.copy(dailyFoodCapCents = cents?.takeIf { it > 0 }),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
        ActionFeedback.show("Food cap saved")
    }

    /** The service target is user-set; null clears it. */
    suspend fun setServiceTarget(km: Int?, date: String?) = mutex.withLock {
        if (blocked()) return@withLock
        store = store.copy(
            settings = store.settings.copy(nextServiceKm = km, nextServiceOn = date),
            updatedAt = nowIso(),
        )
        saveLocal(store)
        pushWithTombs(emptyList())
    }

    fun setDiscreet(on: Boolean) {
        config = config.copy(discreet = on)
        saveConfig(config)
        PlatformEffects.tapFeedback()
    }

    fun setThemeMode(mode: String) {
        config = config.copy(themeMode = mode)
        saveConfig(config)
        PlatformEffects.tapFeedback()
        ActionFeedback.show("Theme saved")
    }

    fun setHighContrast(on: Boolean) {
        config = config.copy(highContrast = on)
        saveConfig(config)
        ActionFeedback.show(if (on) "High contrast on" else "Standard contrast")
        PlatformEffects.tapFeedback()
    }

    fun setColorSource(source: String) {
        val clean = if (source == "baki") "baki" else "wallpaper"
        config = config.copy(colorSource = clean)
        saveConfig(config)
        PlatformEffects.tapFeedback()
        ActionFeedback.show("Colour source saved")
    }

    fun setReducedMotion(on: Boolean) {
        config = config.copy(reducedMotion = on)
        saveConfig(config)
        PlatformEffects.tapFeedback()
        ActionFeedback.show(if (on) "Reduced motion on" else "Reduced motion off")
    }

    fun setServer(url: String, token: String = config.serverToken) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        config = config.copy(serverUrl = clean, serverToken = token.trim())
        saveConfig(config)
        client = TrackerClient(clean, config.serverToken)
        scope.launch { runCatching { sync() } }
    }
}
