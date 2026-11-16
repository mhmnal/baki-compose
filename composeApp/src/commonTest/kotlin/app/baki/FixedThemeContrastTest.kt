package app.baki

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** The fixed fallback themes must be legible, not merely pretty. */
class FixedThemeContrastTest {
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
    private fun check(name: String, scheme: ColorScheme) {
        val pairs = listOf(
            "body" to (scheme.onSurface to scheme.surface),
            "secondary body" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "primary action" to (scheme.onPrimary to scheme.primary),
            "primary container" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "secondary action" to (scheme.onSecondary to scheme.secondary),
            "secondary container" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "tertiary action" to (scheme.onTertiary to scheme.tertiary),
            "tertiary container" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "error" to (scheme.onError to scheme.error),
            "error container" to (scheme.onErrorContainer to scheme.errorContainer),
        )
        val failures = pairs.mapNotNull { (label, pair) ->
            val r = ratio(pair.first, pair.second)
            if (r < 4.5) "$name $label = ${"%.2f".format(r)} (< 4.5)" else null
        }
        assertTrue(failures.isEmpty(), "fixed theme contrast failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun fixedLightAndDarkAreReadable() {
        check("Oat & Pine", BakiLightColors)
        check("Roast & Pine", BakiDarkColors)
        check("Oat & Pine high contrast", BakiLightHighContrast)
        check("Roast & Pine high contrast", BakiDarkHighContrast)
    }
}
