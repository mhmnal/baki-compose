package app.baki

import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Route maths, simplification and the encrypted route store. */
class RouteTest {

    private fun sample(n: Int = 200, lat: Double = 3.14, lon: Double = 101.69): List<RoutePoint> =
        (0 until n).map { i ->
            RoutePoint(
                lat = lat + i * 0.00045,          // ~50 m per step north
                lon = lon + i * 0.00020,
                t = 1_700_000_000_000 + i * 2_000L,
                speed = 12.0,
            )
        }

    @Test
    fun straightLineSimplifiesToItsEndpoints() {
        val line = (0 until 50).map { i ->
            RoutePoint(3.14 + i * 0.0001, 101.69, 1_700_000_000_000 + i * 2_000L)
        }
        val simplified = simplifyRoute(line)
        assertEquals(2, simplified.size)
        assertEquals(line.first(), simplified.first())
        assertEquals(line.last(), simplified.last())
    }

    @Test
    fun simplificationKeepsCorners() {
        val corner = listOf(
            RoutePoint(3.1400, 101.6900, 0),
            RoutePoint(3.1400, 101.6950, 1_000),   // 500 m east
            RoutePoint(3.1450, 101.6950, 2_000),   // 500 m north
        )
        val simplified = simplifyRoute(corner)
        assertEquals(3, simplified.size, "a right angle is not a straight line")
    }

    @Test
    fun routeLengthIsAboutRight() {
        // 1 degree of latitude is ~111.3 km
        val a = RoutePoint(3.0, 101.0, 0)
        val b = RoutePoint(4.0, 101.0, 1_000)
        val km = routeMetres(listOf(a, b)) / 1000.0
        assertTrue(abs(km - 111.3) < 1.5, "expected ~111.3 km, was $km")
    }

    @Test
    fun projectionFitsEveryPointInsideTheCanvas() {
        val points = sample()
        val size = Size(900f, 420f)
        val projection = mapProjectionForTest(points, size)
        assertNotNull(projection)
        points.forEach { p ->
            val q = projection.project(p)
            assertTrue(q.x in 0f..size.width, "x out of bounds: ${q.x}")
            assertTrue(q.y in 0f..size.height, "y out of bounds: ${q.y}")
        }
    }

    @Test
    fun aStandstillDoesNotDivideByZero() {
        val one = listOf(RoutePoint(3.14, 101.69, 0))
        val projection = mapProjectionForTest(one, Size(900f, 420f))
        assertNotNull(projection, "a single fix still needs a map")
    }

    @Test
    fun storeRoundTripsAppendSimplifyAndDelete() = runBlocking {
        val id = "test-route-${nowMillis()}"
        val date = todayIso()
        RouteStore.append(id, date, sample(30).take(10))
        RouteStore.append(id, date, sample(30).drop(10))
        val stored = RouteStore.route(id)
        assertNotNull(stored)
        assertEquals(30, stored.points.size)
        assertEquals(date, stored.date)

        RouteStore.finalize(id)
        val simplified = RouteStore.route(id)
        assertNotNull(simplified)
        assertTrue(simplified.points.size in 2..30, "simplification should not grow the route")
        assertEquals(stored.points.first(), simplified.points.first())
        assertEquals(stored.points.last(), simplified.points.last())

        assertTrue(RouteStore.routesForDate(date).any { it.shiftId == id })

        RouteStore.delete(id)
        assertEquals(null, RouteStore.route(id))
    }

    private fun mapProjectionForTest(points: List<RoutePoint>, size: Size): MapProjection? {
        // MapProjection.fit is the production entry point
        return MapProjection.fit(points, size)
    }
}
