package app.baki

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders the day map offscreen so the cartography can be inspected without a recorded shift. */
class MapPreview {

    @Test
    fun renderPreview() = runBlocking {
        val size = Size(900f, 420f)

        // two synthetic shifts in Kuala Lumpur, so A/B markers and two colours are both exercised
        fun loop(lat0: Double, lon0: Double, index: Int): ShiftRoute {
            val pts = (0..140).map { i ->
                val a = i / 140.0 * 2 * Math.PI
                RoutePoint(
                    lat = lat0 + kotlin.math.sin(a) * 0.010 + index * 0.002,
                    lon = lon0 + kotlin.math.cos(a) * 0.014,
                    t = 1_700_000_000_000 + i * 2_000L,
                    speed = 11.0,
                )
            }
            return ShiftRoute("preview-$index", todayIso(), simplifyRoute(pts, 3.0))
        }
        val routes = listOf(
            MapRoute(loop(3.1390, 101.6869, 0), Color(0xFF4A5A2E), "Shift 1"),
            MapRoute(loop(3.1520, 101.7100, 1), Color(0xFF0F6B4F), "Shift 2"),
        )

        // warm the tiles the projection will ask for
        val all = routes.flatMap { it.route.points }
        MapProjection.fit(all, size)?.visibleTiles()?.forEach { (z, x, y) -> TileCache.load(z, x, y) }

        val scene = ImageComposeScene(width = 900, height = 420, density = Density(2f)) {
            BakiTheme(mode = "dark") {
                RouteMap(routes, Modifier.fillMaxSize(), showAttribution = true)
            }
        }
        var image = scene.render()
        repeat(12) {
            delay(250)
            image = scene.render(it * 16_000_000L)
        }
        val data = image.encodeToData(EncodedImageFormat.PNG)!!
        val out = File("build/map-preview.png").apply { parentFile?.mkdirs() }
        out.writeBytes(data.bytes)
        scene.close()
        println("preview written: ${out.absolutePath} (${data.bytes.size} bytes)")
    }
}
