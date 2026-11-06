package app.baki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon set built from Material Symbols Rounded geometry (Apache-2.0), compiled to ImageVectors so
 * the same icons work on Android and desktop. Rounded matches the app's own shape language.
 */
object BakiIcons {
    val Today: ImageVector = build("today",
        "M19 3h-1V2c0-.55-.45-1-1-1s-1 .45-1 1v1H8V2c0-.55-.45-1-1-1s-1 .45-1 1v1H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-1 16H6c-.55 0-1-.45-1-1V8h14v10c0 .55-.45 1-1 1zM8 10h3c.55 0 1 .45 1 1v3c0 .55-.45 1-1 1H8c-.55 0-1-.45-1-1v-3c0-.55.45-1 1-1z",
    )
    val Car: ImageVector = build("car",
        "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01l-1.97 5.67c-.07.21-.11.43-.11.66v7.16c0 .83.67 1.5 1.5 1.5S6 20.33 6 19.5V19h12v.5c0 .82.67 1.5 1.5 1.5.82 0 1.5-.67 1.5-1.5v-7.16c0-.22-.04-.45-.11-.66l-1.97-5.67zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.27-3.82c.14-.4.52-.68.95-.68h9.56c.43 0 .81.28.95.68L19 11H5z",
    )
    val Insights: ImageVector = build("insights",
        "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM8 17c-.55 0-1-.45-1-1v-5c0-.55.45-1 1-1s1 .45 1 1v5c0 .55-.45 1-1 1zm4 0c-.55 0-1-.45-1-1V8c0-.55.45-1 1-1s1 .45 1 1v8c0 .55-.45 1-1 1zm4 0c-.55 0-1-.45-1-1v-2c0-.55.45-1 1-1s1 .45 1 1v2c0 .55-.45 1-1 1z",
    )
    val More: ImageVector = build("more",
        "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
    )
    val Fuel: ImageVector = build("fuel",
        "m19.77 7.23.01-.01-3.19-3.19c-.29-.29-.77-.29-1.06 0-.29.29-.29.77 0 1.06l1.58 1.58c-1.05.4-1.76 1.47-1.58 2.71.16 1.1 1.1 1.99 2.2 2.11.47.05.88-.03 1.27-.2v7.21c0 .55-.45 1-1 1s-1-.45-1-1V14c0-1.1-.9-2-2-2h-1V5c0-1.1-.9-2-2-2H6c-1.1 0-2 .9-2 2v15c0 .55.45 1 1 1h8c.55 0 1-.45 1-1v-6.5h1.5v4.86c0 1.31.94 2.5 2.24 2.63 1.5.15 2.76-1.02 2.76-2.49V9c0-.69-.28-1.32-.73-1.77zM12 10H6V6c0-.55.45-1 1-1h4c.55 0 1 .45 1 1v4zm6 0c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z",
    )
    val Parking: ImageVector = build("parking",
        "M12.79 3H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2s2-.9 2-2v-4h3c3.57 0 6.42-3.13 5.95-6.79C18.56 5.19 15.84 3 12.79 3zm.41 8H10V7h3.2c1.1 0 2 .9 2 2s-.9 2-2 2z",
    )
    val Wash: ImageVector = build("wash",
        "M17 5c.83 0 1.5-.67 1.5-1.5 0-.66-.66-1.64-1.11-2.22-.2-.26-.59-.26-.79 0-.44.58-1.1 1.56-1.1 2.22 0 .83.67 1.5 1.5 1.5zm-5 0c.83 0 1.5-.67 1.5-1.5 0-.66-.66-1.64-1.11-2.22-.2-.26-.59-.26-.79 0-.44.58-1.1 1.56-1.1 2.22 0 .83.67 1.5 1.5 1.5zM7 5c.83 0 1.5-.67 1.5-1.5 0-.66-.66-1.64-1.11-2.22-.2-.26-.59-.26-.79 0-.44.58-1.1 1.56-1.1 2.22C5.5 4.33 6.17 5 7 5zm11.92 3.01C18.72 7.42 18.16 7 17.5 7h-11c-.66 0-1.21.42-1.42 1.01l-1.97 5.67c-.07.21-.11.43-.11.66v7.16c0 .83.67 1.5 1.5 1.5S6 22.33 6 21.5V21h12v.5c0 .82.67 1.5 1.5 1.5.82 0 1.5-.67 1.5-1.5v-7.16c0-.22-.04-.45-.11-.66l-1.97-5.67zM6.5 18c-.83 0-1.5-.67-1.5-1.5S5.67 15 6.5 15s1.5.67 1.5 1.5S7.33 18 6.5 18zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 13l1.27-3.82c.14-.4.52-.68.95-.68h9.56c.43 0 .81.28.95.68L19 13H5z",
    )
    val Tool: ImageVector = build("tool",
        "M12.09 2.91C10.08.9 7.07.49 4.65 1.67L8.28 5.3c.39.39.39 1.02 0 1.41L6.69 8.3c-.39.4-1.02.4-1.41 0L1.65 4.67C.48 7.1.89 10.09 2.9 12.1c1.86 1.86 4.58 2.35 6.89 1.48l7.96 7.96c1.03 1.03 2.69 1.03 3.71 0 1.03-1.03 1.03-2.69 0-3.71L13.54 9.9c.92-2.34.44-5.1-1.45-6.99z",
    )
    val Doc: ImageVector = build("doc",
        "M14.59 2.59c-.38-.38-.89-.59-1.42-.59H6c-1.1 0-2 .9-2 2v16c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8.83c0-.53-.21-1.04-.59-1.41l-4.82-4.83zM15 18H9c-.55 0-1-.45-1-1s.45-1 1-1h6c.55 0 1 .45 1 1s-.45 1-1 1zm0-4H9c-.55 0-1-.45-1-1s.45-1 1-1h6c.55 0 1 .45 1 1s-.45 1-1 1zm-2-6V3.5L18.5 9H14c-.55 0-1-.45-1-1z",
    )
    val Phone: ImageVector = build("phone",
        "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z",
    )
    val Food: ImageVector = build("food",
        "M16 6v6c0 1.1.9 2 2 2h1v7c0 .55.45 1 1 1s1-.45 1-1V3.13c0-.65-.61-1.13-1.24-.98C17.6 2.68 16 4.51 16 6zm-5 3H9V3c0-.55-.45-1-1-1s-1 .45-1 1v6H5V3c0-.55-.45-1-1-1s-1 .45-1 1v6c0 2.21 1.79 4 4 4v8c0 .55.45 1 1 1s1-.45 1-1v-8c2.21 0 4-1.79 4-4V3c0-.55-.45-1-1-1s-1 .45-1 1v6z",
    )
    val Tag: ImageVector = build("tag",
        "M17.63 5.84C17.27 5.33 16.67 5 16 5L5 5.01C3.9 5.01 3 5.9 3 7v10c0 1.1.9 1.99 2 1.99L16 19c.67 0 1.27-.33 1.63-.84l3.96-5.58c.25-.35.25-.81 0-1.16l-3.96-5.58z",
    )
    val Taxi: ImageVector = build("taxi",
        "M18.92 6.01C18.72 5.42 18.16 5 17.5 5H15V4c0-.55-.45-1-1-1h-4c-.55 0-1 .45-1 1v1H6.5c-.66 0-1.21.42-1.42 1.01l-1.97 5.67c-.07.21-.11.43-.11.66v7.16c0 .83.67 1.5 1.5 1.5S6 20.33 6 19.5V19h12v.5c0 .82.67 1.5 1.5 1.5.82 0 1.5-.67 1.5-1.5v-7.16c0-.22-.04-.45-.11-.66l-1.97-5.67zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z",
    )
    val Wallet: ImageVector = build("wallet",
        "M10 16V8c0-1.1.89-2 2-2h9V5c0-1.1-.9-2-2-2H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-1h-9c-1.11 0-2-.9-2-2zm3-8c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h9V8h-9zm3 5.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
    )
    val Trend: ImageVector = build("trend",
        "M16.85 6.85l1.44 1.44-4.88 4.88-3.29-3.29c-.39-.39-1.02-.39-1.41 0l-6 6.01c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0L9.41 12l3.29 3.29c.39.39 1.02.39 1.41 0l5.59-5.58 1.44 1.44c.31.31.85.09.85-.35V6.5c.01-.28-.21-.5-.49-.5h-4.29c-.45 0-.67.54-.36.85z",
    )

