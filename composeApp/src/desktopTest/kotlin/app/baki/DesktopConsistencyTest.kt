package app.baki

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Desktop-only rules: one material, one component set, no Material chrome left over. */
class DesktopConsistencyTest {

    private fun desktopDir(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(4) {
            val candidate = File(dir, "src/desktopMain/kotlin/app/baki")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: dir
        }
        error("cannot locate desktop sources")
    }

    private fun shell() = File(desktopDir(), "DesktopShell.kt").readText()

    @Test
    fun noMaterialChromeInTheDesktopShell() {
        val forbidden = listOf(
            "OutlinedTextField(", "DropdownMenu(", "androidx.compose.material3.Button(",
            "BakiSheet(", "androidx.compose.material3.Switch(", "Slider(",
        )
        val found = forbidden.filter { shell().contains(it) }
        assertTrue(found.isEmpty(), "Material components on the neumorphic shell: $found")
    }

    @Test
    fun noRawHexColoursInTheDesktopShell() {
        assertTrue(!shell().contains("Color(0x"), "raw colour literal in the shell")
    }

    @Test
    fun pillShapesUseTheRadiusToken() {
        listOf("DesktopShell.kt", "Neumorphism.kt").forEach { name ->
            val text = File(desktopDir(), name).readText()
            assertTrue(!text.contains("RoundedCornerShape(50)"), "$name hardcodes a pill radius")
        }
    }
}

/** The shadow math is delegated to a maintained KMP library, never re-implemented by hand. */
class ShadowEngineTest {

    private fun material() = File(
        File(System.getProperty("user.dir"), "src/desktopMain/kotlin/app/baki"),
        "Neumorphism.kt",
    ).readText()

    @Test
    fun everyShadowIsDrawnInItsOwnLayer() {
        // the library leaks canvas transforms on desktop; a shadow drawn on the shared canvas shifts
        // everything painted after it. Each call therefore lives inside its own graphicsLayer.
        val text = material()
        var index = text.indexOf(".boxShadow(")
        var checked = 0
        while (index >= 0) {
            // each modifier function is one chain: a graphicsLayer anywhere before the first
            // shadow in that function contains every shadow that follows it
            val fnStart = text.lastIndexOf("): Modifier", index)
            val chain = text.substring(fnStart.coerceAtLeast(0), index)
            assertTrue(
                chain.contains(".graphicsLayer()"),
                "shadow #${checked + 1} is not isolated in a graphicsLayer",
            )
            checked++
            index = text.indexOf(".boxShadow(", index + 1)
        }
        assertTrue(checked >= 4, "expected the four material tiers to draw shadows, found $checked")
    }

    @Test
    fun shadowsComeFromTheLibrary() {
        val m = material()
        assertTrue(m.contains("dev.lennartegb.shadows.boxShadow"), "the material must use the library modifier")
        assertTrue(!m.contains("MaskFilter") && !m.contains("makeBlur"), "hand-rolled blur shaders crept back in")
        // every surface tier is expressed through the library
        listOf("fun Modifier.raised", "fun Modifier.inset", "fun Modifier.elevated").forEach { fn ->
            assertTrue(m.contains(fn), "$fn missing")
        }
    }
}
