package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShiftYieldTest {
    @Test
    fun grossPerKmAndTargetGapAreHonest() {
        val m = shiftMetrics(
            Shift(id = "s", date = "2026-11-12", start = "14:13", hours = 4.0, km = 100.0, gross = 9_880, grabFee = 750),
            Profit(fuelPrice = 1.99, kmPerLitre = 11.0, wearPerKm = 0.09, avgSpeed = 30.0, grabInsurance = 7.5, targetRate = 18.0),
        )
        assertNotNull(m.grossPerKm)
        assertEquals(0.988, m.grossPerKm!!, 0.0001)
        assertEquals(18.0, m.netHr!! + (grossToClearTarget(m, 18.0)!! - m.gross) / m.hrs, 0.0001)
    }
}