    val Mic: ImageVector = build("mic",
        "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.91-3c-.49 0-.9.36-.98.85C16.52 14.2 14.47 16 12 16s-4.52-1.8-4.93-4.15c-.08-.49-.49-.85-.98-.85-.61 0-1.09.54-1 1.14.49 3 2.89 5.35 5.91 5.78V20c0 .55.45 1 1 1s1-.45 1-1v-2.08c3.02-.43 5.42-2.78 5.91-5.78.1-.6-.39-1.14-1-1.14z",
    )
    val ArrowRight: ImageVector = build("arrow_right",
        "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z",
    )

    val Receipt: ImageVector = build("receipt",
        "M21 2.21c-.13 0-.26.05-.35.15l-.79.79c-.2.2-.51.2-.71 0l-.79-.79c-.2-.2-.51-.2-.71 0l-.79.79c-.2.2-.51.2-.71 0l-.79-.79c-.2-.2-.51-.2-.71 0l-.79.79c-.2.2-.51.2-.71 0l-.79-.79c-.2-.2-.51-.2-.71 0l-.79.79c-.2.2-.51.2-.71 0l-.8-.8c-.2-.2-.51-.2-.71 0l-.79.8c-.2.2-.51.2-.71 0l-.79-.8c-.2-.2-.51-.2-.71 0l-.79.8c-.2.2-.51.2-.71 0l-.79-.8c-.09-.09-.22-.14-.35-.14V21.8c.13 0 .26-.05.35-.15l.79-.79c.2-.2.51-.2.71 0l.79.79c.2.2.51.2.71 0l.79-.79c.2-.2.51-.2.71 0l.79.79c.2.2.51.2.71 0l.79-.79c.2-.2.51-.2.71 0l.79.79c.2.2.51.2.71 0l.79-.79c.2-.2.51-.2.71 0l.79.79c.2.2.51.2.71 0l.79-.79c.2-.2.51-.2.71 0l.79.79c.2.2.51.2.71 0l.79-.79c.2-.2.51-.2.71 0l.79.79c.1.1.23.15.35.15V2.21zM17 17H7c-.55 0-1-.45-1-1s.45-1 1-1h10c.55 0 1 .45 1 1s-.45 1-1 1zm0-4H7c-.55 0-1-.45-1-1s.45-1 1-1h10c.55 0 1 .45 1 1s-.45 1-1 1zm0-4H7c-.55 0-1-.45-1-1s.45-1 1-1h10c.55 0 1 .45 1 1s-.45 1-1 1z",
    )

