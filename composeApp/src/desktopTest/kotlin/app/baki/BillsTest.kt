package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillsTest {

    private val rent = Bill("b1", "Rent", 27_000, "monthly", "rent")
    private val google = Bill("b2", "Google", 10_000, "annual", "subscriptions", anchorMonth = 1)
    private val car = Bill("b3", "Car loan", 20_000, "monthly", "carloan", optional = true)

    private fun store(payments: List<BillPayment> = emptyList(), entries: List<Entry> = emptyList()): Store =
        Store(entries = entries, bills = listOf(rent, google, car), billPayments = payments)

    private fun entry(id: String) =
        Entry(id = id, date = "2026-11-05", type = "expense", cat = "rent", amount = 27_000, updated = "x")

    @Test
    fun monthlyPeriodsRollOverTheYear() {
        assertEquals("2026-12", Bills.periodOf(rent, "2026-12"))
        assertEquals("2027-01", Bills.nextPeriod(rent, "2026-12"))
    }

    @Test
    fun annualBillsUseTheYearAndTheirAnchorMonth() {
        assertEquals("2026", Bills.periodOf(google, "2026-03"))
        assertEquals("2027", Bills.nextPeriod(google, "2026"))
        assertTrue(Bills.fallsIn(google, "2026-01"))
        assertFalse(Bills.fallsIn(google, "2026-07"))
    }

    @Test
    fun optionalBillsAreNeverDue() {
        val due = Bills.due(store(), "2026-11")
        assertEquals(listOf("b1"), due.map { it.id })
    }

    @Test
    fun payingCoversOnlyThatPeriod() {
        val pay = BillPayment("b1", "2026-11", 27_000, "e1", "2026-11-05", "x")
        val s = store(listOf(pay), listOf(entry("e1")))
        assertTrue(Bills.isCovered(s, rent, "2026-11"))
        assertFalse(Bills.isCovered(s, rent, "2026-12"))
        assertEquals(listOf(rent), Bills.unpaid(s, "2026-12"))
    }

    @Test
    fun nextOpenMonthSkipsFullyCoveredOnes() {
        val s = store(
            listOf(
                BillPayment("b1", "2026-11", 27_000, "e1", "2026-11-05", "x"),
                BillPayment("b1", "2026-12", 27_000, "e2", "2026-11-05", "x"),
            ),
            listOf(entry("e1"), entry("e2")),
        )
        // November and December are covered; Google falls due in January, so that is the next open month
        assertEquals("2027-01", Bills.nextOpenMonth(s, "2026-11"))
        assertEquals("2027-01", Bills.nextOpenMonth(s, "2026-12"))
        // Google is annual, so only January blocks January
        val onlyRent = Store(bills = listOf(rent, google))
        assertEquals("2026-11", Bills.nextOpenMonth(onlyRent, "2026-11"))
    }
}

class BillLinkTest {

    private val rent = Bill("b1", "Rent", 27_000, "monthly", "rent")

    private fun e(id: String, cat: String, amount: Long, date: String = "2026-11-07") =
        Entry(id = id, date = date, type = "expense", cat = cat, amount = amount, updated = "x")

    @Test
    fun matchesByAmountEvenWhenTheOldCategoryWasWrong() {
        // the November rent was logged as "Other" with a note; it should still be offerable
        val store = Store(entries = listOf(e("e1", "other", 27_000)), bills = listOf(rent))
        assertEquals(listOf("e1"), Bills.linkCandidates(store, rent, "2026-11").map { it.id })
    }

    @Test
    fun matchesByCategoryAtADifferentAmount() {
        val store = Store(entries = listOf(e("e1", "rent", 26_000)), bills = listOf(rent))
        assertEquals(listOf("e1"), Bills.linkCandidates(store, rent, "2026-11").map { it.id })
    }

