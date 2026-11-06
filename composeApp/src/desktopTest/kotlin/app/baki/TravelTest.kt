package app.baki

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelTest {

    @Test
    fun stopGoTrafficStillCounts() {
        // 2 s at 1.2 m/s (4 km/h) moves only ~2.4 m, under a fixed 3 m gate, but the car is moving
        val d = travelMetres(stepMeters = 2.0, seconds = 2.0, accuracyMeters = 12.0, speedMps = 1.2, hasSpeed = true)
        assertTrue(abs(d - 2.4) < 0.01, "expected 2.4 m from speed, got $d")
    }

    @Test
    fun stationaryJitterIsIgnored() {
        // parked, with a 25 m accuracy fix wandering 5 m: neither signal says "moving"
        val d = travelMetres(stepMeters = 5.0, seconds = 2.0, accuracyMeters = 25.0, speedMps = 0.0, hasSpeed = true)
        assertEquals(0.0, d)
    }

    @Test
    fun cityDrivingUsesPosition() {
        // 2 s at 27 km/h = 15 m, accuracy 8 m -> noise floor 2 m, position wins
        val d = travelMetres(stepMeters = 15.0, seconds = 2.0, accuracyMeters = 8.0, speedMps = 7.5, hasSpeed = true)
        assertEquals(15.0, d)
    }

    @Test
    fun highwayIsNotCapped() {
        val d = travelMetres(stepMeters = 33.0, seconds = 2.0, accuracyMeters = 5.0, speedMps = 16.5, hasSpeed = true)
        assertEquals(33.0, d)
    }

    @Test
    fun badSpeedSampleCannotInventTravel() {
        // 30 m/s claimed over 2 s while the position only moved 2 m: cap keeps it honest
        val d = travelMetres(stepMeters = 2.0, seconds = 2.0, accuracyMeters = 20.0, speedMps = 30.0, hasSpeed = true)
        assertTrue(d <= 17.0, "cap should limit invented travel, got $d")
    }

    @Test
    fun noElapsedTimeMeansNothing() {
        assertEquals(0.0, travelMetres(5.0, 0.0, 10.0, 3.0, true))
    }

    @Test
    fun missingSpeedFallsBackToPosition() {
        val d = travelMetres(stepMeters = 12.0, seconds = 2.0, accuracyMeters = 10.0, speedMps = 0.0, hasSpeed = false)
        assertEquals(12.0, d)
    }
}
