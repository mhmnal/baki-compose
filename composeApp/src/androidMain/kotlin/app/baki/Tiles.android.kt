package app.baki

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header

/**
 * OpenStreetMap raster tiles. Their usage policy asks for a descriptive User-Agent and light,
 * cached traffic — the in-memory cache in [TileCache] covers that for one day view at a time.
 */
actual object TileSource {

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 6_000
            }
        }
    }

    actual suspend fun fetch(z: Int, x: Int, y: Int): ImageBitmap? = runCatching {
        val bytes: ByteArray = client.get("https://tile.openstreetmap.org/$z/$x/$y.png") {
            header("User-Agent", "Baki/0.3 (personal shift logbook)")
            header("Accept", "image/png")
        }.body()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
