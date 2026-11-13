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
    fun newInstallsStartOnPaper() {
        assertEquals("paper", ClientConfig().palette)
    }

    @Test
    fun glassKeepsItsOwnGeometry() {
        assertEquals(50, Radius.glass.value.toInt())
        assertTrue(Radius.glass > Radius.card)
    }

    @Test
    fun longMoneyStepsDownWithoutLosingTheHero() {
        assertEquals(BakiType.displayNumeric.fontSize, heroMoneyStyle(8705).fontSize)
        assertEquals(BakiType.displaySmallNumeric.fontSize, heroMoneyStyle(1_000_000).fontSize)
        assertEquals(BakiType.headlineNumeric.fontSize, heroMoneyStyle(10_000_000).fontSize)
    }
}