    val Shield: ImageVector = build("shield",
        "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z",
    )

    /** A toll gate: barrier arm on a post, drawn to match the other glyphs' weight. */
    val Gate: ImageVector = build("gate","M3 9h11a2 2 0 0 1 0 4H3a2 2 0 0 1 0-4zm0 1.4a.6.6 0 0 0 0 1.2h11a.6.6 0 0 0 0-1.2H3z",
        "M16.5 4a1.5 1.5 0 0 1 1.5 1.5v4h-2v-4H16a1.5 1.5 0 0 1 .5-.03z",
        "M17 12h2v9h-2z",
    )

    private fun build(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
        return builder.build()
    }
}

/** Categories are data, so map whatever id we find onto the closest glyph. */
fun categoryIcon(id: String, name: String = ""): ImageVector {
    val key = (id + " " + name).lowercase()
    return when {
        "fuel" in key || "petrol" in key || "gas" in key -> BakiIcons.Fuel
        "toll" in key -> BakiIcons.Gate
        "park" in key -> BakiIcons.Parking
        "wash" in key || "clean" in key -> BakiIcons.Wash
        "maintenance" in key || "service" in key || "repair" in key -> BakiIcons.Tool
        "insurance" in key || "cover" in key -> BakiIcons.Shield
        "roadtax" in key || "road tax" in key || "tax" in key -> BakiIcons.Doc
        "phone" in key || "data" in key || "sim" in key -> BakiIcons.Phone
        "food" in key || "meal" in key || "eat" in key -> BakiIcons.Food
        "grab" in key || "fee" in key -> BakiIcons.Taxi
        "income" in key || "payout" in key -> BakiIcons.Wallet
        else -> BakiIcons.Tag
    }
}
