package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceTest {
    private fun service(date: String, odo: Int?, note: String = "service") = Entry(
        id = date, date = date, cat = "maintenance", amount = 10_000, note = note, odo = odo,
    )

    @Test
    fun lastServiceAndLatestOdometerAreSeparateFacts() {
        val store = Store(
            entries = listOf(
                service("2026-10-01", 40_000, "engine oil"),
                service("2026-11-04", 41_250, "engine + CVT"),
                Entry(id = "fuel", date = "2026-11-10", cat = "fuel", amount = 5000, odo = 42_100),
            ),
        )
        val s = serviceInfo(store)
        assertEquals("2026-11-04", s.last?.date)
        assertEquals(41_250, s.last?.odo)
        assertEquals(42_100, s.latestOdo)
    }

    @Test
    fun noTargetMeansNoInventedInterval() {
        val s = serviceInfo(Store(entries = listOf(service("2026-11-04", 41_250))))
        assertEquals(null, s.targetKm)
        assertEquals(null, s.targetOn)
        assertEquals(null, s.remainingKm)
    }

    @Test
    fun userTargetReportsRemainingAndOverdue() {
        val store = Store(
            entries = listOf(service("2026-11-04", 41_250)),
            settings = Settings(nextServiceKm = 46_250, nextServiceOn = "2027-05-01"),
        )
        val s = serviceInfo(store)
        assertEquals(5_000, s.remainingKm)
        assertTrue(!s.overdue)
    }
}
