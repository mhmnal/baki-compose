package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pulls from a live tracker server. The test is skipped (with a printed note) when no
 * server is listening, so it never fails a build on a machine without one.
 */
class SyncTest {

    private val base = System.getProperty("baki.server") ?: "http://127.0.0.1:8802"

    @Test
    fun pullsTheLiveStore() = runBlocking {
        val client = TrackerClient(base)
        val remote = try {
            client.pull()
        } catch (e: Exception) {
            println("[sync] no server at $base — skipping (${e.message})")
            return@runBlocking
        }
        println("[sync] pulled ${remote.entries.size} entries, ${remote.shifts.size} shifts")
        assertTrue(remote.entries.isNotEmpty(), "server returned an empty store")
        assertEquals(remote.entries.size, remote.entries.distinctBy { it.id }.size, "no duplicate ids")

        // merging the pulled store into an empty local one must be a no-op
        val merged = mergeStores(Store(), remote)
        assertEquals(remote.entries.size, merged.entries.size)
        assertEquals(remote.shifts.size, merged.shifts.size)
    }

    @Test
    fun roundTripSurvivesJson() {
        val store = loadLocal()
        val text = BakiJson.encodeToString(Store.serializer(), store)
        val back = BakiJson.decodeFromString<Store>(text)
        assertEquals(store.entries.size, back.entries.size)
        assertEquals(store.settings.profit.kmPerLitre, back.settings.profit.kmPerLitre)
    }
}
