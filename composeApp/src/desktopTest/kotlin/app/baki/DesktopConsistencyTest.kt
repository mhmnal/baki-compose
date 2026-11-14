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
    fun tabSwitchesAreNotCrossfaded() {
        // a fade composites two embossed pages: shadows ramp in and "settle", which reads as jank
        val text = shell()
        listOf("AnimatedContent(", "Crossfade(", "fadeIn(", "togetherWith(").forEach { marker ->
            assertTrue(!text.contains(marker), "tab transitions must be instant, found $marker")
        }
    }

    @Test
    fun pillShapesUseTheRadiusToken() {
        listOf("DesktopShell.kt", "Neumorphism.kt").forEach { name ->
            val text = File(desktopDir(), name).readText()
            assertTrue(!text.contains("RoundedCornerShape(50)"), "$name hardcodes a pill radius")
        }
    }
}

/** The material is hand-rolled from the brief: no third-party shadow engine, no Material chrome. */
class ShadowEngineTest {

    private fun material() = File(
        File(System.getProperty("user.dir"), "src/desktopMain/kotlin/app/baki"),
        "Neumorphism.kt",
    ).readText()

    private fun functionBody(name: String): String {
        val m = material()
        val start = m.indexOf("fun Modifier.$name(")
        if (start < 0) return ""
        val end = m.indexOf("\nfun ", start + 10).let { if (it < 0) m.length else it }
        return m.substring(start, end)
    }

    @Test
    fun theShadowEngineIsNative() {
        val m = material()
        assertTrue(!m.contains("boxShadow"), "the third-party shadow modifier is still present")
        assertTrue(!m.contains("dev.lennartegb"), "the shadow library import remains")
        assertTrue(m.contains("MaskFilter.makeBlur"), "the native blurred shadow engine is missing")
        assertTrue(m.contains("FilterBlurMode.NORMAL"), "blur mode missing from the native engine")
    }

    @Test
    fun everySurfaceTierDrawsInItsOwnLayer() {
        // canvas transforms leak on desktop; each shadow must sit in its own graphicsLayer so it
        // cannot shift what is painted afterwards
        listOf("raised", "inset", "elevated").forEach { fn ->
            val body = functionBody(fn)
            assertTrue(body.contains(".graphicsLayer()"), "$fn is not isolated in a graphicsLayer")
            assertTrue(body.contains("drawBehind"), "$fn does not paint its own shadows")
        }
        val m = material()
        assertTrue(
            m.contains("): Modifier = raised("),
            "cardSurface must use the same raised engine, not a second shadow path",
        )
    }

    @Test
    fun theInsetClipsItsShadowsToTheShape() {
        val body = functionBody("inset")
        assertTrue(body.contains("clipPath"), "the inset must clip its shadows to the rounded shape")
    }

    @Test
    fun tokensMatchTheBrief() {
        val m = material()
        listOf(
            "0xFFE0E5EC" to "light canvas",
            "0xFF1E222B" to "dark canvas",
            "0xFF3B82F6" to "Electric Blue accent",
            "0xFF10B981" to "Emerald success",
            "0xFF1E293B" to "light ink",
            "0xFF64748B" to "light muted",
        ).forEach { (hex, label) ->
            assertTrue(m.contains(hex), "$label ($hex) missing from the material")
        }
    }
}

/** Obsidian Slate: the dark branch has its own tokens, geometry and theme resolution. */
class DarkModeTest {

    private fun source(path: String): String {
        var dir = File(System.getProperty("user.dir"))
        while (true) {
            val candidate = File(dir, "src/$path")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile ?: error("cannot locate sources")
        }
    }

    private fun material() = source("desktopMain/kotlin/app/baki/Neumorphism.kt")

    @Test
    fun obsidianTokensArePresent() {
        val m = material()
        listOf(
            "0xFF1E222B" to "dark canvas",
            "0xFF282D39" to "raised glow / inset highlight",
            "0xFF12151B" to "raised drop",
            "0xFF14171D" to "inset dark",
            "0xFFF1F5F9" to "dark primary text",
            "0xFF94A3B8" to "dark secondary text",
            "0xFF3B82F6" to "Electric Blue",
            "0xFF10B981" to "Emerald",
            "0xFFEF4444" to "danger red",
        ).forEach { (hex, label) ->
            assertTrue(m.contains(hex), "$label ($hex) missing")
        }
    }

    @Test
    fun darkGeometryFollowsTheBrief() {
        val m = material()
        listOf(
            "if (neu.isDark) 6.dp" to "raised offset 6dp",
            "if (neu.isDark) 10.dp" to "raised blur 10dp",
            "if (neu.isDark) 3.dp" to "sunken offset 3dp",
            "if (neu.isDark) 5.dp" to "sunken blur 5dp",
            "0.90f" to "inner dark alpha",
            "0.70f" to "inner light alpha",
        ).forEach { (needle, label) ->
            assertTrue(m.contains(needle), "$label missing from the engine")
        }
    }

    @Test
    fun deskThemeUsesMaterialBaseline() {
        val theme = source("commonMain/kotlin/app/baki/Theme.kt")
        assertTrue(theme.contains("lightColorScheme()") && theme.contains("darkColorScheme()"))
        assertTrue(theme.contains("paletteOf(scheme, dark)"))
    }
}
