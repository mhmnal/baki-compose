package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhaseOneDataTest {
    @Test
    fun oldStoresDecodeWithTheNewFieldsAbsent() {
        val raw = """{"v":1,"entries":[],"shifts":[],"settings":{"profit":{}}}"""
        val store = BakiJson.decodeFromString<Store>(raw)
        assertTrue(store.dayNotes.isEmpty())
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun dayNotesAndReceiptsRoundTrip() {
        val store = Store(
            entries = listOf(Entry(id = "e", date = "2026-11-14", amount = 100, receipt = "abc")),
            dayNotes = mapOf("2026-11-14" to DayNote("rainy", "2026-11-14T10:00:00Z")),
        )
        val copy = BakiJson.decodeFromString<Store>(BakiJson.encodeToString(Store.serializer(), store))
        assertEquals("abc", copy.entries.single().receipt)
        assertEquals("rainy", copy.dayNotes["2026-11-14"]?.text)
    }

    @Test
    fun mergeKeepsReceiptWhenAnOlderClientEditsTheEntry() {
        val old = Entry(id = "e", date = "2026-11-14", amount = 100, updated = "2026-11-14T10:00:00Z", receipt = "abc")
        val newerWithoutReceipt = old.copy(amount = 200, updated = "2026-11-14T11:00:00Z", receipt = null)
        val merged = mergeStores(Store(entries = listOf(old)), Store(entries = listOf(newerWithoutReceipt)))
        assertEquals(200, merged.entries.single().amount)
        assertEquals("abc", merged.entries.single().receipt)
    }

    @Test
    fun mergeHonoursAnExplicitReceiptRemoval() {
        val old = Entry(id = "e", date = "2026-11-14", amount = 100, updated = "2026-11-14T10:00:00Z", receipt = "abc")
        val removed = old.copy(updated = "2026-11-14T11:00:00Z", receipt = "")
        val merged = mergeStores(Store(entries = listOf(old)), Store(entries = listOf(removed)))
        assertEquals("", merged.entries.single().receipt)
    }

    @Test
    fun dayNoteMergeIsLastWriterWins() {
        val first = Store(dayNotes = mapOf("2026-11-14" to DayNote("first", "2026-11-14T10:00:00Z")))
        val second = Store(dayNotes = mapOf("2026-11-14" to DayNote("second", "2026-11-14T11:00:00Z")))
        assertEquals("second", mergeStores(first, second).dayNotes["2026-11-14"]?.text)
        assertEquals("first", mergeStores(second, first).dayNotes["2026-11-14"]?.text)
    }

    @Test
    fun missingReceiptIsNull() {
        assertNull(Entry(id = "e", date = "2026-11-14", amount = 100).receipt)
    }
}
