package app.baki

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.random.Random

/**
 * A very quiet paper tooth. It is a tiny static tile, not a shader or an animated overlay:
 * cheap to composite, invisible over text, and only present in the Paper palette.
 */
private const val GRAIN_TILE = 64

private fun grainTile(dark: Boolean): ImageBitmap {
    val tile = ImageBitmap(GRAIN_TILE, GRAIN_TILE)
    val canvas = Canvas(tile)
    val paint = Paint().apply { isAntiAlias = true }
    val random = Random(0xBA61)
    repeat(420) {
        val alpha = if (dark) 0.009f + random.nextFloat() * 0.015f
        else 0.005f + random.nextFloat() * 0.008f
        paint.color = if (dark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
        canvas.drawCircle(
            Offset(
                random.nextFloat() * GRAIN_TILE,
                random.nextFloat() * GRAIN_TILE,
            ),
            0.55f + random.nextFloat() * 0.65f,
            paint,
        )
    }
    return tile
}

@Composable
fun PaperGrain(modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    if (!baki.paper) return
    val tile = remember(baki.dark) { grainTile(baki.dark) }
    val brush = remember(tile) {
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }
    Box(
        modifier.drawBehind {
            drawRect(brush = brush, alpha = if (baki.dark) 0.45f else 0.32f)
        },
    )
}
