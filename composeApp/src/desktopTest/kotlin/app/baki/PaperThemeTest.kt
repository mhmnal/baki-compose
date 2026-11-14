package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaperThemeTest {
    @Test
    fun paperPackHasBothModes() {
        assertTrue(PaperPalette.paper)
        assertTrue(PaperNightPalette.paper)
        assertEquals(PaperPalette, paletteFor("paper", dark = false))
        assertEquals(PaperNightPalette, paletteFor("paper", dark = true))
    }

    @Test
    fun newInstallsUseDynamicMaterial() {
        assertEquals("dynamic", ClientConfig().palette)
        assertTrue(ClientConfig().matchWallpaper)
    }

    @Test
    fun md3ShapeHierarchyIsOrdered() {
        assertEquals(28, Radius.glass.value.toInt())
        assertTrue(Radius.glass > Radius.card)
        assertTrue(Radius.card > Radius.small)
    }

    @Test
    fun md3ShapeScaleMatchesMaterial() {
        assertEquals(4, Radius.tiny.value.toInt())
        assertEquals(8, Radius.small.value.toInt())
        assertEquals(12, Radius.card.value.toInt())
        assertEquals(16, Radius.large.value.toInt())
        assertEquals(28, Radius.sheet.value.toInt())
    }

    @Test
    fun longMoneyStepsDownWithoutLosingTheHero() {
        assertEquals(BakiType.displayNumeric.fontSize, heroMoneyStyle(8705).fontSize)
        assertEquals(BakiType.displaySmallNumeric.fontSize, heroMoneyStyle(1_000_000).fontSize)
        assertEquals(BakiType.headlineNumeric.fontSize, heroMoneyStyle(10_000_000).fontSize)
    }
}
