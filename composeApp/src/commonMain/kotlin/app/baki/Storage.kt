package app.baki

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
}

/** Non-fatal storage problems, surfaced in diagnostics rather than swallowed. */
object StoreHealth {
    var loadError: String? = null
    var saveError: String? = null
}

/** Lets the repository mark the durable outbox bit after any local store save. */
object StoreWrite {
    var onLocalSave: (() -> Unit)? = null
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

fun saveLocal(store: Store, markDirty: Boolean = true) {
    runCatching {
        LocalStore.write(BakiJson.encodeToString(Store.serializer(), store))
    }.onSuccess {
        StoreHealth.saveError = null
        if (markDirty) StoreWrite.onLocalSave?.invoke()   // durable outbox bit
        PlatformEffects.dataChanged()                     // keep the home-screen widget in step
    }.onFailure {
        // The data still exists in memory: mark the outbox so sync can push it even if disk failed.
        StoreHealth.saveError = "Local save failed: ${it.message}"
        if (markDirty) StoreWrite.onLocalSave?.invoke()
    }
}
