package app.baki

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * Read-only day map: every shift of the day on one canvas, each with its own polyline and its own
 * A → B markers, auto-fitted.
 *
 * Deliberately inert — no pan, zoom, tilt, rotate or tap handlers are installed, so it behaves as
 * a summary card rather than a map to fiddle with.
 */
@Composable
fun RouteMap(
    routes: List<MapRoute>,
    modifier: Modifier = Modifier,
    showAttribution: Boolean = true,
) {
    val baki = LocalBaki.current
    // subscribe to the tile cache so newly arrived tiles redraw the canvas
    val tileVersion = TileCache.version
    val measurer = rememberTextMeasurer()
    val allPoints = remember(routes) { routes.flatMap { it.route.points } }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(baki.surfaceHigh),
    ) {
        val sizePx = with(LocalDensity.current) {
            Size(maxWidth.toPx(), maxHeight.toPx())
        }
        val projection = remember(allPoints, sizePx) {
            MapProjection.fit(allPoints, sizePx)
        }

        LaunchedEffect(projection) {
            projection?.visibleTiles()?.forEach { (z, x, y) -> TileCache.load(z, x, y) }
        }

        Canvas(Modifier.fillMaxSize()) {
            val proj = projection ?: return@Canvas
            @Suppress("UNUSED_EXPRESSION")
            tileVersion // read so a tile arriving triggers a redraw

            // basemap
            for ((z, x, y) in proj.visibleTiles()) {
                val bitmap = TileCache.peek(z, x, y) ?: continue
                val topLeft = proj.tileTopLeft(x, y)
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(257, 257), // 1 px overlap hides sub-pixel seams between tiles
                )
            }
            // a faint veil keeps the paths readable over busy streets
            drawRect(baki.surface.copy(alpha = 0.18f))

            // routes: white casing then the colour, so lines read on any basemap
            routes.forEach { mapRoute ->
                val points = mapRoute.route.points
                if (points.size < 2) return@forEach
                val path = Path()
                val first = proj.project(points.first())
                path.moveTo(first.x, first.y)
                for (i in 1 until points.size) {
                    val q = proj.project(points[i])
                    path.lineTo(q.x, q.y)
                }
                drawPath(
                    path, Color.White.copy(alpha = 0.65f),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path, mapRoute.color,
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // A / B markers per shift
            routes.forEach { mapRoute ->
                mapRoute.route.start?.let { drawEndpoint(proj.project(it), mapRoute.color, "A", measurer) }
                mapRoute.route.end?.let { drawEndpoint(proj.project(it), mapRoute.color, "B", measurer) }
            }

            if (showAttribution) {
                val layout = measurer.measure(
                    AnnotatedString("© OpenStreetMap"),
                    TextStyle(color = baki.muted, fontSize = 8.sp),
                )
                val pad = 6.dp.toPx()
                drawRect(
                    color = baki.surface.copy(alpha = 0.72f),
                    topLeft = Offset(pad, size.height - layout.size.height - pad * 1.6f),
                    size = Size(layout.size.width + pad, layout.size.height + pad * 0.6f),
                )
                drawText(
                    layout,
                    topLeft = Offset(pad + pad * 0.5f, size.height - layout.size.height - pad * 1.2f),
                )
            }
        }
    }
}

private fun DrawScope.drawEndpoint(
    center: Offset,
    color: Color,
    label: String,
    measurer: TextMeasurer,
) {
    drawCircle(Color.White, radius = 8.dp.toPx(), center = center)
    drawCircle(color, radius = 6.dp.toPx(), center = center)
    val layout = measurer.measure(
        AnnotatedString(label),
        TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold),
    )
    drawText(
        layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f,
        ),
    )
}

/** One day's worth of routes, as a card you can open full-screen. */
@Composable
fun DayMapCard(
    date: String,
    routes: List<MapRoute>,
    onOpen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val baki = LocalBaki.current
    val km = routes.sumOf { routeMetres(it.route.points) } / 1000.0
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(baki.container)
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(prettyDate(date), color = baki.onSurface, style = BakiType.bodyStrong)
                val shifts = routes.size
                Text(
                    buildString {
                        append(if (shifts == 1) "1 shift" else "$shifts shifts")
                        append(" · ")
                        append("%.1f km".format(km))
                    },
                    color = baki.muted, style = BakiType.captionNumeric,
                )
            }
            if (onOpen != null) {
                Text("Open", color = baki.primary, style = BakiType.labelStrong)
            }
        }
        RouteMap(
            routes = routes,
            modifier = Modifier.fillMaxWidth().height(168.dp),
            showAttribution = true,
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** Full-screen read-only day map. Still inert: it is a bigger card, not an interactive map. */
@Composable
fun DayMapFullScreen(date: String, routes: List<MapRoute>, onDismiss: () -> Unit) {
    val baki = LocalBaki.current
    val km = routes.sumOf { routeMetres(it.route.points) } / 1000.0
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(baki.surface).padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(prettyDate(date), color = baki.onSurface, style = BakiType.headline)
                        Text(
                            "${routes.size} shift${if (routes.size == 1) "" else "s"} · %.1f km".format(km),
                            color = baki.muted, style = BakiType.captionNumeric,
                        )
                    }
                    Text(
                        "Close", color = baki.primary, style = BakiType.labelStrong,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                RouteMap(routes = routes, modifier = Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(12.dp))
                // legend: which colour is which shift
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    routes.forEach { mapRoute ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(Radius.pill)).background(mapRoute.color))
                            Spacer(Modifier.width(8.dp))
                            Text(mapRoute.label, color = baki.muted, style = BakiType.caption)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "%02d:%02d – %02d:%02d".format(
                                    clockHour(mapRoute.route.start), clockMinute(mapRoute.route.start),
                                    clockHour(mapRoute.route.end), clockMinute(mapRoute.route.end),
                                ),
                                color = baki.muted, style = BakiType.captionNumeric,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun clockHour(p: RoutePoint?): Int = p?.let { ((it.t / 3_600_000L) % 24).toInt() } ?: 0
private fun clockMinute(p: RoutePoint?): Int = p?.let { ((it.t / 60_000L) % 60).toInt() } ?: 0
