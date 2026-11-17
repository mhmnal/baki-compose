package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Two statement clients editing the same row: the newer updated timestamp wins. */
class StatementConflictTest {
    private val base = System.getenv("BAKI_STATEMENT_SERVER")
        ?: System.getProperty("baki.statementServer")
        ?: "http://127.0.0.1:8810"

    @Test
    fun newestStatementEditWins() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pullStatements() } catch (e: Exception) {
            println("[statements] no server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val suffix = abs(System.nanoTime()).toString()
        val account = Account(id = "test-account-$suffix", name = "Conflict $suffix")
        val batch = StatementBatch(
            id = "test-batch-$suffix", accountId = account.id, source = "test",
            importedAt = nowIso(), periodStart = "2026-10-01", periodEnd = "2026-10-31", rowCount = 1,
        )
        fun row(description: String, updated: String) = ImportedTxn(
            id = "test-row-$suffix", accountId = account.id, date = "2026-10-01",
            description = description, amount = 4321, direction = "out", source = "test",
            batchId = batch.id, updated = updated,
        )
        val baseBook = before.copy(accounts = before.accounts + account, batches = before.batches + batch)
        client.pushStatements(baseBook.copy(txns = before.txns + row("older", "2026-10-01T10:00:00Z")))
        client.pushStatements(baseBook.copy(txns = before.txns + row("newer", "2026-10-01T11:00:00Z")))

        val after = client.pullStatements()
        assertEquals("newer", after.txns.first { it.id == "test-row-$suffix" }.description)

        client.pushStatements(after, deletedBatchIds = listOf(batch.id))
    }
}
