package app.baki

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The desk studies; the phone records. A write call appearing in the desktop shell is a boundary
 * violation, not a feature.
 */
class ReadOnlyShellTest {

    private fun shell(): String {
        var dir = File(System.getProperty("user.dir"))
        repeat(4) {
            val candidate = File(dir, "src/desktopMain/kotlin/app/baki/DesktopShell.kt")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile ?: dir
        }
        error("cannot locate DesktopShell.kt")
    }

    @Test
    fun theDesktopNeverWritesRecords() {
        val text = shell()
        val forbidden = listOf(
            "repo.addEntry", "repo.updateEntry", "repo.deleteEntry",
            "repo.addShift", "repo.updateShift", "repo.deleteShift",
            "EntryDialog(", "DeleteDialog(",
            "\"New entry\"",
        )
        val found = forbidden.filter { text.contains(it) }
        assertTrue(found.isEmpty(), "write paths on the read-only desk: $found")
    }

    @Test
    fun theDeskTellsTheUserItIsReadOnly() {
        assertTrue(shell().contains("read-only"), "the entries table should say where editing happens")
    }
}
