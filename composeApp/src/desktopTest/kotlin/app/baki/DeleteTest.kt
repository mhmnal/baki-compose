package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deleting must remove the record everywhere, including its linked insurance entry. */
class DeleteTest {

    private val base = System.getProperty("baki.deleteServer") ?: "http://127.0.0.1:8813"

    @Test
    fun deletingAShiftRemovesItAndItsFeeEntry() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pull()!! } catch (e: Exception) {
            println("[delete] no server at $base — skipping (${e.message})")
            return@runBlocking
        }

        val repo = BakiRepo(ClientConfig(serverUrl = base, allowWrites = true))
        repo.sync()

        repo.addShift("2026-12-02", "09:00", hours = 2.0, km = 20.0, grossCents = 5000)
        val created = repo.store.shifts.first { it.date == "2026-12-02" }
        val feeId = "fee-${created.id}"
        assertTrue(repo.store.entries.any { it.id == feeId } || created.grabFee == 0L, "cover handled")

        repo.deleteShift(created.id)

        val after = client.pull()!!
        assertTrue(after.shifts.none { it.id == created.id }, "shift gone from the server")
        assertTrue(after.entries.none { it.id == feeId }, "linked fee entry gone too")
        assertTrue(after.tomb.ids.contains(created.id), "the deletion is remembered as a tombstone")
        assertEquals(before.shifts.size, after.shifts.size, "server back to its original shifts")

        // and it must not come back through a stale merge
        val stale = mergeStores(before.copy(shifts = before.shifts + created), after)
        assertTrue(stale.shifts.none { it.id == created.id }, "a stale copy cannot resurrect it")
    }

    @Test
    fun deletingAnEntryIsTombstoned() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pull()!! } catch (e: Exception) { return@runBlocking }

        val repo = BakiRepo(ClientConfig(serverUrl = base, allowWrites = true))
        repo.sync()
        repo.addExpense(420, "food", "delete-me", "2026-12-03")
        val e = repo.store.entries.first { it.note == "delete-me" }

        repo.deleteEntry(e.id)
        assertTrue(repo.pendingPush, "the tombstone is queued locally")
        repo.sync()

        val after = client.pull()!!
        assertTrue(after.entries.none { it.id == e.id }, "entry gone from the server")
        assertTrue(after.tomb.ids.contains(e.id), "tombstoned")
        assertEquals(before.entries.size, after.entries.size)
    }

    @Test
    fun writesAreBlockedWhenReadOnly() = runBlocking {
        val repo = BakiRepo(ClientConfig(serverUrl = base, allowWrites = false))
        val startCount = repo.store.entries.size
        repo.addExpense(999, "food", "should-not-exist", "2026-12-04")
        assertEquals(startCount, repo.store.entries.size, "read-only mode refused the write")
        assertTrue(repo.lastError?.contains("Read-only") == true, "and said why")
    }
}
