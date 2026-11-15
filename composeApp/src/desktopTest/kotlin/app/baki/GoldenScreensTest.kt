package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Golden content tests for the two richest screens. The date and fixture storage are pinned, so
 * a clipped hero, collapsed empty state or broken card rhythm fails on pixels instead of relying
 * on someone opening a preview folder.
 *
 * Generate/update baselines once with:
 *   ./gradlew :composeApp:desktopTest --tests app.baki.GoldenScreensTest -Dbaki.golden.update=true
 */
class GoldenScreensTest {
    private val today = "2026-11-15"
    private val month = "2026-11"

    private data class Shot(
        val name: String,
        val widthDp: Int,
        val heightDp: Int,
        val fontScale: Float = 1f,
        val dark: Boolean,
        val content: @Composable () -> Unit,
    )

    @Test
    fun coreScreensMatchTheirGoldens() = runBlocking {
        val originalToday = System.getProperty("baki.testToday")
        val originalNow = System.getProperty("baki.testNowMillis")
        System.setProperty("baki.testToday", today)
        System.setProperty("baki.testNowMillis", "1763200800000")
        val storeFile = File(LocalStore.path())
        val liveFile = File(storeFile.parentFile, "live.json")
        val storeBytes = storeFile.takeIf { it.exists() }?.readBytes()
        val liveBytes = liveFile.takeIf { it.exists() }?.readBytes()
        var repo: BakiRepo? = null
        try {
            // Deterministic, synthetic ledger. Never use the user's store for a baseline.
            liveFile.delete()
            LocalStore.write(BakiJson.encodeToString(Store.serializer(), Store()))
            val fixture = BakiRepo(
                ClientConfig(
                    serverUrl = "http://127.0.0.1:1",
                    allowWrites = true,
                    themeMode = "light",
                    reducedMotion = true,
                ),
            )
            repo = fixture
            fixture.addShift(today, "08:10", hours = 4.5, km = 96.0, grossCents = 11_000, note = "morning run")
            fixture.addShift(today, "17:40", hours = 3.2, km = 71.0, grossCents = 8_150, note = "evening surge")
            fixture.addExpense(1_850, "food", "lunch at KJ", today)
            fixture.addExpense(6_400, "fuel", "Petronas", today)
            fixture.addExpense(320, "toll", "MEX", today)
            fixture.setDailyFoodCap(2_500)
            val store = fixture.store

            val shots = listOf(
                Shot("today-360-light", 360, 800, dark = false) {
                    BakiTheme(mode = "light") {
                        TodayScreen(
                            repo = fixture, day = today, today = today,
                            onDay = {}, onPayout = {}, onPayouts = {}, onQuickAdd = { _, _ -> },
                            onEditEntry = {}, onDeleteEntry = {}, onPreset = {}, onSeeAll = {},
                            onAdd = {}
                        )
                    }
                },
                Shot("today-360-font-200", 360, 1000, fontScale = 2f, dark = false) {
                    BakiTheme(mode = "light") {
                        TodayScreen(
                            repo = fixture, day = today, today = today,
                            onDay = {}, onPayout = {}, onPayouts = {}, onQuickAdd = { _, _ -> },
                            onEditEntry = {}, onDeleteEntry = {}, onPreset = {}, onSeeAll = {},
                            onAdd = {}
                        )
                    }
                },
                Shot("summary-360-light", 360, 800, dark = false) {
                    BakiTheme(mode = "light") {
                        SummaryScreen(repo = fixture, store = store, month = month, onMonth = {})
                    }
                },
                Shot("today-390-type-dark", 390, 844, fontScale = 1.3f, dark = true) {
                    BakiTheme(mode = "dark") {
                        TodayScreen(
                            repo = fixture, day = today, today = today,
                            onDay = {}, onPayout = {}, onPayouts = {}, onQuickAdd = { _, _ -> },
                            onEditEntry = {}, onDeleteEntry = {}, onPreset = {}, onSeeAll = {},
                            onAdd = {}
                        )
                    }
                },
                Shot("summary-390-type-dark", 390, 844, fontScale = 1.3f, dark = true) {
                    BakiTheme(mode = "dark") {
                        SummaryScreen(repo = fixture, store = store, month = month, onMonth = {})
                    }
                },
            )

            for (shot in shots) {
                val actual = render(shot)
                val golden = File("src/desktopTest/resources/golden/${shot.name}.png")
                if (System.getProperty("baki.golden.update") == "true") {
                    golden.parentFile.mkdirs()
                    actual.copyTo(golden, overwrite = true)
                    println("golden updated: ${golden.absolutePath}")
                } else {
                    assertTrue(golden.exists(), "missing golden ${golden.absolutePath}; run with -Dbaki.golden.update=true once")
                    assertImagesMatch(shot.name, golden, actual)
                }
            }

            // Start at 17:00, render at 18:00 so the live hero has real elapsed numbers.
            System.setProperty("baki.testNowMillis", "1763197200000")
            fixture.startLive()
            System.setProperty("baki.testNowMillis", "1763200800000")
            fixture.addPayout(8_000)
            val liveShot = Shot("today-live-360-light", 360, 800, dark = false) {
                BakiTheme(mode = "light") {
                    TodayScreen(
                        repo = fixture, day = today, today = today,
                        onDay = {}, onPayout = {}, onPayouts = {}, onQuickAdd = { _, _ -> },
                        onEditEntry = {}, onDeleteEntry = {}, onPreset = {}, onSeeAll = {},
                        onAdd = {},
                    )
                }
            }
            val liveActual = render(liveShot)
            val liveGolden = File("src/desktopTest/resources/golden/${liveShot.name}.png")
            if (System.getProperty("baki.golden.update") == "true") {
                liveGolden.parentFile.mkdirs()
                liveActual.copyTo(liveGolden, overwrite = true)
            } else {
                assertTrue(liveGolden.exists(), "missing golden ${liveGolden.absolutePath}")
                assertImagesMatch(liveShot.name, liveGolden, liveActual)
            }
            fixture.discardLive()
        } finally {
            repo = null
            if (storeBytes != null) storeFile.writeBytes(storeBytes) else storeFile.delete()
            if (liveBytes != null) liveFile.writeBytes(liveBytes) else liveFile.delete()
            if (originalToday == null) System.clearProperty("baki.testToday") else System.setProperty("baki.testToday", originalToday)
            if (originalNow == null) System.clearProperty("baki.testNowMillis") else System.setProperty("baki.testNowMillis", originalNow)
        }
    }

