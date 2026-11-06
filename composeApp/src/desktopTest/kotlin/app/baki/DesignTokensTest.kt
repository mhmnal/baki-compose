package app.baki

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The design system only holds if nobody bypasses it. This walks the UI sources and fails when a
 * literal type size or corner radius appears outside Design.kt, so consistency cannot quietly drift.
 */
class DesignTokensTest {

    private fun sourceDirs(): List<File> {
        var dir = File(System.getProperty("user.dir"))
        repeat(4) {
            val common = File(dir, "src/commonMain/kotlin/app/baki")
            val desktop = File(dir, "src/desktopMain/kotlin/app/baki")
            if (common.isDirectory) return listOfNotNull(common, desktop.takeIf { it.isDirectory })
            dir = dir.parentFile ?: dir
        }
        error("cannot locate the UI sources from ${System.getProperty("user.dir")}")
    }

    private fun offenders(pattern: Regex): List<String> = sourceDirs().flatMap { it.listFiles().orEmpty().toList() }
        .filter { file -> file.name.endsWith(".kt") && file.name != "Design.kt" }
        .flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> pattern.containsMatchIn(line) }
                .map { (i, line) -> "${file.name}:${i + 1}  ${line.trim().take(90)}" }
        }

    @Test
    fun noRawFontSizesOutsideTheDesignSystem() {
        val found = offenders(Regex("""fontSize\s*=\s*[\d.]+\.sp"""))
        assertTrue(found.isEmpty(), "raw fontSize outside Design.kt:\n" + found.joinToString("\n"))
    }

    @Test
    fun noRawCornerRadii() {
        val found = offenders(Regex("""RoundedCornerShape\(\s*[\d.]+\.dp"""))
        assertTrue(found.isEmpty(), "raw corner radius outside Design.kt:\n" + found.joinToString("\n"))
    }
}

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
