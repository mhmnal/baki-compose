package app.baki

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
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
class WeekCloseUiTest {
    /**
     * The Week Close modal uses ModalBottomSheet, whose popup content is not part of the
     * Skiko test semantics tree. Close/reopen is covered by WeekCloseFlowTest; this test
     * locks the card's visible actions and the week picker's presence.
     */
    @Test
    fun weekCloseCardExposesReviewAndPicker() = runSkikoComposeUiTest(
        size = Size(720f, 1600f), density = Density(2f),
    ) {
        PhonePreview.active = true
        val previous = runCatching { loadLocal() }.getOrNull()
        saveLocal(
            Store(
                shifts = listOf(Shift(id = "ui-week-shift", date = todayIso(), start = "09:00", hours = 2.0, km = 10.0, gross = 5_000)),
            ),
            markDirty = false,
        )
        try {
            val repo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:1", allowWrites = true, themeMode = "light"))
            setContent { BakiTheme("light") { App(repo) } }
            waitForIdle()

            onNodeWithContentDescription("Stats").performClick()
            waitForIdle()
            onNodeWithText("WEEK CLOSE").assertExists()
            onNodeWithTag("week-close-review").performScrollTo().assertIsEnabled()
            onNodeWithTag("week-close-picker").assertExists()

            onNodeWithText("30D").performClick()
            waitForIdle()
            onNodeWithText("Running net", substring = true).assertExists()
        } finally {
            previous?.let { saveLocal(it, markDirty = false) }
            PhonePreview.active = false
        }
    }
}
