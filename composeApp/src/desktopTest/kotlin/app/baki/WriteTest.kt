package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the real write path against a throwaway server: pull, add, verify on the
 * server, then delete the test record again. Skips when no server is listening.
 */
class WriteTest {

    private val base = System.getProperty("baki.testServer") ?: "http://127.0.0.1:8810"

    @Test
    fun addingAnExpenseReachesTheServer() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pull()!! } catch (e: Exception) {
            println("[write] no server at $base — skipping (${e.message})")
            return@runBlocking
        }

        val repo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = base, allowWrites = true))
        repo.sync()
        assertEquals(before.entries.size, repo.store.entries.size, "pull matches the server")

        val marker = "compose-write-test"
        repo.addExpense(350, "food", marker, todayIso())
        assertTrue(repo.pendingPush, "the local write is queued before the network")
        repo.sync()

        val after = client.pull()!!
        val saved = after.entries.firstOrNull { it.note == marker }
        assertTrue(saved != null, "the new expense reached the server")
        assertEquals(350L, saved!!.amount, "amount in cents")
        assertEquals("food", saved.cat, "category")
        assertEquals("expense", saved.type, "type")

        // clean up: tombstone the record so the fixture server is unchanged
        client.push(after.copy(), deletedIds = listOf(saved.id))
        val cleaned = client.pull()!!
        assertTrue(cleaned.entries.none { it.id == saved.id }, "test record removed")
    }
}
