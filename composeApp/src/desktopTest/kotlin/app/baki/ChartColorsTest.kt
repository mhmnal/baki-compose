package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartColorsTest {

    @Test
    fun noTwoSlicesShareAColour() {
        val ids = listOf("food", "fuel", "toll", "parking", "carwash", "maintenance")
        listOf(
            androidx.compose.material3.lightColorScheme(),
            androidx.compose.material3.darkColorScheme(),
        ).forEach { p ->
            val colors = chartColors(p, ids)
            assertEquals(colors.size, colors.distinct().size, "duplicate slice colour")
        }
    }

    @Test
    fun otherIsAlwaysTheOutlineRole() {
        val scheme = androidx.compose.material3.lightColorScheme()
        val colors = chartColors(scheme, listOf("food", "other", "fuel"))
        assertEquals(scheme.outline, colors[1])
        assertTrue(colors[0] != scheme.outline && colors[2] != scheme.outline)
    }

    @Test
    fun greyIsSkippedForRealCategories() {
        // Other present or not, the five hues stay unique among themselves
        val scheme = androidx.compose.material3.lightColorScheme()
        val without = chartColors(scheme, listOf("a", "b", "c", "d", "e"))
        assertEquals(5, without.distinct().size)
        assertTrue(without.none { it == scheme.outline })
    }
}
