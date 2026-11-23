package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

/** G5c path: the repository can run on normalized SQLite behind the storage seam. */
class SqliteRepositoryTest {
    @Test
    fun repositoryWritesReadsAndExportsSqlite() = runBlocking {
        val previous = loadLocal()
        val tag = Random.nextLong().toString().takeLast(10)
        val dbName = "g5c-test-$tag.db"
        val dbFile = File(System.getProperty("user.home") ?: ".", ".local/share/baki/$dbName")
        dbFile.delete()
        val storage = NormalizedSqliteStorage(dbName, "g5c-test-key")
        try {
            saveLocal(Store(), markDirty = false)
            val repo = BakiRepo(
                ClientConfig(serverUrl = "http://127.0.0.1:1", allowWrites = true, storageEngine = "sqlite"),
                customStorage = storage,
            )
            val id = repo.addEntry("expense", 432, "food", "g5c", "2026-11-23")!!
            assertTrue(repo.store.entries.any { it.id == id })
            assertEquals(1, repo.store.entries.size)
            val exported = BakiJson.decodeFromString<Store>(storage.exportJson()!!)
            assertEquals(repo.store.entries.size, exported.entries.size)
            assertEquals(id, exported.entries.single().id)
            assertTrue(storage.exportJson()!!.contains(id))
        } finally {
            storage.close()
            saveLocal(previous, markDirty = false)
            LocalStore.deleteBytes("operations.log")
            LocalStore.deleteBytes("operations.meta")
            dbFile.delete()
            StoreWrite.operationLog = null
        }
    }
}
