package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Ensures the connected-group selection actually animates instead of snapping. The frame is
 * advanced explicitly, so this is deterministic and independent of wall-clock animation.
 */
@OptIn(ExperimentalTestApi::class)
class MotionContractTest {
    @Test
    fun connectedGroupMorphsAcrossFrames() = runSkikoComposeUiTest(
        size = Size(720f, 1200f),
        density = Density(2f),
    ) {
        mainClock.autoAdvance = false
        var selected by mutableStateOf("Money")
        setContent {
            BakiTheme("light") {
                ConnectedChoiceGroup(
                    options = listOf("Money", "Shift", "App"),
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }
        waitForIdle()
        onNodeWithText("Shift").performClick()
        mainClock.advanceTimeBy(16)
        val mid = captureToImage().toPixelMap()
        mainClock.advanceTimeBy(1_000)
        val settled = captureToImage().toPixelMap()
        var different = 0
        for (y in 0 until mid.height) {
            for (x in 0 until mid.width) {
                if (mid[x, y] != settled[x, y]) different++
            }
        }
        assertFalse(different == 0, "selected group snapped instead of morphing")
    }
}
