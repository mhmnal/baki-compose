package app.baki

import kotlinx.serialization.Serializable

@Serializable
private data class RecoverySnapshotFile(
    val format: String = "baki-recovery-v1",
    val savedAt: String = "",
    val entries: Int = 0,
    val shifts: Int = 0,
    val store: Store,
)

/**
 * Automatic local recovery copy. It is encrypted by the same StoreCipher key as the rest of the
 * device files, so it can be written without asking for the portable-backup passphrase. It is not
 * a substitute for an off-device portable backup.
 */
object RecoverySnapshot {
    private const val FILE = "recovery-snapshot.json"
    private const val PREV = "recovery-snapshot.prev.json"

    var lastSavedAt: String? = null
        private set
    var lastError: String? = null
        private set

    fun save(store: Store): Boolean = runCatching {
        val previous = LocalStore.readText(FILE)
        val body = BakiJson.encodeToString(
            RecoverySnapshotFile.serializer(),
            RecoverySnapshotFile(
                savedAt = nowIso(),
                entries = store.entries.size,
                shifts = store.shifts.size,
                store = store,
            ),
        )
        LocalStore.writeText(FILE, body)
        val readBack = LocalStore.readText(FILE)?.let {
            BakiJson.decodeFromString<RecoverySnapshotFile>(it)
        } ?: error("recovery read-back failed")
        require(readBack.entries == store.entries.size)
        require(readBack.shifts == store.shifts.size)
        require(readBack.store == store)
        if (previous != null) LocalStore.writeText(PREV, previous)
        lastSavedAt = readBack.savedAt
        lastError = null
        true
    }.getOrElse {
        lastError = it.message
        false
    }

    fun load(): Pair<Store, String>? {
        val raw = runCatching { LocalStore.readText(FILE) }.getOrNull() ?: return null
        val file = runCatching {
            BakiJson.decodeFromString<RecoverySnapshotFile>(raw)
        }.getOrNull() ?: return null
        return file.store to file.savedAt
    }
}
