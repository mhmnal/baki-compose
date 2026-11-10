package app.baki

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoggedAtTest {

    @Test
    fun oldEntriesHaveNoLoggedTime() {
        val old = Entry(id = "old", date = "2026-11-01", amount = 500)
        assertEquals("", old.loggedAt)
        assertNull(loggedTime(old.loggedAt))
    }

    @Test
    fun loggedTimeIsLocalClock() {
        val iso = "2026-11-11T01:02:03Z"
        val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
        assertEquals("%02d:%02d".format(local.hour, local.minute), loggedTime(iso))
        assertNull(loggedTime("not a timestamp"))
    }

    @Test
    fun loggedAtRoundTripsAndSurvivesACopy() {
        val e = Entry(
            id = "e1", date = "2026-11-11", amount = 1234,
            updated = "2026-11-11T01:02:03Z", loggedAt = "2026-11-11T01:02:03Z",
        )
        val json = BakiJson.encodeToString(Entry.serializer(), e)
        assertTrue(json.contains("\"loggedAt\""))
        val decoded = BakiJson.decodeFromString(Entry.serializer(), json)
        assertEquals(e.loggedAt, decoded.loggedAt)
        assertEquals(e.loggedAt, decoded.copy(note = "edited").loggedAt)
    }
}
