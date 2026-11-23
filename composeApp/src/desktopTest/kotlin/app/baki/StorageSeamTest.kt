package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageSeamTest {
    private class MemoryStorage(var value: Store = Store()) : LedgerStorage {
        override fun loadWithOperations(log: OperationLog): Store = value
        override fun save(store: Store, markDirty: Boolean, recordOutbox: Boolean) { value = store }
        override fun path(): String = "memory"
        override fun exportJson(): String? = BakiJson.encodeToString(Store.serializer(), value)
    }

    @Test
    fun repositoryWritesOnlyThroughStorage() = runBlocking {
        LocalStore.deleteBytes("operations.log")
        LocalStore.deleteBytes("operations.meta")
        try {
            val memory = MemoryStorage()
            val repo = BakiRepo(
                ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:1", allowWrites = true),
                customStorage = memory,
            )
            val id = repo.addEntry("expense", 111, "food", "seam", "2026-11-23")!!
            assertEquals("memory", repo.storagePath)
            assertTrue(memory.value.entries.any { it.id == id })
            assertEquals(1, memory.value.entries.size)
        } finally {
            LocalStore.deleteBytes("operations.log")
            LocalStore.deleteBytes("operations.meta")
            StoreWrite.operationLog = null
        }
    }
}
