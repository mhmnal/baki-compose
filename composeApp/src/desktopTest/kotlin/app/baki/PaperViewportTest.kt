package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Phone-only viewport sweep: narrow, standard, wide and large type.
 * Frames are material for review, not golden snapshots.
 */
class PaperViewportTest {

    private data class Viewport(val name: String, val widthDp: Int, val heightDp: Int, val fontScale: Float = 1f)

    private val viewports = listOf(
        Viewport("360", 360, 800),
        Viewport("390", 390, 844),
        Viewport("412", 412, 915),
        Viewport("390-type", 390, 844, fontScale = 1.3f),
        Viewport("360-type", 360, 800, fontScale = 1.3f),
        Viewport("360-type-xl", 360, 800, fontScale = 1.8f),
    )

    @Test
    fun renderViewportSweep() = runBlocking {
        val today = todayIso()
        // Seed one large total so the adaptive hero and long rows are exercised, then remove it.
        val seedRepo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:1", allowWrites = true, themeMode = "light"))
        runCatching { seedRepo.addShift(today, "15:41", hours = 5.0, km = 182.0, grossCents = 1_234_500, note = "viewport test") }
        val seeded = seedRepo.store.shifts.firstOrNull { it.note == "viewport test" }
        val root = File("build/preview/viewports")
        try {
            for (mode in listOf("light", "dark")) {
                val dir = File(root, mode).apply { mkdirs() }
                for (v in viewports) {
                    for ((index, tab) in listOf(0 to "overview", 1 to "spending", 2 to "shifts", 3 to "summary", 4 to "more")) {
                        PhonePreview.active = true
                        PhonePreview.initialTab = index
                        val repo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:1", allowWrites = true, themeMode = mode))
                        render(
                            File(dir, "${v.name}-$tab.png"),
                            width = v.widthDp * 2,
                            height = v.heightDp * 2,
                            density = Density(2f, fontScale = v.fontScale),
                        ) { App(repo) }
                    }
                }
            }
        } finally {
            seeded?.let { seedRepo.deleteShift(it.id) }
            PhonePreview.active = false
            PhonePreview.initialTab = 0
        }
    }

    private fun render(out: File, width: Int, height: Int, density: Density, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width, height = height, density = density) { content() }
        try {
            var image = scene.render()
            repeat(14) { frame -> image = scene.render((frame + 1) * 16_000_000L) }
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode ${out.name}")
            out.writeBytes(data.bytes)
            println("viewport: ${out.absolutePath} (${data.bytes.size} bytes)")
        } finally {
            scene.close()
        }
    }
}
