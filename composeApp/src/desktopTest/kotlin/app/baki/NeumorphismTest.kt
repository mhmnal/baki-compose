package app.baki

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The material is derived, so its invariants can be tested like any other logic. */
class NeumorphismTest {

    private fun lum(c: Color) = 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue

    @Test
    fun blendHitsBothEnds() {
        val a = Color(0f, 0f, 0f)
        val b = Color(1f, 1f, 1f)
        assertEquals(0f, blend(a, b, 0f).red)
        assertEquals(1f, blend(a, b, 1f).red)
        val mid = blend(a, b, 0.5f).red
        assertTrue(abs(mid - 0.5f) < 0.01f, "midpoint was $mid")
    }

    @Test
    fun blendNeverProducesTransparency() {
        // a translucent base is what made the window composite over the desktop
        val out = blend(Color(0.2f, 0.3f, 0.4f), Color.White, 0.3f)
        assertEquals(1f, out.alpha)
    }

    @Test
    fun theFaceIsTheCanvasExactly() {
        // true neumorphism: depth is shadow alone, the face carries no second colour
        listOf(LightPalette, DarkPalette, NightPalette, PaperPalette, PaperNightPalette, MistLight, MistDark).forEach { p ->
            val n = neuOf(p)
            assertEquals(n.base, n.panel, "raised face must equal the canvas in pack dark=${p.dark}")
            assertTrue(lum(n.light) > lum(n.base), "highlight must be lighter than the base")
            assertTrue(lum(n.dark) < lum(n.base), "shade must be darker than the base")
            // tints, not black-alpha: the shade keeps some colour in every channel
            assertTrue(n.dark.blue > 0.05f && n.dark.green > 0.05f, "shade collapsed to near-black")
        }
    }

    @Test
    fun tiersFollowTheSpecifiedDistances() {
        assertEquals(9f, Elevation.card.value)
        assertEquals(16f, Elevation.cardBlur.value)
        assertEquals(5f, Elevation.control.value)
        assertEquals(10f, Elevation.controlBlur.value)
        assertEquals(4f, Elevation.inset.value)
        assertEquals(8f, Elevation.insetBlur.value)
    }

    @Test
    fun darkThemesShortenDistances() {
        val light = neuOf(LightPalette)
        val dark = neuOf(DarkPalette)
        assertEquals(9f, Elevation.distance(light, Elevation.card).value, 0.01f)
        assertEquals(5.4f, Elevation.distance(dark, Elevation.card).value, 0.01f)
        assertEquals(9.6f, Elevation.blur(dark, Elevation.cardBlur).value, 0.01f)
    }

    @Test
    fun shadowsKeepEnoughSeparationToRead() {
        listOf(LightPalette, DarkPalette, NightPalette, PaperPalette, PaperNightPalette, MistLight, MistDark).forEach { p ->
            val n = neuOf(p)
            val spread = abs(lum(n.light) - lum(n.dark))
            assertTrue(spread > 0.05, "shadows too close in pack ${p.dark}: $spread")
        }
    }
}
