package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Logging a shift must reach the server with a cover fee derived by the 24h rule. */
class ShiftWriteTest {

    private val base = System.getProperty("baki.shiftServer") ?: "http://127.0.0.1:8811"

    @Test
    fun loggingAShiftDerivesCoverAndCreatesTheFeeEntry() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pull()!! } catch (e: Exception) {
            println("[shift] no server at $base — skipping (${e.message})")
            return@runBlocking
        }

        val repo = BakiRepo(ClientConfig(serverUrl = base))
        repo.sync()

        val date = "2026-12-01"
        val start = "14:00"
        val expectedCover = draftCoverCharge(repo.store, date, start)
        repo.addShift(date, start, hours = 4.0, km = 100.0, grossCents = 15000)

        val after = client.pull()!!
        val saved = after.shifts.firstOrNull { it.date == date && it.start == start }
        assertTrue(saved != null, "shift reached the server")
        assertEquals(expectedCover, saved!!.grabFee, "cover fee derived by the rule")
        assertEquals(15000L, saved.gross, "payout in cents")

        val fee = after.entries.firstOrNull { it.id == "fee-${saved.id}" }
        if (expectedCover > 0) {
            assertTrue(fee != null, "a charged shift gets its expense entry")
            assertEquals(expectedCover, fee!!.amount)
            assertEquals("grabfee", fee.cat)
        } else {
            assertTrue(fee == null, "a free shift must not add a fee entry")
        }

        // cleanup: remove the shift and its fee entry from the throwaway server
        client.push(after.copy(), deletedIds = listOfNotNull(saved.id, fee?.id))
        val cleaned = client.pull()!!
        assertTrue(cleaned.shifts.none { it.date == date && it.start == start }, "test shift removed")
        assertEquals(before.shifts.size, cleaned.shifts.size, "server back to its original shifts")
    }
}
