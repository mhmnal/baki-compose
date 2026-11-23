package app.baki

/**
 * G5a: the SQLite file replaces the JSON file as the durable snapshot container.
 * The Store is still serialised as JSON inside one transactional row; entity tables can be
 * normalised later without changing LedgerStorage.
 */
class SqliteLedgerStorage(
    private val dbName: String = "baki.db",
    private val fixedPassphrase: String? = null,
) : LedgerStorage {

    private fun passphrase(): String = fixedPassphrase ?: sqlitePassphrase()

    private fun open(): EncryptedSpikeDb = openEncryptedDb(dbName, passphrase())

    private fun read(db: app.baki.db.BakiDatabase): Store? =
        db.storeSnapshotQueries.selectSnapshot().executeAsOneOrNull()?.let {
            runCatching { BakiJson.decodeFromString<Store>(it) }.getOrElse { err ->
                StoreHealth.loadError = "SQLite snapshot could not be parsed: ${err.message}"
                null
            }
        }

    private fun write(db: app.baki.db.BakiDatabase, store: Store) {
        val json = BakiJson.encodeToString(Store.serializer(), store)
        db.transaction {
            db.storeSnapshotQueries.replaceSnapshot(json, store.updatedAt)
        }
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
                // First G5 launch: import the existing JSON snapshot exactly once, verify the round trip.
                base = loadLocal()
                write(handle.db, base)
                val readBack = read(handle.db) ?: error("SQLite snapshot missing after import")
                if (readBack != base) error("SQLite import verification failed")
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
}
