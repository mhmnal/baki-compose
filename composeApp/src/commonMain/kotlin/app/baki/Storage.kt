package app.baki


/** Storage seam: the repository never needs to know whether the snapshot is JSON or SQLite. */
interface LedgerStorage {
    fun loadWithOperations(log: OperationLog): Store
    fun save(store: Store, markDirty: Boolean = true, recordOutbox: Boolean = true)
    fun path(): String
    /** Rollback/export path; null when the implementation cannot read its own snapshot. */
    fun exportJson(): String?
}

object JsonLedgerStorage : LedgerStorage {
    override fun loadWithOperations(log: OperationLog): Store = loadLocalWithOperations(log)
    override fun save(store: Store, markDirty: Boolean, recordOutbox: Boolean) {
        saveLocal(store, markDirty = markDirty, recordOutbox = recordOutbox)
    }
    override fun path(): String = LocalStore.path()
    override fun exportJson(): String? = runCatching {
        LocalStore.read()?.let { BakiJson.decodeFromString<Store>(it) }
    }.getOrNull()?.let { BakiJson.encodeToString(Store.serializer(), it) }
}
/** Where the local copy of the store lives. Mirrors localStorage in the web app. */
expect object LocalStore {
    fun read(): String?
    fun write(text: String)
    fun path(): String

    /** Small side files (client config) that are never pushed to the server. */
    fun readText(name: String): String?
    fun writeText(name: String, text: String)

    /** Move an unreadable file aside so it can be inspected instead of overwritten. */
    fun quarantine(name: String): String?

    /** Raw sidecar bytes; the caller is responsible for sealing if the content is sensitive. */
    fun exists(name: String): Boolean
    fun readBytes(name: String): ByteArray?
    fun writeBytes(name: String, bytes: ByteArray)
    fun appendBytes(name: String, bytes: ByteArray)
    fun deleteBytes(name: String)
}

/** Non-fatal storage problems, surfaced in diagnostics rather than swallowed. */
object StoreHealth {
    var loadError: String? = null
    var saveError: String? = null
    /** Set when SQLite failed and a stale recovery/JSON copy was loaded instead. */
    var recoveryWarning: String? = null
}

/** Lets the repository mark the durable outbox bit after any local store save. */
object StoreWrite {
    var onLocalSave: (() -> Unit)? = null
    /** Set by BakiRepo; saveLocal uses it for the generic non-entry operation record. */
    var operationLog: OperationLog? = null
}

/** Load the local copy, or an empty store on first run. */
private fun loadLocalBackup(): Store? = runCatching {
    LocalStore.readText("store.json.bak")?.let { BakiJson.decodeFromString<Store>(it) }
}.getOrNull()

/** Keep the damaged file, try the previous local write, and let sync recover from the server. */
private fun persistRecovered(store: Store) {
    runCatching { LocalStore.write(BakiJson.encodeToString(Store.serializer(), store)) }
}

private fun recoverLocal(reason: String): Store {
    val kept = runCatching { LocalStore.quarantine("store.json") }.getOrNull()
    val backup = loadLocalBackup()
    StoreHealth.loadError = buildString {
        append(reason)
        if (backup != null) append(" Recovered the previous local backup.")
        else append(" No usable local backup; sync will restore the server copy.")
        if (kept != null) append(" Damaged file kept as ${kept.substringAfterLast('/')}.")
    }
    if (backup != null) persistRecovered(backup)
    return backup ?: Store()
}

fun loadLocal(): Store {
    val raw = try {
        LocalStore.read()
    } catch (e: Exception) {
        // The key was invalidated or the file is damaged. Never pretend this is a first launch.
        return recoverLocal("Local store could not be opened (${e.message ?: "decrypt failed"}).")
    } ?: run {
        // main file gone but a previous local copy exists (e.g. crash after quarantine recovery)
        val backup = loadLocalBackup() ?: return Store()
        StoreHealth.loadError = "Local store was missing; restored the previous local backup."
        persistRecovered(backup)
        return backup
    }
    return runCatching { BakiJson.decodeFromString<Store>(raw) }.getOrElse { e ->
        recoverLocal("Local store could not be parsed (${e.message ?: "invalid JSON"}).")
    }
}

/** Load the snapshot, then replay any entry operations that a crash left outside it. */
fun loadLocalWithOperations(log: OperationLog): Store {
    log.repairTornTail()
    val base = loadLocal()
    val pending = log.pendingAfterSnapshot()
    if (pending.isEmpty()) return base
    val replayed = applyOperations(base, pending)
    if (replayed != base) saveLocal(replayed, recordOutbox = false)
    if (replayed == base || StoreHealth.saveError == null) {
        log.markSnapshot(pending.maxOf { it.seq })
    }
    return replayed
}

fun saveLocal(store: Store, markDirty: Boolean = true, recordOutbox: Boolean = true) {
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
    runCatching {
        LocalStore.write(BakiJson.encodeToString(Store.serializer(), store))
    }.onSuccess {
        StoreHealth.saveError = null
        if (markDirty) StoreWrite.onLocalSave?.invoke()   // durable outbox bit
        if (opSeq != null && log != null && StoreHealth.saveError == null) log.markSnapshot(opSeq)
        PlatformEffects.dataChanged()                     // keep the home-screen widget in step
    }.onFailure {
        // The data still exists in memory: mark the outbox so sync can push it even if disk failed.
        StoreHealth.saveError = "Local save failed: ${it.message}"
        if (markDirty) StoreWrite.onLocalSave?.invoke()
    }
}
