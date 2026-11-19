package app.baki

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BillPaymentUiTest {
    @Test
    fun markPaidUpdatesTheBillRow() = runSkikoComposeUiTest(
        size = Size(720f, 1600f), density = Density(2f),
    ) {
        PhonePreview.active = true
        val previous = runCatching { loadLocal() }.getOrNull()
        val bill = Bill("ui-bill", "UI bill", 1_000, "monthly", "other")
        saveLocal(Store(bills = listOf(bill)), markDirty = false)
        try {
            val repo = BakiRepo(ClientConfig(serverUrl = "http://127.0.0.1:1", allowWrites = true, themeMode = "light"))
            setContent { BakiTheme("light") { App(repo) } }
            waitForIdle()

            onNodeWithContentDescription("More").performClick()
            waitForIdle()
            onNodeWithTag("bill-mark-paid-ui-bill").performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { repo.store.billPayments.isNotEmpty() }
            waitForIdle()
            onNodeWithText("1 of 1 covered", substring = true).assertExists()
        } finally {
            previous?.let { saveLocal(it, markDirty = false) }
            PhonePreview.active = false
        }
    }
}
