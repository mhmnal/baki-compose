package app.baki

import app.baki.db.BakiDatabase
import kotlinx.serialization.Serializable

@Serializable
private data class StoreAux(
    val v: Int = 1,
    val updatedAt: String = "",
    val statements: Map<String, Statement> = emptyMap(),
    val tombCats: List<String> = emptyList(),
    val dayNotes: Map<String, DayNote> = emptyMap(),
    val dayCloses: Map<String, DayClose> = emptyMap(),
    val statementMarks: Map<String, StatementMark> = emptyMap(),
    val envelopeMoves: Map<String, Map<String, Double>> = emptyMap(),
)

private fun auxOf(store: Store) = StoreAux(
    v = store.v,
    updatedAt = store.updatedAt,
    statements = store.statements,
    tombCats = store.tomb.cats,
    dayNotes = store.dayNotes,
    dayCloses = store.dayCloses,
    statementMarks = store.statementMarks,
    envelopeMoves = store.envelopeMoves,
)

private fun auxJson(store: Store): String =
    BakiJson.encodeToString(StoreAux.serializer(), auxOf(store))

private fun settingsJson(store: Store): String =
    BakiJson.encodeToString(Settings.serializer(), store.settings)

/**
 * G5b: core entities are stored as rows. Payload columns keep schema evolution safe while the
 * indexed columns provide the normal ledger queries. Saves are diff-only and keep one DB handle
 * open for the process lifetime; opening SQLCipher and rewriting every table per tap was the
 * source of the post-cutover latency.
 */
