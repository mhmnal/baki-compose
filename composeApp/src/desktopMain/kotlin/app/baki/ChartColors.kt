package app.baki

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

fun chartColors(p: ColorScheme, ids: List<String>): List<Color> {
    val hues = listOf(p.primary, p.tertiary, p.secondary, p.error, p.primaryContainer, p.tertiaryContainer)
    var next = 0
    return ids.map { id -> if (id == "other") p.outline else hues[next++ % hues.size] }
}
