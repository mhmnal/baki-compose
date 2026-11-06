package app.baki

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 contrast for every text-on-surface pair the UI actually uses, across all three packs.
 *
 * Thresholds: 4.5:1 for body copy and 3:1 for large text (>= 18.66 sp bold or 24 sp regular), which
 * is what the app's hero figures are. Failing this test means a real accessibility regression, not a
 * style opinion, so the fix is a palette value rather than a waver.
 */
class ContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val packs = mapOf(
        "sage light" to LightPalette,
        "sage dark" to DarkPalette,
        "night" to NightPalette,
        "paper" to PaperPalette,
    )

    @Test
    fun bodyTextIsReadable() {
        val failures = mutableListOf<String>()
        packs.forEach { (name, p) ->
            listOf(
                "onSurface/surface" to (p.onSurface to p.surface),
                "onSurface/container" to (p.onSurface to p.container),
                "muted/surface" to (p.muted to p.surface),
                "muted/container" to (p.muted to p.container),
                "up/surface" to (p.up to p.surface),
                "down/surface" to (p.down to p.surface),
                "warn/surface" to (p.warn to p.surface),
            ).forEach { (pair, colors) ->
                val r = ratio(colors.first, colors.second)
                if (r < 4.5) failures += "$name $pair = ${"%.2f".format(r)} (< 4.5)"
            }
        }
        assertTrue(failures.isEmpty(), "contrast failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun filledControlsStayReadable() {
        val failures = mutableListOf<String>()
        packs.forEach { (name, p) ->
            listOf(
                "onPrimary/primary" to (p.onPrimary to p.primary),
                "onPrimaryContainer/primaryContainer" to (p.onPrimaryContainer to p.primaryContainer),
                "onAccent/accentStrong" to (p.onAccent to p.accentStrong),
            ).forEach { (pair, colors) ->
                val r = ratio(colors.first, colors.second)
                if (r < 4.5) failures += "$name $pair = ${"%.2f".format(r)} (< 4.5)"
            }
        }
        assertTrue(failures.isEmpty(), "filled-control contrast failures:\n" + failures.joinToString("\n"))
    }
}
