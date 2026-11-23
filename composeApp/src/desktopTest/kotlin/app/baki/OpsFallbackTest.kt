package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A server without /api/ops must fall back to the whole-store push. */
class OpsFallbackTest {
    private val base = System.getProperty("baki.fallbackServer") ?: "http://127.0.0.1:8815"

    @Test
    fun oldServerFallsBackToWholeStorePush() = runBlocking {
        val client = TrackerClient(base)
        try { client.pull()!! } catch (e: Exception) {
            println("[fallback] no server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val previous = loadLocal()
        LocalStore.deleteBytes("operations.log")
        LocalStore.deleteBytes("operations.meta")
        try {
            val repo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = base, allowWrites = true))
            repo.sync()
            val id = repo.addEntry("expense", 654, "food", "fallback-test", "2026-11-23")!!
            assertTrue(repo.outboxDepth() > 0)
            repo.sync()
            assertEquals(0, repo.outboxDepth(), "fallback push must acknowledge the outbox")
            assertTrue(client.pull()!!.entries.any { it.id == id })
        } finally {
            saveLocal(previous, markDirty = false)
            LocalStore.deleteBytes("operations.log")
            LocalStore.deleteBytes("operations.meta")
        }
    }
}
