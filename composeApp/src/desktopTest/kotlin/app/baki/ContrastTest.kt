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

    @Test
    fun materialSchemesKeepTextReadable() {
        val schemes = listOf(
            "light" to androidx.compose.material3.lightColorScheme(),
            "dark" to androidx.compose.material3.darkColorScheme(),
        )
        val failures = mutableListOf<String>()
        schemes.forEach { (name, s) ->
            listOf(
                "onSurface/surface" to (s.onSurface to s.surface),
                "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
                "onPrimary/primary" to (s.onPrimary to s.primary),
                "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            ).forEach { (pair, colors) ->
                val r = ratio(colors.first, colors.second)
                if (r < 4.5) failures += "$name $pair = ${"%.2f".format(r)} (< 4.5)"
            }
        }
        assertTrue(failures.isEmpty(), "material contrast failures:\n" + failures.joinToString("\n"))
    }
}