class NormalizedSqliteStorage(
    private val dbName: String = "baki.db",
    private val fixedPassphrase: String? = null,
) : LedgerStorage {
    private var handle: EncryptedSpikeDb? = null
    private var lastWritten: Store? = null

    private fun passphrase(): String = fixedPassphrase ?: sqlitePassphrase()

    private fun db(): BakiDatabase {
        handle?.let { return it.db }
        val opened = openEncryptedDb(dbName, passphrase())
        handle = opened
        return opened.db
    }

    fun close() {
        handle?.close()
        handle = null
        lastWritten = null
    }

    private fun read(db: BakiDatabase): Store? {
        val q = db.normalizedQueries
        val auxJson = q.selectAuxPayload().executeAsOneOrNull() ?: return null
        val aux = BakiJson.decodeFromString<StoreAux>(auxJson)
        val settingsJson = q.selectSettingsPayload().executeAsOneOrNull()
            ?: error("settings row missing")
        val entries = q.selectEntryPayloads().executeAsList().map { BakiJson.decodeFromString<Entry>(it) }
        val shifts = q.selectShiftPayloads().executeAsList().map { BakiJson.decodeFromString<Shift>(it) }
        val bills = q.selectBillPayloads().executeAsList().map { BakiJson.decodeFromString<Bill>(it) }
        val payments = q.selectBillPaymentPayloads().executeAsList().map { BakiJson.decodeFromString<BillPayment>(it) }
        val weeks = q.selectWeekClosePayloads().executeAsList().map { BakiJson.decodeFromString<WeekClose>(it) }
        val reserves = q.selectReserveTxnPayloads().executeAsList().map { BakiJson.decodeFromString<ReserveTxn>(it) }
        val odometers = q.selectOdometerPayloads().executeAsList().map { BakiJson.decodeFromString<OdometerReading>(it) }
        return Store(
            v = aux.v,
            updatedAt = aux.updatedAt,
            entries = entries,
            shifts = shifts,
            statements = aux.statements,
            settings = BakiJson.decodeFromString<Settings>(settingsJson),
            tomb = Tomb(ids = q.selectTombstoneIds().executeAsList(), cats = aux.tombCats),
            bills = bills,
            billPayments = payments,
            dayNotes = aux.dayNotes,
            dayCloses = aux.dayCloses,
            weekCloses = weeks.associateBy { it.weekStart },
            statementMarks = aux.statementMarks,
            envelopeMoves = aux.envelopeMoves,
            reserveTxns = reserves,
            odometerReadings = odometers,
        )
    }

    private fun writeAll(db: BakiDatabase, store: Store) {
        val q = db.normalizedQueries
        db.transaction {
            q.deleteEntries(); q.deleteShifts(); q.deleteBills(); q.deleteBillPayments()
            q.deleteWeekCloses(); q.deleteReserveTxns(); q.deleteOdometer(); q.deleteTombstones()
            store.entries.forEach { e ->
                q.insertEntry(e.id, e.date, e.type, e.cat, e.amount, e.updated, BakiJson.encodeToString(Entry.serializer(), e))
            }
            store.shifts.forEach { s ->
                q.insertShift(s.id, s.date, s.start, s.hours, s.km, s.gross, s.updated, BakiJson.encodeToString(Shift.serializer(), s))
            }
            store.bills.forEach { b ->
                q.insertBill(b.id, if (b.active) 1 else 0, b.updated, BakiJson.encodeToString(Bill.serializer(), b))
            }
            store.billPayments.forEach { p ->
                q.insertBillPayment(p.billId, p.period, if (p.active) 1 else 0, p.updated, BakiJson.encodeToString(BillPayment.serializer(), p))
            }
            store.weekCloses.values.forEach { w ->
                q.insertWeekClose(w.weekStart, w.closedAt, w.receivedCents, w.updated, BakiJson.encodeToString(WeekClose.serializer(), w))
            }
            store.reserveTxns.forEach { r ->
                q.insertReserveTxn(r.id, r.date, r.fund, r.amount, r.updated, BakiJson.encodeToString(ReserveTxn.serializer(), r))
            }
            store.odometerReadings.forEach { o ->
                q.insertOdometer(o.id, o.date, o.km.toDouble(), o.updated, BakiJson.encodeToString(OdometerReading.serializer(), o))
            }
            store.tomb.ids.forEach { q.insertTombstone("id", it) }
            store.tomb.cats.forEach { q.insertTombstone("cat", it) }
            q.replaceSettings(settingsJson(store), store.updatedAt)
            q.replaceAux(auxJson(store), store.updatedAt)
        }
    }

    private fun writeDiff(db: BakiDatabase, old: Store, new: Store) {
        if (old == new) return
        val q = db.normalizedQueries
        db.transaction {
            val oldEntries = old.entries.associateBy { it.id }
            val newEntries = new.entries.associateBy { it.id }
            (oldEntries.keys - newEntries.keys).forEach { q.deleteEntryById(it) }
            newEntries.values.forEach { e ->
                val payload = BakiJson.encodeToString(Entry.serializer(), e)
                if (oldEntries[e.id] == null) q.insertEntry(e.id, e.date, e.type, e.cat, e.amount, e.updated, payload)
                else if (oldEntries[e.id] != e) q.updateEntry(e.date, e.type, e.cat, e.amount, e.updated, payload, e.id)
            }

            val oldShifts = old.shifts.associateBy { it.id }
            val newShifts = new.shifts.associateBy { it.id }
            (oldShifts.keys - newShifts.keys).forEach { q.deleteShiftById(it) }
            newShifts.values.forEach { s ->
                val payload = BakiJson.encodeToString(Shift.serializer(), s)
                if (oldShifts[s.id] == null) q.insertShift(s.id, s.date, s.start, s.hours, s.km, s.gross, s.updated, payload)
                else if (oldShifts[s.id] != s) q.updateShift(s.date, s.start, s.hours, s.km, s.gross, s.updated, payload, s.id)
            }

            val oldBills = old.bills.associateBy { it.id }
            val newBills = new.bills.associateBy { it.id }
            (oldBills.keys - newBills.keys).forEach { q.deleteBillById(it) }
            newBills.values.forEach { b ->
                val payload = BakiJson.encodeToString(Bill.serializer(), b)
                if (oldBills[b.id] == null) q.insertBill(b.id, if (b.active) 1 else 0, b.updated, payload)
                else if (oldBills[b.id] != b) q.updateBill(if (b.active) 1 else 0, b.updated, payload, b.id)
            }

            val oldPayments = old.billPayments.associateBy { it.billId to it.period }
            val newPayments = new.billPayments.associateBy { it.billId to it.period }
            (oldPayments.keys - newPayments.keys).forEach { (bill, period) -> q.deleteBillPaymentByKey(bill, period) }
            newPayments.forEach { (key, p) ->
                val payload = BakiJson.encodeToString(BillPayment.serializer(), p)
                if (oldPayments[key] == null) q.insertBillPayment(p.billId, p.period, if (p.active) 1 else 0, p.updated, payload)
                else if (oldPayments[key] != p) q.updateBillPayment(if (p.active) 1 else 0, p.updated, payload, p.billId, p.period)
            }

            (old.weekCloses.keys - new.weekCloses.keys).forEach { q.deleteWeekCloseById(it) }
            new.weekCloses.forEach { (week, w) ->
                val payload = BakiJson.encodeToString(WeekClose.serializer(), w)
                if (old.weekCloses[week] == null) q.insertWeekClose(w.weekStart, w.closedAt, w.receivedCents, w.updated, payload)
                else if (old.weekCloses[week] != w) q.updateWeekClose(w.closedAt, w.receivedCents, w.updated, payload, w.weekStart)
            }

            val oldReserves = old.reserveTxns.associateBy { it.id }
            val newReserves = new.reserveTxns.associateBy { it.id }
            (oldReserves.keys - newReserves.keys).forEach { q.deleteReserveTxnById(it) }
            newReserves.values.forEach { r ->
                val payload = BakiJson.encodeToString(ReserveTxn.serializer(), r)
                if (oldReserves[r.id] == null) q.insertReserveTxn(r.id, r.date, r.fund, r.amount, r.updated, payload)
                else if (oldReserves[r.id] != r) q.updateReserveTxn(r.date, r.fund, r.amount, r.updated, payload, r.id)
            }

            val oldOdometers = old.odometerReadings.associateBy { it.id }
            val newOdometers = new.odometerReadings.associateBy { it.id }
            (oldOdometers.keys - newOdometers.keys).forEach { q.deleteOdometerById(it) }
            newOdometers.values.forEach { o ->
                val payload = BakiJson.encodeToString(OdometerReading.serializer(), o)
                if (oldOdometers[o.id] == null) q.insertOdometer(o.id, o.date, o.km.toDouble(), o.updated, payload)
                else if (oldOdometers[o.id] != o) q.updateOdometer(o.date, o.km.toDouble(), o.updated, payload, o.id)
            }

            val oldIds = old.tomb.ids.toSet()
            val newIds = new.tomb.ids.toSet()
            (oldIds - newIds).forEach { q.deleteTombstone("id", it) }
            (newIds - oldIds).forEach { q.insertTombstone("id", it) }
            val oldCats = old.tomb.cats.toSet()
            val newCats = new.tomb.cats.toSet()
            (oldCats - newCats).forEach { q.deleteTombstone("cat", it) }
            (newCats - oldCats).forEach { q.insertTombstone("cat", it) }

            if (settingsJson(old) != settingsJson(new)) q.replaceSettings(settingsJson(new), new.updatedAt)
            if (auxJson(old) != auxJson(new)) q.replaceAux(auxJson(new), new.updatedAt)
        }
    }

    private fun readSnapshot(db: BakiDatabase): Store? =
        db.storeSnapshotQueries.selectSnapshot().executeAsOneOrNull()?.let {
            runCatching { BakiJson.decodeFromString<Store>(it) }.getOrNull()
        }

    override fun loadWithOperations(log: OperationLog): Store {
        log.repairTornTail()
        try {
            val database = db()
            StoreHealth.recoveryWarning = null
            var base = read(database)
            if (base == null) {
                base = readSnapshot(database) ?: loadLocal()
                writeAll(database, base)
                val readBack = read(database) ?: error("SQLite normalized snapshot missing after import")
                if (readBack != base) error("SQLite normalized import verification failed")
            }
            lastWritten = base
            val pending = log.pendingAfterSnapshot()
            if (pending.isNotEmpty()) {
                val replayed = applyOperations(base, pending)
                if (replayed != base) writeDiff(database, base, replayed)
                log.markSnapshot(pending.maxOf { it.seq })
                base = replayed
                lastWritten = base
            }
            return base
        } catch (e: Exception) {
            close()
            val recovery = RecoverySnapshot.load()
            StoreHealth.loadError = "SQLite load failed: ${e.message}"
            return if (recovery != null) {
                StoreHealth.recoveryWarning =
                    "SQLite unavailable — using local recovery snapshot from ${recovery.second}. Do not add data until recovery is resolved."
                recovery.first
            } else {
                StoreHealth.recoveryWarning =
                    "SQLite unavailable — using the old JSON fallback. Recent changes may be missing."
                loadLocalWithOperations(log)
            }
        }
    }

    override fun save(store: Store, markDirty: Boolean, recordOutbox: Boolean) {
        val log = StoreWrite.operationLog
        val opSeq = if (markDirty && recordOutbox && log != null) {
            log.append(
                entity = "store",
                entityId = "store",
                kind = "replace",
                payload = BakiJson.encodeToString(Store.serializer(), store),
                updated = store.updatedAt,
            )
        } else null
        try {
            val database = db()
            val previous = lastWritten ?: read(database)
            if (previous == null) writeAll(database, store) else writeDiff(database, previous, store)
            lastWritten = store
            StoreHealth.saveError = null
            if (markDirty) StoreWrite.onLocalSave?.invoke()
            if (opSeq != null && log != null) log.markSnapshot(opSeq)
            PlatformEffects.dataChanged()
        } catch (e: Exception) {
            close()
            StoreHealth.saveError = "SQLite save failed: ${e.message}"
            if (markDirty) StoreWrite.onLocalSave?.invoke()
        }
    }

    override fun path(): String = "sqlite:$dbName"

    override fun exportJson(): String? = runCatching {
        read(db())?.let { BakiJson.encodeToString(Store.serializer(), it) }
    }.getOrNull()
}
