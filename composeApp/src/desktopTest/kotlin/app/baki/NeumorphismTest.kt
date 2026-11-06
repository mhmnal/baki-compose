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
    fun lightPackLiftsPanelsAndDarkPackSinksThem() {
        val light = neuOf(LightPalette)
        assertTrue(lum(light.panel) > lum(light.base), "panel should sit above the base in light mode")
        assertTrue(lum(light.light) > lum(light.base), "light shadow must be lighter than the base")
        assertTrue(lum(light.dark) < lum(light.base), "dark shadow must be darker than the base")

        val night = neuOf(NightPalette)
        assertTrue(lum(night.dark) < lum(night.base))
        assertTrue(lum(night.light) > lum(night.base))
    }

    @Test
    fun shadowsKeepEnoughSeparationToRead() {
        listOf(LightPalette, DarkPalette, NightPalette, PaperPalette).forEach { p ->
            val n = neuOf(p)
            val spread = abs(lum(n.light) - lum(n.dark))
            assertTrue(spread > 0.05, "shadows too close in pack ${p.dark}: $spread")
        }
    }
}
