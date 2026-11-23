package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Two clients editing the same entry through /api/ops. */
class OpsConflictTest {
    private val base = System.getProperty("baki.testServer") ?: "http://127.0.0.1:8810"

    private fun entry(updated: String, amount: Long) = Entry(
        id = "conflict-entry", date = "2026-11-23", type = "expense", cat = "food",
        amount = amount, note = "conflict", updated = updated,
    )

    private fun op(id: String, entry: Entry) = LedgerOperation(
        id = id, seq = 1, entity = "entry", entityId = entry.id, kind = "upsert",
        payload = BakiJson.encodeToString(Entry.serializer(), entry), updated = entry.updated,
    )

    @Test
    fun newestEditWinsAndTombstonesStayWinning() = runBlocking {
        val a = TrackerClient(base)
        val b = TrackerClient(base)
        val before = try { a.pull()!! } catch (e: Exception) {
            println("[conflict] no server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val older = entry("2026-11-23T20:00:00", 100)
        val newer = entry("2026-11-23T20:01:00", 200)
        a.pushOps(listOf(op("conflict-a1", older)))
        b.pushOps(listOf(op("conflict-b1", newer)))
        assertEquals(200L, a.pull()!!.entries.first { it.id == older.id }.amount)

        // A stale edit arrives with a new operation id: it must not overwrite the newer record.
        a.pushOps(listOf(op("conflict-a2", older)))
        assertEquals(200L, a.pull()!!.entries.first { it.id == older.id }.amount)

        // A deletion wins against a stale resurrection attempt.
        b.pushOps(listOf(LedgerOperation("conflict-del", 2, "entry", older.id, "delete", updated = "2026-11-23T20:02:00")))
        a.pushOps(listOf(op("conflict-a3", older)))
        val after = a.pull()!!
        assertTrue(after.entries.none { it.id == older.id })
        assertTrue(after.tomb.ids.contains(older.id))
        a.push(before, deletedIds = listOf(older.id))
    }
}
