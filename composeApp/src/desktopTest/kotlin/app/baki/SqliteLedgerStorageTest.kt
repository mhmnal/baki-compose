package app.baki

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class SqliteLedgerStorageTest {
    @Test
    fun importsJsonVerifiesItAndStoresNewSnapshots() {
        val previous = loadLocal()
        val tag = Random.nextLong().toString().takeLast(10)
        val dbName = "g5-test-$tag.db"
        val dbFile = File(System.getProperty("user.home") ?: ".", ".local/share/baki/$dbName")
        dbFile.delete()
        val original = Store(
            entries = listOf(Entry(id = "json-entry", date = "2026-11-01", amount = 123, note = "from-json", updated = "a")),
            settings = Store().settings.copy(nextServiceKm = 50_000),
        )
        try {
            saveLocal(original, markDirty = false)
            val storage = SqliteLedgerStorage(dbName, "g5-storage-test-key")
            val imported = storage.loadWithOperations(OperationLog("g5-empty-$tag.log", "g5-empty-$tag.meta"))
            assertEquals(original, imported, "import must be exact")

            val next = original.copy(entries = original.entries + Entry(id = "new-entry", date = "2026-11-02", amount = 456, updated = "b"))
            val log = OperationLog("g5-$tag.log", "g5-$tag.meta")
            StoreWrite.operationLog = log
            storage.save(next, markDirty = true, recordOutbox = true)
            assertEquals(1, log.pendingCount())
            assertEquals(next, storage.loadWithOperations(log))
            log.acknowledgeAll()
            assertEquals(0, log.pendingCount())
        } finally {
            StoreWrite.operationLog = null
            saveLocal(previous, markDirty = false)
            dbFile.delete()
        }
    }
}
