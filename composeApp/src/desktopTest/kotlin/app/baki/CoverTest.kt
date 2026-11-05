package app.baki

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cover rule must be *idempotent* on the real store: the web app already computed these
 * fees and fee-* entries, so re-deriving them in Kotlin must want to change nothing.
 */
class CoverTest {

    private fun resource(name: String) =
        CoverTest::class.java.classLoader.getResourceAsStream(name)?.bufferedReader()?.readText()
            ?: error("missing $name")

    private val store: Store by lazy { BakiJson.decodeFromString<Store>(resource("store.json")) }
    private val tz = TimeZone.currentSystemDefault()

    @Test
    fun reDerivingTheRealStoreChangesNothing() {
        val (updated, changed) = recomputeCover(store)
        assertFalse(changed, "recompute wanted to change the real store — the port disagrees with the app")
        assertEquals(store.shifts.map { it.grabFee }, updated.shifts.map { it.grabFee })
        assertEquals(store.entries.size, updated.entries.size)
    }

    @Test
    fun theRuleIsA24HourWindow() {
        // 1 Nov 14:00 charged, 2 Nov 13:30 is 23.5h later → free, 3 Nov 17:00 → charged
        val a = Shift(id = "a", date = "2026-11-01", start = "14:00", hours = 4.0, gross = 14026)
        val b = Shift(id = "b", date = "2026-11-02", start = "13:30", hours = 6.0, gross = 16495)
        val c = Shift(id = "c", date = "2026-11-03", start = "17:00", hours = 3.0, gross = 7568)
        assertEquals(setOf("a", "c"), coverChargedIds(listOf(a, b, c), tz))
    }

    @Test
    fun exactlyTwentyFourHoursIsCharged() {
        val a = Shift(id = "a", date = "2026-11-01", start = "15:00", hours = 4.0)
        val b = Shift(id = "b", date = "2026-11-02", start = "15:00", hours = 4.0)
        assertEquals(setOf("a", "b"), coverChargedIds(listOf(a, b), tz))
    }

    @Test
    fun aNewShiftOutsideTheWindowWouldBeCharged() {
        val store = store
        // 4 Nov 03:30 is inside the 3 Nov 17:00 window; 5 Nov 06:00 is not
        assertEquals(0L, draftCoverCharge(store, "2026-11-04", "03:30"))
        assertTrue(draftCoverCharge(store, "2026-11-06", "06:00") > 0L)
    }

    @Test
    fun coverProducesTheLinkedExpenseEntry() {
        val bare = store.copy(
            entries = emptyList(),
            shifts = listOf(Shift(id = "s1", date = "2026-11-01", start = "14:00", hours = 4.0, gross = 10000)),
        )
        val (updated, changed) = recomputeCover(bare)
        assertTrue(changed)
        val fee = updated.entries.first { it.id == "fee-s1" }
        assertEquals("grabfee", fee.cat)
        assertEquals(750L, fee.amount)
        assertEquals("2026-11-01", fee.date)
    }
}
