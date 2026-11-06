package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickEntryTest {

    private val cats = listOf(
        Cat("toll", "Toll", "out"),
        Cat("parking", "Parking", "out"),
        Cat("petrol", "Petrol", "out"),
        Cat("food", "Food", "out"),
    )

    @Test
    fun oneCategoryAndAmount() {
        val q = parseQuickEntry("toll 4.20", "2026-11-06", cats)
        assertEquals(listOf(QuickEntry("toll", 420)), q.entries)
        assertEquals("2026-11-06", q.date)
        assertTrue(q.loose.isEmpty())
    }

    @Test
    fun severalOnOneLine() {
        val q = parseQuickEntry("toll 4.20 parking 2 petrol 40.50", "2026-11-06", cats)
        assertEquals(
            listOf(QuickEntry("toll", 420), QuickEntry("parking", 200), QuickEntry("petrol", 4050)),
            q.entries,
        )
    }

    @Test
    fun malayWordsAndYesterday() {
        val q = parseQuickEntry("tol 4.20 minyak 40 semalam", "2026-11-06", cats)
        assertEquals("2026-11-05", q.date)
        assertEquals(listOf(QuickEntry("toll", 420), QuickEntry("petrol", 4000)), q.entries)
    }

    @Test
    fun amountBeforeTheWordStillBinds() {
        val q = parseQuickEntry("4.20 tol", "2026-11-06", cats)
        assertEquals(listOf(QuickEntry("toll", 420)), q.entries)
    }

    @Test
    fun unclaimedNumberIsLooseNotGuessed() {
        val q = parseQuickEntry("toll 4.20 7", "2026-11-06", cats)
        assertEquals(listOf(QuickEntry("toll", 420)), q.entries)
        assertEquals(listOf(700L), q.loose)
    }

    @Test
    fun payoutIsRecognised() {
        val q = parseQuickEntry("payout 32.50", "2026-11-06", cats)
        assertEquals(listOf(3250L), q.payouts)
        assertTrue(q.entries.isEmpty())
    }

    @Test
    fun nonsenseYieldsNothing() {
        val q = parseQuickEntry("hello world", "2026-11-06", cats)
        assertTrue(q.empty)
    }

    @Test
    fun odometerSizedNumberIsNotAnExpense() {
        val q = parseQuickEntry("toll 4.20 45120", "2026-11-06", cats)
        assertEquals(listOf(QuickEntry("toll", 420)), q.entries)
        assertTrue(q.loose.isEmpty())
    }
}
