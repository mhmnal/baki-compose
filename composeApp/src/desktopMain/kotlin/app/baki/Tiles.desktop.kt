package app.baki

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import org.jetbrains.skia.Image

actual object TileSource {

    private val client by lazy {
        HttpClient(CIO) {
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
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
