package app.baki

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** One recorded fix while a shift is running. */
@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    /** epoch millis of the fix */
    val t: Long,
    /** metres per second reported by the provider, 0 when unavailable */
    val speed: Double = 0.0,
)

/** The path of one shift. Routes are local-only and never synced. */
@Serializable
data class ShiftRoute(
    val shiftId: String,
    val date: String,
    val points: List<RoutePoint> = emptyList(),
) {
    val start: RoutePoint? get() = points.firstOrNull()
    val end: RoutePoint? get() = points.lastOrNull()
}

/** A route plus how it should be drawn on a shared day map. */
data class MapRoute(val route: ShiftRoute, val color: Color, val label: String)

// ------------------------------------------------------------------ geometry

/** Web-Mercator projection that fits a set of points into a canvas. */
class MapProjection(
    val zoom: Int,
    val centerWorld: Offset,
    val size: Size,
) {
    private val tiles = 256.0 * 2.0.pow(zoom)

    fun world(lat: Double, lon: Double): Offset {
        val x = (lon + 180.0) / 360.0 * tiles
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clamped * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * tiles
        return Offset(x.toFloat(), y.toFloat())
    }

    fun project(p: RoutePoint): Offset {
        val w = world(p.lat, p.lon)
        return Offset(
            (w.x - centerWorld.x + size.width / 2f),
            (w.y - centerWorld.y + size.height / 2f),
        )
    }

    /** Tile coordinates visible in this viewport, at this zoom. */
    fun visibleTiles(): List<Triple<Int, Int, Int>> {
        val left = centerWorld.x - size.width / 2f
        val top = centerWorld.y - size.height / 2f
        val firstX = floor(left / 256f).toInt()
        val firstY = floor(top / 256f).toInt()
        val lastX = floor((left + size.width) / 256f).toInt()
        val lastY = floor((top + size.height) / 256f).toInt()
        val max = (1 shl zoom) - 1
        val out = ArrayList<Triple<Int, Int, Int>>((lastX - firstX + 1) * (lastY - firstY + 1))
        for (x in firstX..lastX) {
            for (y in firstY..lastY) {
                if (x in 0..max && y in 0..max) out += Triple(zoom, x, y)
            }
        }
        return out
    }

    /** Screen position of a tile's top-left corner. */
    fun tileTopLeft(x: Int, y: Int): Offset = Offset(
        x * 256f - (centerWorld.x - size.width / 2f),
        y * 256f - (centerWorld.y - size.height / 2f),
    )

    companion object {
        /** Picks the largest zoom that keeps every point inside [size] minus padding. */
        fun fit(
            points: List<RoutePoint>,
            size: Size,
            paddingPx: Float = 18f,
        ): MapProjection? {
            if (points.isEmpty() || size.width <= 1f || size.height <= 1f) return null
            var minLat = points.first().lat
            var maxLat = minLat
            var minLon = points.first().lon
            var maxLon = minLon
            for (p in points) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lon < minLon) minLon = p.lon
                if (p.lon > maxLon) maxLon = p.lon
            }
            val usableW = (size.width - paddingPx * 2f).coerceAtLeast(32f)
            val usableH = (size.height - paddingPx * 2f).coerceAtLeast(32f)

            // A single point (or a standstill) would divide by zero: keep a street-level default.
            val lonSpan = abs(maxLon - minLon).coerceAtLeast(1e-4)
            val latSpan = abs(maxLat - minLat).coerceAtLeast(1e-4)

            var zoom = 18
            while (zoom > 3) {
                val probe = MapProjection(zoom, Offset.Zero, size)
                val a = probe.world(minLat, minLon)
                val b = probe.world(maxLat, maxLon)
                // Mercator y grows southwards, so the latitude delta is negative: compare spans,
                // not raw differences, or the fit accepts a zoom that overflows the canvas.
                val spanX = abs(b.x - a.x)
                val spanY = abs(b.y - a.y)
                if (spanX <= usableW && spanY <= usableH) break
                zoom--
            }
            val probe = MapProjection(zoom, Offset.Zero, size)
            val a = probe.world(minLat, minLon)
            val b = probe.world(maxLat, maxLon)
            val center = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            return MapProjection(zoom, center, size)
        }
    }
}

/** Screen-space bounding box of a projected route. */
fun MapProjection.boundsOf(points: List<RoutePoint>): Rect {
    if (points.isEmpty()) return Rect.Zero
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in points) {
        val q = project(p)
        if (q.x < minX) minX = q.x
        if (q.y < minY) minY = q.y
        if (q.x > maxX) maxX = q.x
        if (q.y > maxY) maxY = q.y
    }
    return Rect(minX, minY, maxX, maxY)
}

// ------------------------------------------------------------- simplification

/** Douglas–Peucker in metres, using a local equirectangular approximation. */
fun simplifyRoute(points: List<RoutePoint>, epsilonMetres: Double = 4.0): List<RoutePoint> {
    if (points.size <= 2) return points
    val lat0 = points.first().lat
    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * cos(lat0 * PI / 180.0)

    fun x(p: RoutePoint) = p.lon * mPerDegLon
    fun y(p: RoutePoint) = p.lat * mPerDegLat

    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true

    val stack = ArrayDeque<IntRange>()
    stack.addLast(0..points.lastIndex)
    while (stack.isNotEmpty()) {
        val range = stack.removeLast()
        if (range.last - range.first < 2) continue
        val a = points[range.first]
        val b = points[range.last]
        val ax = x(a); val ay = y(a)
        val bx = x(b); val by = y(b)
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy

        var bestIndex = -1
        var bestDist = 0.0
        for (i in range.first + 1 until range.last) {
            val p = points[i]
            val px = x(p); val py = y(p)
            val dist = if (lengthSq < 1e-9) {
                val ex = px - ax; val ey = py - ay
                ex * ex + ey * ey
            } else {
                val t = ((px - ax) * dx + (py - ay) * dy) / lengthSq
                val tc = t.coerceIn(0.0, 1.0)
                val ex = px - (ax + tc * dx)
                val ey = py - (ay + tc * dy)
                ex * ex + ey * ey
            }
            if (dist > bestDist) { bestDist = dist; bestIndex = i }
        }
        if (bestIndex > 0 && bestDist > epsilonMetres * epsilonMetres) {
            keep[bestIndex] = true
            stack.addLast(range.first..bestIndex)
            stack.addLast(bestIndex..range.last)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Rough length of a route in metres, for captions. */
fun routeMetres(points: List<RoutePoint>): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val dLat = (b.lat - a.lat) * PI / 180.0
        val dLon = (b.lon - a.lon) * PI / 180.0
        val la1 = a.lat * PI / 180.0
        val la2 = b.lat * PI / 180.0
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(la1) * cos(la2) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        total += 2 * 6_371_000.0 * atan(kotlin.math.sqrt(h) / kotlin.math.sqrt(1 - h))
    }
    return total
}
