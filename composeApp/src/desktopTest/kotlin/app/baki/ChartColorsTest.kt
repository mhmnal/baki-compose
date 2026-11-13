package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartColorsTest {

    @Test
    fun noTwoSlicesShareAColour() {
        val ids = listOf("food", "fuel", "toll", "parking", "carwash", "maintenance")
        listOf(LightPalette, DarkPalette, NightPalette, PaperPalette, PaperNightPalette, MistLight, MistDark).forEach { p ->
            val colors = chartColors(p, ids)
            assertEquals(colors.size, colors.distinct().size, "duplicate slice colour in ${if (p.dark) "dark" else "light"} pack")
        }
    }

    @Test
    fun otherIsAlwaysTheNeutralGrey() {
        val colors = chartColors(MistLight, listOf("food", "other", "fuel"))
        assertEquals(ChartGrey, colors[1])
        assertTrue(colors[0] != ChartGrey && colors[2] != ChartGrey)
    }

    @Test
    fun greyIsSkippedForRealCategories() {
        // Other present or not, the five hues stay unique among themselves
        val without = chartColors(MistLight, listOf("a", "b", "c", "d", "e"))
        assertEquals(5, without.distinct().size)
        assertTrue(without.none { it == ChartGrey })
    }
}
