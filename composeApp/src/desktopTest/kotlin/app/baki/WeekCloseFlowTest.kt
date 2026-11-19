package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Week Close must round-trip through the real server and survive a fresh client sync. */
class WeekCloseFlowTest {
    private val base = System.getProperty("baki.billServer") ?: "http://127.0.0.1:8813"

    @Test
    fun weekCloseSyncsAndCanReopen() = runBlocking {
        val client = TrackerClient(base)
        try { client.pull() } catch (e: Exception) {
            println("[week-close-flow] no test server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val repo = BakiRepo(ClientConfig(serverUrl = base, allowWrites = true))
        repo.sync()
        val week = "2026-12-07"
        assertTrue(repo.closeWeek(week, 45_600))
        val remote = client.pull()!!.weekCloses[week]
        assertEquals(45_600, remote?.receivedCents)
        assertTrue(remote?.closedAt?.isNotBlank() == true)

        val second = BakiRepo(ClientConfig(serverUrl = base, allowWrites = true))
        second.sync()
        assertTrue(second.store.weekCloses[week]?.closedAt?.isNotBlank() == true)

        assertTrue(second.reopenWeek(week))
        assertFalse(client.pull()!!.weekCloses[week]?.closedAt?.isNotBlank() == true)
    }
}
