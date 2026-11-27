package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklySetAsideTest {
    @Test fun setAsideIsRoundedAndBounded() {
        assertEquals(WeeklySetAside(20, 7_754, 31_016), weeklySetAside(38_770, 20))
        assertEquals(WeeklySetAside(0, 0, 38_770), weeklySetAside(38_770, 0))
        assertEquals(WeeklySetAside(50, 19_385, 19_385), weeklySetAside(38_770, 90))
    }
}
