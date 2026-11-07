package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedBarsTest {

    @Test
    fun lossesHangBelowTheZeroLine() {
        val loss = SignedBars.of(-6070, peakPositive = 11563, peakNegative = 6070)
        assertFalse(loss.positive, "a negative day must be drawn below zero")
        assertEquals(1f, loss.fraction, 0.001f)
    }

    @Test
    fun gainsRiseAboveIt() {
        val gain = SignedBars.of(9210, peakPositive = 11563, peakNegative = 6070)
        assertTrue(gain.positive)
        assertTrue(gain.fraction > 0.7f)
    }

    @Test
    fun scaleIsIndependentPerSide() {
        // a small gain and a small loss should look proportional to their own side, not to each other
        val gain = SignedBars.of(100, peakPositive = 10_000, peakNegative = 1_000)
        val loss = SignedBars.of(-500, peakPositive = 10_000, peakNegative = 1_000)
        assertEquals(0.01f, gain.fraction, 0.001f)
        assertEquals(0.5f, loss.fraction, 0.001f)
    }

    @Test
    fun zeroIsFlat() {
        val flat = SignedBars.of(0, 1, 1)
        assertTrue(flat.zero)
        assertEquals(0f, flat.fraction)
    }
}
