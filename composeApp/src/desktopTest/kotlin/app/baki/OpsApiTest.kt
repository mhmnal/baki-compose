package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpsApiTest {
    private val base = System.getProperty("baki.testServer") ?: "http://127.0.0.1:8810"

    @Test
    fun duplicateOperationDeliveryIsAcceptedOnce() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pull()!! } catch (e: Exception) {
            println("[ops] no server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val entry = Entry(
            id = "ops-api-entry", date = "2026-11-23", type = "expense", cat = "food",
            amount = 321, note = "ops-api", updated = "2026-11-23T20:00:00",
        )
        val op = LedgerOperation(
            id = "ops-api-op", seq = 1, entity = "entry", entityId = entry.id, kind = "upsert",
            payload = BakiJson.encodeToString(Entry.serializer(), entry), updated = entry.updated,
        )
        val first = client.pushOps(listOf(op))
        val second = client.pushOps(listOf(op))
        assertEquals(listOf(op.id), first)
        assertEquals(listOf(op.id), second)
        val after = client.pull()!!
        assertEquals(1, after.entries.count { it.id == entry.id })
        assertEquals(321L, after.entries.first { it.id == entry.id }.amount)
        client.push(after, deletedIds = listOf(entry.id))
        assertEquals(before.entries.size, client.pull()!!.entries.size)
    }

    @Test
    fun repoOutboxSyncsThroughOperationsAndCompacts() = runBlocking {
        val client = TrackerClient(base)
        try { client.pull()!! } catch (e: Exception) { return@runBlocking }
        val previous = loadLocal()
        LocalStore.deleteBytes("operations.log")
        LocalStore.deleteBytes("operations.meta")
        try {
            val repo = BakiRepo(ClientConfig(serverUrl = base, allowWrites = true))
            repo.sync()
            val id = repo.addEntry("expense", 777, "food", "ops-repo", "2026-11-23")!!
            assertTrue(repo.outboxDepth() > 0)
            repo.sync()
            assertEquals(0, repo.outboxDepth(), "acknowledged operations are compacted")
            val after = client.pull()!!
            assertTrue(after.entries.any { it.id == id })
            repo.deleteEntry(id)
            repo.sync()
            assertTrue(client.pull()!!.entries.none { it.id == id })
        } finally {
            saveLocal(previous, markDirty = false)
            LocalStore.deleteBytes("operations.log")
            LocalStore.deleteBytes("operations.meta")
        }
    }
}
