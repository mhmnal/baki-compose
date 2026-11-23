package app.baki

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeskStatementUiTest {
    private suspend fun repo(): BakiRepo {
        val dir = java.io.File(LocalStore.path()).parentFile
        listOf("store.json", "store.json.bak").forEach { java.io.File(dir, it).delete() }
        val suffix = System.nanoTime().toString().takeLast(6)
        val repo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:9", allowWrites = true, themeMode = "light"))
        repo.addEntry("expense", 1000, "food", "UIMATCH$suffix", "2026-10-05")
        repo.stageStatementText("""
            AEON Bank (M) Berhad
            05 Oct 26   UIMATCH$suffix - DuitNowQR
                        -RM 10.00                    RM 90.00
        """.trimIndent())
        repo.stageStatementText("""
            AEON Bank (M) Berhad
            04 Oct 26   UIPAIR$suffix OUT - Fund Transfer
                        -RM 7.77                    RM 82.23
        """.trimIndent())
        repo.stageStatementText("""
            TNG WALLET TRANSACTION HISTORY
            04/10/2026 Success DUITNOW_RECEIVEFROM 20261004101 UIPAIR$suffix IN RM7.77 RM90.00
        """.trimIndent())
        return repo
    }

    @Test
    fun confirmPairMatchAndDeleteBatch() = runSkikoComposeUiTest(
        size = Size(1440f, 900f), density = Density(1f),
    ) {
        val r = kotlinx.coroutines.runBlocking { repo() }
        setContent { BakiTheme("light") { DeskApp(r) } }
        waitForIdle()
        onNodeWithText("Statements").performClick()
        waitForIdle()
        onAllNodesWithTag("confirm-pair")[0].performScrollTo().performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { r.store.importedTxns.any { it.description.contains("UIPAIR") && it.pairId.isNotBlank() } }
        onAllNodesWithText("PAIRED TRANSFER", substring = true)[0].assertExists()
        val entriesBeforeMatch = r.store.entries.size
        onAllNodesWithTag("match-statement")[0].performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { r.store.importedTxns.any { it.description.contains("UIMATCH") && it.status == "matched" } }
        assertTrue(r.store.entries.size == entriesBeforeMatch, "matching must not create a ledger entry")
        onAllNodesWithTag("delete-batch")[0].performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("confirm-delete-batch").performClick()
        waitForIdle()
        onNodeWithTag("confirm-delete-batch").assertDoesNotExist()
    }
}
