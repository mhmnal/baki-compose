package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxRepoTest {
    @Test
    fun addAndDeleteLeaveDurableEntryOperations() = runBlocking {
        val previous = loadLocal()
        LocalStore.deleteBytes("operations.log")
        LocalStore.deleteBytes("operations.meta")
        try {
            val repo = BakiRepo(ClientConfig(serverUrl = "http://127.0.0.1:1", allowWrites = true))
            val id = repo.addEntry("expense", 123, "food", "outbox-test", todayIso())
            assertTrue(id != null)
            assertEquals(1, repo.outboxDepth())
            assertTrue(repo.deleteEntry(id!!))
            assertEquals(2, repo.outboxDepth(), "delete is a second durable operation")
        } finally {
            saveLocal(previous, markDirty = false)
            LocalStore.deleteBytes("operations.log")
            LocalStore.deleteBytes("operations.meta")
        }
    }
}
