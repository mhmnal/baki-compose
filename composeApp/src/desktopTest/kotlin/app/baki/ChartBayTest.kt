package app.baki

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Data-viz bays share one inset depth and keep their data 16dp clear of the inner shadow. */
class ChartBayTest {

    private fun shell(): String {
        var dir = File(System.getProperty("user.dir"))
        while (true) {
            val candidate = File(dir, "src/desktopMain/kotlin/app/baki")
            if (candidate.isDirectory) return File(candidate, "DesktopShell.kt").readText()
            dir = dir.parentFile ?: error("cannot locate desktop sources")
        }
    }

    private fun material(): String {
        var dir = File(System.getProperty("user.dir"))
        while (true) {
            val candidate = File(dir, "src/desktopMain/kotlin/app/baki")
            if (candidate.isDirectory) return File(candidate, "Neumorphism.kt").readText()
            dir = dir.parentFile ?: error("cannot locate desktop sources")
        }
    }

    private fun body(name: String): String {
        val text = shell()
        val start = text.indexOf("private fun $name(")
        if (start < 0) return ""
        val end = text.indexOf("\nprivate fun ", start + 10)
            .let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    @Test
    fun everyBayUsesTheSameDepth() {
        listOf("WeekChart", "MonthTrend").forEach { fn ->
            val b = body(fn)
            assertTrue(b.contains("offset = Elevation.viz"), "$fn does not use the shared bay offset")
            assertTrue(b.contains("blur = Elevation.vizBlur"), "$fn does not use the shared bay blur")
            assertTrue(!b.contains("offset = Elevation.inset"), "$fn still mixes a second inset depth")
        }
        assertTrue(
            shell().contains("offset = Elevation.viz, blur = Elevation.vizBlur"),
            "the Compare bay does not use the shared depth",
        )
        val m = material()
        assertTrue(m.contains("val viz = 3.dp"), "bay offset must be 3dp")
        assertTrue(m.contains("val vizBlur = 4.dp"), "bay blur must be 4dp")
    }

    @Test
    fun bayContentKeepsClearOfTheInnerShadow() {
        listOf("WeekChart", "MonthTrend").forEach { fn ->
            assertTrue(
                body(fn).contains("padding(Space.l)"),
                "$fn must pad its content by at least 16dp on all sides",
            )
            assertTrue(!body(fn).contains("padding(Space.m)"), "$fn still uses 12dp padding")
        }
        // Compare's tray (the function is internal, so slice from its section heading)
        val text = shell()
        val idx = text.indexOf("WHERE IT WENT, SIDE BY SIDE")
        assertTrue(idx > 0, "Compare bar section not found")
        val slice = text.substring(idx, (idx + 4000).coerceAtMost(text.length))
        assertTrue(slice.contains("padding(Space.l)"), "Compare bars must pad by 16dp")
    }
}
