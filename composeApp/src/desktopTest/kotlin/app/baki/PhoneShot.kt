package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders both app shells offscreen, in every theme mode, so the design can be inspected
 * without a device, an emulator, or an X screen grab.
 *
 * The desktop and Android targets compose the same `commonMain` code, so this is the real UI:
 * [PhonePreview] only forces which shell composes, and the repo's `themeMode` picks the palette.
 *
 * Run: gradle :composeApp:desktopTest --tests app.baki.PhoneShot
 * Output: composeApp/build/preview/<mode>/phone/<tab>.png and .../<mode>/desk/<view>.png
 */
class PhoneShot {

    private val phoneTabs = listOf(
        0 to "overview",
        1 to "spending",
        2 to "shifts",
        3 to "summary",
        4 to "more",
    )

    private val desktopViews = listOf(
        "overview", "entries", "shifts", "routes",
        "compare", "year", "statement", "settings",
    )

    private val modes = listOf("dark", "light")

    @Test
    fun renderAll() = runBlocking {
        // The test JVM's user.home is a shared temp dir; reset the fixture store so reruns do not
        // accumulate shifts and expenses. The Keystore/key file stays put.
        resetPreviewStore()
        // seed once; every mode reloads the same persisted store
        seed(
            BakiRepo(
                ClientConfig(
                    serverUrl = "http://127.0.0.1:1",
                    allowWrites = true,
                    themeMode = "dark",
                ),
            ),
        )

        val root = File("build/preview")
        for (mode in modes) {
            // a fixture server that refuses immediately: rendering must not depend on a network
            val repo = BakiRepo(
                ClientConfig(
                    serverUrl = "http://127.0.0.1:1",
                    allowWrites = true,
                    themeMode = mode,
                ),
            )
            val phoneDir = File(root, "$mode/phone").apply { mkdirs() }
            val deskDir = File(root, "$mode/desk").apply { mkdirs() }

            // phone: 390x844 dp at 2x = 780x1688 px
            PhonePreview.active = true
            for ((index, name) in phoneTabs) {
                PhonePreview.initialTab = index
                render(File(phoneDir, "$name.png"), 780, 1688, 2f) { App(repo) }
            }

            // desktop: 1440x900 at 1x, no wallpaper and no other windows in frame
            PhonePreview.active = false
            PhonePreview.initialTab = 0
            for (view in desktopViews) {
                PhonePreview.desktopView = view
                render(File(deskDir, "$view.png"), 1440, 900, 1f) { App(repo) }
            }
        }

        PhonePreview.active = false
        PhonePreview.initialTab = 0
        PhonePreview.desktopView = null
    }

    /** Composes one frame series and writes the settled image; the scene owns its own disposal. */
    private fun render(out: File, width: Int, height: Int, density: Float, content: @Composable () -> Unit) {
        // ImageComposeScene takes pixels, so width/height are already device pixels
        val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
            content()
        }
        try {
            scene.render()
            var image = scene.render()
            // let entry animations and the first layout pass settle
            repeat(14) { frame ->
                image = scene.render((frame + 1) * 16_000_000L)
            }
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("could not encode ${out.name}")
            out.writeBytes(data.bytes)
            println("preview: ${out.absolutePath} (${data.bytes.size} bytes)")
        } finally {
            scene.close()
        }
    }

    /** Remove only the seeded ledger/config files, not the desktop key material. */
    private fun resetPreviewStore() {
        val dir = File(System.getProperty("user.home") ?: ".", ".local/share/baki")
        listOf("store.json", "store.json.bak", "config.json", "config.json.bak", "live.json").forEach {
            File(dir, it).delete()
        }
    }

    /** Two shifts and a few expenses, so the screens show real shapes instead of empty states. */
    private suspend fun seed(repo: BakiRepo) {
        val today = todayIso()
        runCatching {
            repo.addShift(
                date = today, start = "08:10", hours = 4.5, km = 96.0,
                grossCents = 11_000, note = "morning run",
                odoStart = 41_200.0, odoEnd = 41_296.0,
            )
        }
        runCatching {
            repo.addShift(
                date = today, start = "17:40", hours = 3.2, km = 71.0,
                grossCents = 8_150, note = "evening surge",
            )
        }
        runCatching {
            val shift = repo.store.shifts.firstOrNull { it.start == "17:40" }
            if (shift != null) {
                RouteStore.put(
                    ShiftRoute(
                        shiftId = shift.id,
                        date = shift.date,
                        points = listOf(
                            RoutePoint(3.1390, 101.6869, 1_762_000_000_000),
                            RoutePoint(3.1450, 101.6950, 1_762_000_120_000),
                            RoutePoint(3.1520, 101.7020, 1_762_000_240_000),
                            RoutePoint(3.1590, 101.7110, 1_762_000_360_000),
                            RoutePoint(3.1510, 101.7210, 1_762_000_480_000),
                            RoutePoint(3.1420, 101.7130, 1_762_000_600_000),
                        ),
                    ),
                )
            }
        }
        runCatching { repo.addExpense(1_850, "food", "lunch at KJ", today) }
        runCatching { repo.addExpense(6_400, "fuel", "Petronas", today) }
        runCatching { repo.addExpense(320, "toll", "MEX", today) }
        runCatching { repo.addExpense(1_200, "wear", "tyre wear", today) }
        runCatching { repo.setDailyFoodCap(2_500) }
        runCatching {
            repo.saveStatement(
                Statement(
                    weekStart = addDays(today, -7),
                    rangeText = "27 October – 2 November 2026",
                    income = 507.34, deductions = 37.50, weekly = 469.84,
                    insuranceCount = 5, importedAt = "2026-11-03T09:12:00Z",
                ),
            )
        }
        runCatching {
            repo.saveStatement(
                Statement(
                    weekStart = addDays(today, -14),
                    rangeText = "20–26 October 2026",
                    income = 482.10, deductions = 30.00, weekly = 452.10,
                    insuranceCount = 4, importedAt = "2026-10-27T08:44:00Z",
                ),
            )
        }
    }
}
