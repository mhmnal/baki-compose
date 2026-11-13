package app.baki

import kotlin.test.Test
import kotlin.test.assertTrue

/** Technical a11y guardrails that can be enforced without a device/UI test harness. */
class AccessibilityTest {
    @Test
    fun interactiveControlFloorsMeet48dp() {
        assertTrue(Control.primary.value >= 48f, "primary control is ${Control.primary.value}dp")
        assertTrue(Control.secondary.value >= 48f, "secondary control is ${Control.secondary.value}dp")
    }
}
