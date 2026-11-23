package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatementPairTest {
    private fun repo() = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:1", allowWrites = true))

    @Test
    fun suggestedPairRequiresConfirmation() = runBlocking {
        val repo = repo()
        repo.stageStatementText(
            """
            AEON Bank (M) Berhad
            01 Oct 26   TESTPAIR OUT - Fund Transfer
                        -RM 10.00                    RM 90.00
            """.trimIndent(),
        )
        repo.stageStatementText(
            """
            TNG WALLET TRANSACTION HISTORY
            01/10/2026 Success DUITNOW_RECEIVEFROM 20261001101 TESTPAIR IN RM10.00 RM100.00
            """.trimIndent(),
        )
        val rows = repo.store.importedTxns.filter { it.description.contains("TESTPAIR") }
        assertEquals(2, rows.size)
        val suggestion = rows.first().suggestedPairId
        assertTrue(suggestion.isNotBlank(), "pair is suggested, not confirmed")
        assertTrue(rows.all { it.pairId.isBlank() }, "no auto-confirm")

        assertTrue(repo.confirmTransferPair(suggestion))
        val confirmed = repo.store.importedTxns.filter { it.description.contains("TESTPAIR") }
        assertTrue(confirmed.all { it.pairId == suggestion })
        assertTrue(confirmed.all { it.suggestedPairId.isBlank() })
    }

    @Test
    fun rejectedPairStaysRejected() = runBlocking {
        val repo = repo()
        repo.stageStatementText(
            """
            AEON Bank (M) Berhad
            02 Oct 26   TESTREJECT OUT - Fund Transfer
                        -RM 11.00                    RM 79.00
            """.trimIndent(),
        )
        repo.stageStatementText(
            """
            TNG WALLET TRANSACTION HISTORY
            02/10/2026 Success DUITNOW_RECEIVEFROM 20261002101 TESTREJECT IN RM11.00 RM111.00
            """.trimIndent(),
        )
        val rows = repo.store.importedTxns.filter { it.description.contains("TESTREJECT") }
        val suggestion = rows.first().suggestedPairId
        assertTrue(suggestion.isNotBlank())
        assertTrue(repo.rejectTransferPair(suggestion))
        val rejected = repo.store.importedTxns.filter { it.description.contains("TESTREJECT") }
        assertTrue(rejected.all { it.suggestedPairId.isBlank() && it.pairRejected })
        assertTrue(rejected.all { it.pairId.isBlank() })
    }
}
