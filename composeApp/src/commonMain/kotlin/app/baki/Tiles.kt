package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** Fetches one raster tile. Platform-specific because image decoding differs. */
expect object TileSource {
    suspend fun fetch(z: Int, x: Int, y: Int): ImageBitmap?
}

/**
 * Small in-memory tile cache. Tiles are public map data, not personal data, so they stay in RAM
 * only — nothing about the user's location is written outside the encrypted route file.
 */
object TileCache {

    private const val MAX_TILES = 96
    private val tiles = HashMap<String, ImageBitmap>()

    var version by mutableStateOf(0)
        private set

    fun peek(z: Int, x: Int, y: Int): ImageBitmap? = tiles["$z/$x/$y"]

    suspend fun load(z: Int, x: Int, y: Int) {
        val key = "$z/$x/$y"
        if (tiles.containsKey(key)) return
        val bitmap = TileSource.fetch(z, x, y) ?: return
        if (tiles.size >= MAX_TILES) tiles.clear()
        tiles[key] = bitmap
        version++
    }
}
