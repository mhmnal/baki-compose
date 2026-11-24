package app.baki

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreviewScenarioTest {
    private val end = LocalDate(2026, 11, 24)

    @Test
    fun sameSeedSameStore() {
        val a = PreviewScenario.generate(end, 42L)
        val b = PreviewScenario.generate(end, 42L)
        assertEquals(a, b)
    }

    @Test
    fun producesMixedHistoryWithinRange() {
        val store = PreviewScenario.generate(end, 42L)
        val dates = store.entries.map { it.date } + store.shifts.map { it.date }
        assertTrue(store.entries.size > 150)
        assertTrue(store.shifts.size > 40)
        assertTrue(dates.all { it <= end.toString() })
        assertTrue(dates.toSet().size > 40)
        assertTrue(store.shifts.any { it.payouts.isEmpty() })
        assertTrue(store.shifts.any { it.payouts.isNotEmpty() })
        assertTrue(store.entries.any { it.cat == "fuel" && it.odo != null })
        assertTrue(store.entries.any { it.cat == "fuel" && it.odo == null })
        assertTrue(store.statements.isNotEmpty())
        assertTrue(store.weekCloses.isNotEmpty())
        assertTrue(store.bills.any { it.dueDay == null })
        assertTrue(store.billPayments.any { !it.active })
        assertNotNull(BakiJson.encodeToString(Store.serializer(), store))
    }

    @Test
    fun includesOpenCurrentWeekAndPayoutGaps() {
        val store = PreviewScenario.generate(end, 7L)
        val report = dataHealth(store, lastBackupAt = null, lastBackupEntries = 0, today = end.toString())
        assertTrue(report.payoutGapTotal > 0)
        assertNotNull(report.openWeek)
        assertTrue(report.backupStale)
    }
}
