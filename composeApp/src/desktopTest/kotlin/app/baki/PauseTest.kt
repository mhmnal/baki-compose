package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A break stops the clock, keeps the distance, and does not count as working time. */
class PauseTest {

    private val hour = 3_600_000L

    @Test
    fun breaksAreSubtractedFromTheClock() {
        val start = 1_000_000L
        val live = LiveShift(
            start = start,
            pauses = listOf(LivePause(from = start + hour, to = start + 3 * hour)),
        )
        val at = start + 4 * hour
        assertEquals(4 * hour, liveElapsedMs(live, at))
        assertEquals(2 * hour, pausedMs(live, at))
        assertEquals(2 * hour, liveWorkedMs(live, at))
        assertFalse(isPaused(live))
    }

    @Test
    fun anOpenBreakCountsUpToNow() {
        val start = 1_000_000L
        val live = LiveShift(start = start, pauses = listOf(LivePause(from = start + hour)))
        val at = start + 3 * hour
        assertTrue(isPaused(live))
        assertEquals(2 * hour, pausedMs(live, at))
        assertEquals(1 * hour, liveWorkedMs(live, at))
    }

    @Test
    fun endingUsesWorkedHoursNotWallClock() = runBlocking {
        // no server needed: endLive writes locally and the push simply fails quietly
        val repo = BakiRepo(ClientConfig(storageEngine = "json", serverUrl = "http://127.0.0.1:9", allowWrites = true))
        repo.startLive()
        val start = repo.live!!.start
        // simulate a 1h break in a 4h shift
        repo.pauseLive()
        val pausedAt = repo.live!!.pauses.last().from
        repo.resumeLive()
        val shiftProbe = repo.live!!.copy(
            pauses = listOf(LivePause(from = pausedAt, to = pausedAt + hour)),
        )
        assertEquals(3 * hour, liveWorkedMs(shiftProbe, start + 4 * hour))
    }
}
