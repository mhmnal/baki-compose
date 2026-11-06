package app.baki

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Floors, not targets: generous bounds that catch an accidental O(n^2) or a per-frame recomputation.
 * Real figures on this machine are 5-10x under them (see Benchmark).
 */
class PerformanceTest {

    private fun store(entries: Int = 2400, shifts: Int = 520): Store {
        val cats = listOf("food", "fuel", "toll", "parking", "carwash", "maintenance", "other")
        val e = (0 until entries).map { i ->
            Entry(
                id = "e$i", date = "2026-%02d-%02d".format(1 + (i / 31) % 12, 1 + i % 28),
                type = if (i % 9 == 0) "income" else "expense",
                cat = cats[i % cats.size], amount = (300 + i % 5000).toLong(),
                updated = "2026-01-01T00:00:00Z",
            )
        }
        val s = (0 until shifts).map { i ->
            Shift(
                id = "s$i", date = "2026-%02d-%02d".format(1 + (i / 31) % 12, 1 + i % 28),
                start = "%02d:00".format(i % 24), hours = 3.0 + (i % 5), km = 60.0 + (i % 90),
                gross = (8000 + i % 9000).toLong(), updated = "2026-01-01T00:00:00Z",
            )
        }
        return Store(entries = e, shifts = s)
    }

    @Test
    fun aMonthOfFiguresIsSubMillisecondish() {
        val s = store()
        monthTotals(s, "2026-05") // warm
        val (_, time) = measureTimedValue { repeat(20) { monthTotals(s, "2026-05") } }
        assertTrue(time.inWholeMilliseconds < 200, "20 month totals took $time")
    }

    @Test
    fun aYearGridStaysInteractive() {
        val s = store()
        val (_, time) = measureTimedValue {
            (1..12).forEach { m -> monthTotals(s, "2026-%02d".format(m)) }
        }
        assertTrue(time.inWholeMilliseconds < 300, "12 month totals took $time")
    }

    @Test
    fun streakAndWeekScanAreCheap() {
        val s = store()
        val (_, time) = measureTimedValue {
            repeat(10) {
                latestWeekWithData(s, "2026-11-07")
                targetStreak(s, "2026-11-07")
            }
        }
        assertTrue(time.inWholeMilliseconds < 300, "10 week+streak scans took $time")
    }

    @Test
    fun storeRoundTripIsFast() {
        val s = store()
        val json = BakiJson.encodeToString(Store.serializer(), s)
        val (decoded, time) = measureTimedValue {
            BakiJson.decodeFromString(Store.serializer(), json)
        }
        assertTrue(time.inWholeMilliseconds < 500, "decoding ${s.entries.size} entries took $time")
        assertTrue(decoded.entries.size == s.entries.size)
    }
}
