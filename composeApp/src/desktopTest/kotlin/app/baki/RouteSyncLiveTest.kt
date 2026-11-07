package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opt-in end-to-end check against a running home server: seal a trace, upload it, pull it back and
 * open it. Skipped unless BAKI_LIVE_SYNC=1 so the normal suite never needs the network.
 *
 *   BAKI_LIVE_SYNC=1 BAKI_E2E_SHIFT=<shift id> ./gradlew :composeApp:desktopTest --tests "app.baki.RouteSyncLiveTest"
 */
class RouteSyncLiveTest {

    @Test
    fun sealedTraceSurvivesTheRoundTrip() = runBlocking {
        if (System.getenv("BAKI_LIVE_SYNC") != "1") return@runBlocking
        val shiftId = System.getenv("BAKI_E2E_SHIFT") ?: "e2e-shift"
        val secret = System.getenv("BAKI_E2E_KEY") ?: "e2e-key"
        val route = ShiftRoute(
            shiftId = shiftId,
            date = "2026-11-06",
            points = listOf(
                RoutePoint(3.1390, 101.6869, 1_760_000_000_000),
                RoutePoint(3.1420, 101.6900, 1_760_000_120_000),
                RoutePoint(3.1460, 101.6930, 1_760_000_240_000),
            ),
        )
        val plain = BakiJson.encodeToString(ShiftRoute.serializer(), route).encodeToByteArray()
        val blob = RouteBase64.encode(RouteCrypto.seal(secret, plain))

        val client = TrackerClient("http://127.0.0.1:8787")
        client.pushRoutes(listOf(RouteBlob(route.shiftId, route.date, blob)))
        val back = client.pullRoutes("2026-11-01").firstOrNull { it.shiftId == shiftId }
            ?: error("the server did not return the trace it accepted")
        val opened = BakiJson.decodeFromString(
            ShiftRoute.serializer(),
            RouteCrypto.open(secret, RouteBase64.decode(back.blob)).decodeToString(),
        )
        assertEquals(route, opened)
    }
}
