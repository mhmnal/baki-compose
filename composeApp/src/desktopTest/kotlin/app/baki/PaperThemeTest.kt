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
}
