package app.baki

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the nested card scheme that ordinary Material contrast tests cannot see. */
class CardContrastTest {
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
    fun tintedCardContentStaysReadable() {
        val failures = mutableListOf<String>()
        listOf(
            "light" to lightColorScheme(),
            "dark" to darkColorScheme(),
        ).forEach { (name, base) ->
            listOf(
                "primaryContainer" to (base.primaryContainer to base.onPrimaryContainer),
                "tertiaryContainer" to (base.tertiaryContainer to base.onTertiaryContainer),
                "surfaceContainerHigh" to (base.surfaceContainerHigh to base.onSurface),
            ).forEach { (label, pair) ->
                val card = md3CardScheme(base, pair.first, pair.second)
                val secondary = card.onSurfaceVariant.compositeOver(card.surface)
                val r = ratio(secondary, card.surface)
                if (r < 4.5) failures += "$name $label secondary = ${"%.2f".format(r)} (< 4.5)"
            }
        }
        assertTrue(failures.isEmpty(), "card contrast failures:\n" + failures.joinToString("\n"))
    }
}
