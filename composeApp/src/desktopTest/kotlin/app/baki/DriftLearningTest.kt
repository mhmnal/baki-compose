package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Learning the drift factor from shifts where the driver's car figure disagrees with the track. */
class DriftLearningTest {

    private fun sample(raw: Double, real: Double) = CalibrationSample(raw, real, "2026-11-07T00:00:00Z")

    @Test
    fun oneSampleIsNotEnough() {
        assertEquals(null, Calibration.suggestedFactor(listOf(sample(50.0, 65.0))))
    }

    @Test
    fun medianOfRecentSamplesWins() {
        // a single wild shift must not swing the model
        val f = Calibration.suggestedFactor(
            listOf(sample(50.0, 65.0), sample(40.0, 52.0), sample(30.0, 90.0)),
        )!!
        assertTrue(f in 1.29..1.31, "expected ~1.30, got $f")
    }

    @Test
    fun factorIsClamped() {
        val f = Calibration.suggestedFactor(listOf(sample(10.0, 40.0), sample(10.0, 42.0)))!!
        assertEquals(1.8, f)
    }
}
