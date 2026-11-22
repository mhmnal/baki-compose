package app.baki

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class CentsFieldTest {
    @Test
    fun centsFirstFormattingAndPasteAreExact() {
        assertEquals("0.01", formatCentsDigits("1"))
        assertEquals("0.12", formatCentsDigits("12"))
        assertEquals("1.23", formatCentsDigits("123"))
        assertEquals("12.34", formatCentsDigits("1234"))
        assertEquals("0.00", formatCentsDigits("0"))
        assertEquals("1234", normalizeCentsDigits("12.34", ""))
        assertEquals("12", normalizeCentsDigits("0012", ""))
        assertEquals(1234L, centsFromDigits("1234"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fieldKeepsFixedDecimalWhileTypingDigits() = runSkikoComposeUiTest(
        size = Size(360f, 200f), density = Density(2f),
    ) {
        val state = mutableStateOf("")
        setContent {
            BakiTheme("light") {
                CentsField(
                    digits = state.value,
                    onDigitsChange = { state.value = it },
                    modifier = Modifier.testTag("cents-field"),
                    label = "Amount",
                )
            }
        }
        onNodeWithTag("cents-field").performTextInput("1234")
        waitForIdle()
        assertEquals("1234", state.value)
        onNodeWithTag("cents-field").assertTextContains("12.34")
    }
}