    @Test
    fun ignoresOtherMonthsAndAlreadyLinkedEntries() {
        val store = Store(
            entries = listOf(e("old", "other", 27_000, "2026-10-07"), e("used", "other", 27_000)),
            bills = listOf(rent),
            billPayments = listOf(BillPayment("b1", "2026-10", 27_000, "used", "2026-10-07", "x")),
        )
        assertEquals(emptyList(), Bills.linkCandidates(store, rent, "2026-11"))
    }
}


class BillMergeTest {
    @Test
    fun billsSurviveTheSyncMerge() {
        val bill = Bill("b1", "Rent", 27_000, "monthly", "rent", updated = "2026-11-01T00:00:00Z")
        val local = Store()
        val remote = Store(bills = listOf(bill))
        assertEquals(1, mergeStores(local, remote).bills.size)
        // and a newer local edit wins
        val newer = bill.copy(amount = 28_000, updated = "2026-11-02T00:00:00Z")
        assertEquals(28_000, mergeStores(Store(bills = listOf(newer)), remote).bills.single().amount)
    }
}

class SyncChangeTest {
    @Test
    fun aBillsOnlyChangeSurvivesTheMerge() {
        val stored = Store(entries = listOf(Entry(id = "e1", date = "2026-11-01", type = "expense", cat = "food", amount = 100, updated = "x")))
        val remote = stored.copy(bills = listOf(Bill("b1", "Rent", 27_000, "monthly", "rent", updated = "y")))
        val merged = mergeStores(stored, remote)
        assertTrue(merged != stored, "merge must be seen as a change so the store is saved")
        assertEquals(1, merged.bills.size)
    }
}


class BillTotalsTest {
    private val rent = Bill("b1", "Rent", 27_000, "monthly", "rent")
    private val google = Bill("b2", "Google", 10_000, "annual", "subscriptions", anchorMonth = 1)
    private val car = Bill("b3", "Car loan", 20_000, "monthly", "carloan", optional = true)
    private val old = Bill("b4", "Cancelled", 5_000, "monthly", "subscriptions", active = false)

    @Test
    fun theMonthTotalCountsOnlyWhatIsDue() {
        val s = Store(bills = listOf(rent, google, car, old))
        assertEquals(27_000, Bills.committed(s, "2026-11"))
        assertEquals(37_000, Bills.committed(s, "2026-01"))   // Google's month
    }

    @Test
    fun averageIncludesAnnualsSpreadOverTheYear() {
        val s = Store(bills = listOf(rent, google, car, old))
        // 270 + 100/12 = 278.33; optional and inactive are excluded
        assertEquals(27_833, Bills.averageMonthly(s))
    }
}


class BillUnlinkTest {
    private val rent = Bill("b1", "Rent", 27_000, "monthly", "rent")
    private fun entry(id: String) = Entry(id = id, date = "2026-11-07", type = "expense", cat = "rent", amount = 27_000, updated = "x")

    @Test
    fun anUnlinkedPaymentStopsCoveringThePeriod() {
        val link = BillPayment("b1", "2026-11", 27_000, "e1", "2026-11-07", "x", active = false)
        val store = Store(entries = listOf(entry("e1")), bills = listOf(rent), billPayments = listOf(link))
        assertFalse(Bills.isCovered(store, rent, "2026-11"))
        assertEquals(listOf(rent), Bills.unpaid(store, "2026-11"))
    }

    @Test
    fun aLinkWhoseExpenseWasDeletedNoLongerCovers() {
        val link = BillPayment("b1", "2026-11", 27_000, "gone", "2026-11-07", "x")
        val store = Store(bills = listOf(rent), billPayments = listOf(link))
        assertFalse(Bills.isCovered(store, rent, "2026-11"))
    }

    @Test
    fun theNewestPaymentWinsAcrossDevices() {
        val live = BillPayment("b1", "2026-11", 27_000, "e1", "2026-11-07", "2026-11-07T10:00:00Z")
        val unlinked = live.copy(active = false, updated = "2026-11-08T10:00:00Z")
        val merged = mergeStores(Store(billPayments = listOf(live)), Store(billPayments = listOf(unlinked)))
        assertEquals(false, merged.billPayments.single().active)
    }
}