    private fun render(shot: Shot): File {
        val width = shot.widthDp * 2
        val height = shot.heightDp * 2
        val scene = ImageComposeScene(width = width, height = height, density = Density(2f, fontScale = shot.fontScale)) {
            shot.content()
        }
        try {
            scene.render()
            var image = scene.render()
            repeat(24) { frame -> image = scene.render((frame + 1) * 16_000_000L) }
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode ${shot.name}")
            val out = File("build/golden-actual/${shot.name}.png").apply { parentFile.mkdirs() }
            out.writeBytes(data.bytes)
            return out
        } finally {
            scene.close()
        }
    }

    private fun assertImagesMatch(name: String, golden: File, actual: File) {
        val a = ImageIO.read(golden)
        val b = ImageIO.read(actual)
        assertTrue(a != null && b != null, "could not decode $name")
        assertTrue(a.width == b.width && a.height == b.height, "$name dimensions ${a.width}x${a.height} vs ${b.width}x${b.height}")
        var different = 0
        val maxDiff = 8
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val pa = a.getRGB(x, y)
                val pb = b.getRGB(x, y)
                val dr = abs(((pa shr 16) and 0xff) - ((pb shr 16) and 0xff))
                val dg = abs(((pa shr 8) and 0xff) - ((pb shr 8) and 0xff))
                val db = abs((pa and 0xff) - (pb and 0xff))
                if (dr > maxDiff || dg > maxDiff || db > maxDiff) different++
            }
        }
        val ratio = different.toDouble() / (a.width * a.height)
        assertTrue(ratio <= 0.002, "$name differs in ${"%.2f".format(ratio * 100)}% of pixels (actual: ${actual.absolutePath})")
    }
}
