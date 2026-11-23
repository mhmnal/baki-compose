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
    val accounts: List<Account> = emptyList(),
    val importedTxns: List<ImportedTxn> = emptyList(),
    val statementBatches: List<StatementBatch> = emptyList(),
    val monthCloses: Map<String, String> = emptyMap(),
)

/**
 * G5b: core entities are stored as rows. Payload columns keep schema evolution safe while the
 * indexed columns provide the normal ledger queries. Settings and the statement/auxiliary maps
 * remain a single JSON row until their own normalization pass.
 */
class NormalizedSqliteStorage(
    private val dbName: String = "baki.db",
    private val fixedPassphrase: String? = null,
) : LedgerStorage {
    private fun passphrase(): String = fixedPassphrase ?: sqlitePassphrase()
    private fun open(): EncryptedSpikeDb = openEncryptedDb(dbName, passphrase())

    private fun decodeStoreAux(json: String): StoreAux =
        BakiJson.decodeFromString<StoreAux>(json)

    private fun read(db: BakiDatabase): Store? {
        val q = db.normalizedQueries
        val auxJson = q.selectAuxPayload().executeAsOneOrNull() ?: return null
        val aux = decodeStoreAux(auxJson)
        val settingsJson = q.selectSettingsPayload().executeAsOneOrNull()
            ?: error("settings row missing")
        val entries = q.selectEntryPayloads().executeAsList().map {
            BakiJson.decodeFromString<Entry>(it)
        }
        val shifts = q.selectShiftPayloads().executeAsList().map {
            BakiJson.decodeFromString<Shift>(it)
        }
        val bills = q.selectBillPayloads().executeAsList().map {
            BakiJson.decodeFromString<Bill>(it)
        }
        val payments = q.selectBillPaymentPayloads().executeAsList().map {
            BakiJson.decodeFromString<BillPayment>(it)
        }
        val weeks = q.selectWeekClosePayloads().executeAsList().map {
            BakiJson.decodeFromString<WeekClose>(it)
        }
        val reserves = q.selectReserveTxnPayloads().executeAsList().map {
            BakiJson.decodeFromString<ReserveTxn>(it)
        }
        val odometers = q.selectOdometerPayloads().executeAsList().map {
            BakiJson.decodeFromString<OdometerReading>(it)
        }
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
            accounts = aux.accounts,
            importedTxns = aux.importedTxns,
            statementBatches = aux.statementBatches,
            monthCloses = aux.monthCloses,
        )
    }

    private fun write(db: BakiDatabase, store: Store) {
        val q = db.normalizedQueries
        val aux = StoreAux(
            v = store.v,
            updatedAt = store.updatedAt,
            statements = store.statements,
            tombCats = store.tomb.cats,
            dayNotes = store.dayNotes,
            dayCloses = store.dayCloses,
            statementMarks = store.statementMarks,
            envelopeMoves = store.envelopeMoves,
            accounts = store.accounts,
            importedTxns = store.importedTxns,
            statementBatches = store.statementBatches,
            monthCloses = store.monthCloses,
        )
        db.transaction {
            q.deleteEntries()
            q.deleteShifts()
            q.deleteBills()
            q.deleteBillPayments()
            q.deleteWeekCloses()
            q.deleteReserveTxns()
            q.deleteOdometer()
            q.deleteTombstones()
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
            q.replaceSettings(BakiJson.encodeToString(Settings.serializer(), store.settings), store.updatedAt)
            q.replaceAux(BakiJson.encodeToString(StoreAux.serializer(), aux), store.updatedAt)
        }
    }

    private fun readSnapshot(db: BakiDatabase): Store? =
        db.storeSnapshotQueries.selectSnapshot().executeAsOneOrNull()?.let {
            runCatching { BakiJson.decodeFromString<Store>(it) }.getOrNull()
        }

    override fun loadWithOperations(log: OperationLog): Store {
        log.repairTornTail()
        val handle = try { open() } catch (e: Exception) {
            StoreHealth.loadError = "SQLite could not be opened; using JSON fallback: ${e.message}"
            return loadLocalWithOperations(log)
        }
        try {
            var base = read(handle.db)
            if (base == null) {
                // G5a snapshot first, then the original JSON snapshot.
                base = readSnapshot(handle.db) ?: loadLocal()
                write(handle.db, base)
                val readBack = read(handle.db) ?: error("SQLite normalized snapshot missing after import")
                if (readBack != base) error("SQLite normalized import verification failed")
            }
            val pending = log.pendingAfterSnapshot()
            if (pending.isNotEmpty()) {
                val replayed = applyOperations(base, pending)
                if (replayed != base) write(handle.db, replayed)
                log.markSnapshot(pending.maxOf { it.seq })
                base = replayed
            }
            return base
        } catch (e: Exception) {
            StoreHealth.loadError = "SQLite load failed; using JSON fallback: ${e.message}"
            return loadLocalWithOperations(log)
        } finally {
            handle.close()
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
            val handle = open()
            try { write(handle.db, store) } finally { handle.close() }
            StoreHealth.saveError = null
            if (markDirty) StoreWrite.onLocalSave?.invoke()
            if (opSeq != null && log != null) log.markSnapshot(opSeq)
            PlatformEffects.dataChanged()
        } catch (e: Exception) {
            StoreHealth.saveError = "SQLite save failed: ${e.message}"
            if (markDirty) StoreWrite.onLocalSave?.invoke()
        }
    }

    override fun path(): String = "sqlite:$dbName"

    override fun exportJson(): String? = runCatching {
        val handle = open()
        try {
            read(handle.db)?.let { BakiJson.encodeToString(Store.serializer(), it) }
        } finally { handle.close() }
    }.getOrNull()
}
