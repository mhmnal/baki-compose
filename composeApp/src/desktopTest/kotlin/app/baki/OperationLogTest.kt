package app.baki

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationLogTest {
    private fun log(): Pair<OperationLog, String> {
        val tag = Random.nextLong().toString().takeLast(10)
        val file = "ops-test-$tag.log"
        val meta = "ops-test-$tag.meta"
        return OperationLog(file, meta) to file
    }

    @Test
    fun operationsAreEncryptedOrderedAndAcknowledged() {
        val (log, file) = log()
        val first = log.append("entry", "e1", "upsert", """{"id":"e1"}""", "2026-11-01")
        val second = log.append("entry", "e2", "delete", updated = "2026-11-02")
        assertTrue(first != null && second != null && second!! > first!!)
        assertEquals(2, log.pendingCount())
        val raw = LocalStore.readBytes(file)!!.decodeToString()
        assertFalse(raw.contains("e1"), "operation payloads are sealed at rest")
        assertFalse(raw.contains("entry"), "operation metadata is sealed at rest")

        log.markSnapshot(first!!)
        assertEquals(2, log.pendingCount(), "snapshot coverage is not server acknowledgement")
        assertEquals(listOf(second), log.pendingAfterSnapshot().map { it.seq })
        log.acknowledgeAll()
        assertEquals(0, log.pendingCount())
        val third = log.append("entry", "e3", "upsert", """{"id":"e3"}""", "2026-11-03")
        assertTrue(third!! > second!!)
        LocalStore.deleteBytes(file)
    }

    @Test
    fun replayIsIdempotentAndDeletesWin() {
        val old = Entry(id = "old", date = "2026-11-01", type = "expense", cat = "food", amount = 100, updated = "a")
        val base = Store(entries = listOf(old))
        val add = LedgerOperation("op1", 1, "entry", "new", "upsert", BakiJson.encodeToString(Entry.serializer(), Entry(id = "new", date = "2026-11-02", amount = 200, updated = "b")), "b")
        val del = LedgerOperation("op2", 2, "entry", "old", "delete", "", "c")
        val once = applyEntryOperations(base, listOf(add, del))
        assertTrue(once.entries.any { it.id == "new" })
        assertTrue(once.entries.none { it.id == "old" })
        assertTrue(once.tomb.ids.contains("old"))
        val twice = applyEntryOperations(once, listOf(add, del))
        assertEquals(once.entries, twice.entries)
        assertEquals(once.tomb, twice.tomb)
    }

    @Test
    fun crashBetweenOperationAndSnapshotIsReplayedExactlyOnce() {
        val previous = loadLocal()
        val tag = Random.nextLong().toString().takeLast(10)
        val file = "ops-test-$tag.log"
        val meta = "ops-test-$tag.meta"
        val log = OperationLog(file, meta)
        try {
            val base = Store(entries = listOf(Entry(id = "base", date = "2026-11-01", amount = 100, updated = "a")))
            saveLocal(base, markDirty = false)
            val entry = Entry(id = "crashed", date = "2026-11-02", amount = 250, note = "replay me", updated = "b")
            log.append("entry", entry.id, "upsert", BakiJson.encodeToString(Entry.serializer(), entry), entry.updated)
            // Simulate the snapshot sequence not advancing before the process died.
            val recovered = loadLocalWithOperations(log)
            assertTrue(recovered.entries.any { it.id == "crashed" })
            assertEquals(1, recovered.entries.count { it.id == "crashed" })
            assertEquals(1, log.pendingCount(), "replay leaves the operation in the server outbox")
        } finally {
            LocalStore.deleteBytes(file)
            LocalStore.deleteBytes(meta)
            saveLocal(previous, markDirty = false)
        }
    }

    @Test
    fun genericStoreOperationCoversNonEntryMutations() {
        val base = Store()
        val next = Store(
            bills = listOf(Bill(id = "b1", name = "Phone", amount = 5000, category = "phone")),
            settings = base.settings.copy(nextServiceKm = 50_000),
        )
        val payload = BakiJson.encodeToString(Store.serializer(), next)
        val op = LedgerOperation("op-store", 1, "store", "store", "replace", payload, "b")
        assertEquals(next, applyOperations(base, listOf(op)))
    }

    @Test
    fun tenThousandOperationsRemainReadable() {
        val (log, file) = log()
        try {
            repeat(10_000) { i ->
                assertTrue(log.append("entry", "bulk-$i", "delete") != null)
            }
            assertEquals(10_000, log.readAll().size)
            assertEquals(10_000, log.pendingCount())
        } finally {
            LocalStore.deleteBytes(file)
        }
    }

    @Test
    fun tornTailDoesNotBlockTheNextOperation() {
        val (log, file) = log()
        log.append("entry", "before", "delete")
        LocalStore.appendBytes(file, byteArrayOf(0, 0, 0, 99, 1, 2, 3))
        log.repairTornTail() // startup repair after a torn append
        val seq = log.append("entry", "after", "upsert", """{"id":"after"}""")
        assertTrue(seq != null)
        val ids = log.readAll().map { it.entityId }
        assertEquals(listOf("before", "after"), ids)
        LocalStore.deleteBytes(file)
    }
}
