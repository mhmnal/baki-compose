package app.baki

import androidx.compose.ui.graphics.Color

/** Neutral grey, reserved for the catch-all category so it never competes with a real hue. */
val ChartGrey = Color(0xFF8A9099)

/**
 * Category colours: a distinct ramp of five hues plus grey for "Other". Assigned by position, so two
 * categories in one chart can never share a colour, whatever the palette.
 */
fun chartColors(p: BakiPalette, ids: List<String>): List<Color> {
    val hues = if (p.dark) listOf(
        p.primary,
        Color(0xFF63C9C0),
        Color(0xFFE0B25C),
        p.down,
        Color(0xFFB9A7F0),
        Color(0xFFE58FB8),
    ) else listOf(
        p.primary,
        Color(0xFF2F7D7A),
        Color(0xFFA9761F),
        p.down,
        Color(0xFF6C5B9E),
        Color(0xFFA64E7A),
    )
    var next = 0
    return ids.map { id -> if (id == "other") ChartGrey else hues[next++ % hues.size] }
}
