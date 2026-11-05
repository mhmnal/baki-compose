package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Live shift: payouts accumulate, ending writes a real shift with derived cover. */
class LiveShiftTest {

    private val base = System.getProperty("baki.liveServer") ?: "http://127.0.0.1:8812"

    @Test
    fun endingALiveShiftWritesItToTheServer() = runBlocking {
        val client = TrackerClient(base)
        val before = try { client.pull()!! } catch (e: Exception) {
            println("[live-shift] no server at $base — skipping (${e.message})")
            return@runBlocking
        }

        val repo = BakiRepo(ClientConfig(serverUrl = base))
        repo.sync()

        repo.startLive()
        val start = repo.live!!.start
        repo.addPayout(1200)
        repo.addPayout(800)
        assertEquals(2000L, liveTotalCents(repo.live!!), "payouts accumulate")

        val expectedCover = draftCoverCharge(repo.store, startDate(start), startClock(start))
        repo.endLive(now = start + 3 * 3_600_000L)      // pretend it ran 3 hours

        val after = client.pull()!!
        val saved = after.shifts.maxByOrNull { it.updated }
        assertTrue(saved != null, "a shift reached the server")
        assertEquals(2000L, saved!!.gross, "payout total became the gross")
        assertTrue(kotlin.math.abs(saved.hours - 3.0) < 0.01, "hours = elapsed (${saved.hours})")
        assertEquals(expectedCover, saved.grabFee, "cover derived by the 24h rule")
        assertEquals(2, saved.payouts.size, "payout timestamps kept on the shift")

        // clean up so the throwaway server stays as it was
        val feeId = "fee-${saved.id}"
        client.push(after.copy(), deletedIds = listOf(saved.id, feeId))
        val cleaned = client.pull()!!
        assertEquals(before.shifts.size, cleaned.shifts.size, "server back to its original shifts")
    }
}
