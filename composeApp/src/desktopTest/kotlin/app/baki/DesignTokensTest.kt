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
        .filter { file -> file.name.endsWith(".kt") && file.name !in setOf("Design.kt", "Theme.kt") }
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
