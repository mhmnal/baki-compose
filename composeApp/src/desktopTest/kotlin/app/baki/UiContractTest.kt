package app.baki

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Geometry contracts that screenshots cannot assert. Uses the real App at a real density.
 */
@OptIn(ExperimentalTestApi::class)
class UiContractTest {
    private fun app(): BakiRepo {
        val live = File(LocalStore.path()).parentFile?.let { File(it, "live.json") }
        live?.delete()
        return BakiRepo(
            ClientConfig(serverUrl = "http://127.0.0.1:1", allowWrites = true, themeMode = "light"),
        )
    }

    @Test
    fun fabSitsCloseAboveBottomNavigation() = runSkikoComposeUiTest(
        size = Size(720f, 1600f), density = Density(2f),
    ) {
        PhonePreview.active = true
        try {
            setContent { BakiTheme("light") { App(app()) } }
            waitForIdle()
            val fab = onNodeWithContentDescription("Open quick actions").getUnclippedBoundsInRoot()
            val nav = onNodeWithTag("bottom-navigation").getUnclippedBoundsInRoot()
            val gap = nav.top.value - fab.bottom.value
            assertTrue(gap in 0f..12f, "FAB/nav gap was ${gap}dp, expected 0..12dp")
            onNodeWithContentDescription("Open quick actions").assertWidthIsAtLeast(48.dp)
            onNodeWithContentDescription("Open quick actions").assertHeightIsAtLeast(48.dp)
        } finally {
            PhonePreview.active = false
        }
    }

    @Test
    fun connectedChoicesMeetTouchTargets() = runSkikoComposeUiTest(
        size = Size(720f, 1600f), density = Density(2f),
    ) {
        PhonePreview.active = true
        try {
            setContent { BakiTheme("light") { App(app()) } }
            waitForIdle()
            onNodeWithTag("nav-More").performClick()
            waitForIdle()
            val choices = onAllNodesWithTag("baki-choice", useUnmergedTree = true)
            choices.fetchSemanticsNodes().forEachIndexed { index, _ ->
                choices[index].assertWidthIsAtLeast(48.dp)
                choices[index].assertHeightIsAtLeast(48.dp)
            }
        } finally {
            PhonePreview.active = false
        }
    }

    @Test
    fun todayHeroHasSpokenSummary() = runSkikoComposeUiTest(
        size = Size(720f, 1600f), density = Density(2f),
    ) {
        PhonePreview.active = true
        try {
            setContent { BakiTheme("light") { App(app()) } }
            waitForIdle()
            // The hero remains the one announcement for today's net.
            onNodeWithContentDescription("Today's take, RM", substring = true).assertExists()
        } finally {
            PhonePreview.active = false
        }
    }

    @Test
    fun wideRailStartsExpanded() = runSkikoComposeUiTest(
        size = Size(1760f, 1600f), density = Density(2f),
    ) {
        PhonePreview.active = true
        try {
            setContent { BakiTheme("light") { App(app()) } }
            waitForIdle()
            onNodeWithContentDescription("Collapse navigation").assertExists()
            onNodeWithTag("bottom-navigation").assertDoesNotExist()
        } finally {
            PhonePreview.active = false
        }
    }
}
