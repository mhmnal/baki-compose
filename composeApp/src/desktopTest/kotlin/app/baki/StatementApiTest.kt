package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real /api/statements round-trip when a local fixture server is available. */
class StatementApiTest {
    private val base = System.getenv("BAKI_STATEMENT_SERVER")
        ?: System.getProperty("baki.statementServer")
        ?: "http://127.0.0.1:8810"

    @Test
    fun pushPullAndDeleteBatchWithAudit() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pullStatements() } catch (e: Exception) {
            println("[statements] no server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val suffix = abs(System.nanoTime()).toString()
        val account = Account(id = "test-account-$suffix", name = "Test Account $suffix")
        val batch = StatementBatch(
            id = "test-batch-$suffix", accountId = account.id, source = "test",
            importedAt = nowIso(), periodStart = "2026-10-01", periodEnd = "2026-10-31",
            rowCount = 1,
        )
        val row = ImportedTxn(
            id = "test-row-$suffix", accountId = account.id, date = "2026-10-01",
            description = "statement api test", amount = 1234, direction = "out",
            source = "test", batchId = batch.id, updated = nowIso(),
        )
        val pushed = client.pushStatements(StatementBook(before.accounts + account, before.batches + batch, before.txns + row))
        assertTrue(pushed.accounts.any { it.id == account.id })
        assertTrue(pushed.txns.any { it.id == row.id })

        val pulled = client.pullStatements()
        assertEquals(1234L, pulled.txns.first { it.id == row.id }.amount)

        client.pushStatements(pulled, deletedBatchIds = listOf(batch.id))
        val after = client.pullStatements()
        assertTrue(after.txns.none { it.id == row.id }, "deleted batch rows are removed")
        val deleted = after.batches.first { it.id == batch.id }
        assertTrue(deleted.deletedAt.isNotBlank(), "deletion audit retained")
    }
}
