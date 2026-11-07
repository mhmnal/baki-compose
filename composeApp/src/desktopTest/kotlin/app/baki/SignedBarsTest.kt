package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedBarsTest {

    @Test
    fun oneSharedScaleAcrossBothDirections() {
        // −60.70 must read as about half of +115.63, not as its own full-height bar
        val gain = SignedBars.of(11_563, peakPositive = 11_563, peakNegative = 6_070)
        val loss = SignedBars.of(-6_070, peakPositive = 11_563, peakNegative = 6_070)
        assertEquals(1f, gain.fraction, 0.001f)
        assertEquals(0.525f, loss.fraction, 0.005f)
        assertTrue(gain.positive)
        assertFalse(loss.positive)
    }

    @Test
    fun zeroLineSitsWhereThePeaksMeet() {
        val z = SignedBars.zeroFraction(peakPositive = 11_563, peakNegative = 6_070)
        assertEquals(0.656f, z, 0.002f)
        // no losses: the baseline belongs at the bottom
        assertEquals(1f, SignedBars.zeroFraction(10, 0), 0.001f)
        // no gains: at the top
        assertEquals(0f, SignedBars.zeroFraction(0, 10), 0.001f)
    }

    @Test
    fun zeroIsFlat() {
        val flat = SignedBars.of(0, 1, 1)
        assertTrue(flat.zero)
        assertEquals(0f, flat.fraction)
    }
}
