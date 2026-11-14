package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhaseTwoDataTest {
    @Test
    fun oldStoresWithoutDayClosesStillDecode() {
        val store = BakiJson.decodeFromString<Store>("""{"v":1,"entries":[],"shifts":[],"settings":{"profit":{}}}""")
        assertTrue(store.dayCloses.isEmpty())
        assertTrue(store.statementMarks.isEmpty())
    }

    @Test
    fun reopenWinsWithANewerTimestamp() {
        val closed = Store(dayCloses = mapOf("2026-11-14" to DayClose(true, "2026-11-14T10:00:00Z")))
        val reopened = Store(dayCloses = mapOf("2026-11-14" to DayClose(false, "2026-11-14T11:00:00Z")))
        val merged = mergeStores(closed, reopened)
        assertFalse(merged.dayCloses.getValue("2026-11-14").closed)
    }

    @Test
    fun statementMarkRoundTrips() {
        val store = Store(statementMarks = mapOf("2026-11-09" to StatementMark(true, "2026-11-14T11:00:00Z")))
        val copy = BakiJson.decodeFromString<Store>(BakiJson.encodeToString(Store.serializer(), store))
        assertTrue(copy.statementMarks.getValue("2026-11-09").reconciled)
        assertEquals("2026-11-14T11:00:00Z", copy.statementMarks.getValue("2026-11-09").updated)
    }

    @Test
    fun statementReopenWinsWithANewerMark() {
        val marked = Store(statementMarks = mapOf("2026-11-09" to StatementMark(true, "2026-11-14T10:00:00Z")))
        val reopened = Store(statementMarks = mapOf("2026-11-09" to StatementMark(false, "2026-11-14T11:00:00Z")))
        assertFalse(mergeStores(marked, reopened).statementMarks.getValue("2026-11-09").reconciled)
    }
}
