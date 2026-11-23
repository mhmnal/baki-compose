package app.baki

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** End-to-end state machine for a bill: pay, sync, unlink, re-pay, delete the expense, delete the bill. */
class BillPaymentFlowTest {
    private val base = System.getProperty("baki.billServer") ?: "http://127.0.0.1:8813"

    @Test
    fun billPaymentSurvivesSyncAndReopensCleanly() = runBlocking {
        val client = TrackerClient(base)
        try { client.pull() } catch (e: Exception) {
            println("[bill-flow] no test server at $base — skipping (${e.message})")
            return@runBlocking
        }
        val repo = BakiRepo(ClientConfig(serverUrl = base, allowWrites = true))
        repo.sync()
        val billId = "test-bill-" + kotlin.random.Random.nextLong().toString().takeLast(8)
        assertTrue(repo.saveBill(Bill(billId, "Test commitment", 12_300, "monthly", "other")))
        val period = "2026-12"

        assertTrue(repo.payBill(billId, period), "first payment should succeed")
        assertTrue(Bills.isCovered(repo.store, repo.store.bills.first { it.id == billId }, period))
        val firstEntry = repo.store.billPayments.first { it.billId == billId && it.period == period }.entryId
        val paid = client.pull()!!
        assertTrue(paid.billPayments.any { it.billId == billId && it.period == period && it.active })

        assertTrue(repo.undoBillPayment(billId, period, removeEntry = true))
        val undone = client.pull()!!
        assertFalse(undone.billPayments.first { it.billId == billId && it.period == period }.active)
        assertFalse(undone.entries.any { it.id == firstEntry })

        assertTrue(repo.payBill(billId, period), "unlinked inactive history must not block re-pay")
        val repaid = client.pull()!!
        assertTrue(repaid.billPayments.any { it.billId == billId && it.period == period && it.active })
        val secondEntry = repo.store.billPayments.first { it.billId == billId && it.period == period }.entryId

        assertTrue(repo.deleteEntry(secondEntry))
        repo.sync()
        val deleted = client.pull()!!
        assertFalse(deleted.entries.any { it.id == secondEntry })
        assertFalse(deleted.billPayments.first { it.billId == billId && it.period == period }.active)

        // A bill with inactive payment history must still be deletable.
        assertTrue(repo.deleteBill(billId))
        assertFalse(client.pull()!!.bills.first { it.id == billId }.active)
        assertNotNull(repo.store.billPayments.firstOrNull { it.billId == billId })
    }
}
