package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyFoodTest {
    @Test
    fun dailyFoodOnlyCountsFoodOnTheDayAndAgainstTheCap() {
        val store = Store(
            settings = Settings(dailyFoodCapCents = 2_500),
            entries = listOf(
                Entry(id = "a", date = "2026-11-12", cat = "food", amount = 1_800),
                Entry(id = "b", date = "2026-11-12", cat = "food", amount = 900),
                Entry(id = "c", date = "2026-11-12", cat = "fuel", amount = 5_000),
                Entry(id = "d", date = "2026-11-11", cat = "food", amount = 400),
            ),
        )
        val f = dailyFood(store, "2026-11-12")!!
        assertEquals(2_700, f.spent)
        assertEquals(2_500, f.cap)
        assertEquals(-200, f.remaining)
        assertTrue(f.over)
    }

    @Test
    fun noCapMeansNoNoise() {
        assertNull(dailyFood(Store(entries = listOf(Entry(id = "a", date = "2026-11-12", cat = "food", amount = 100))), "2026-11-12"))
    }
}
