package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillsTest {

    private val rent = Bill("b1", "Rent", 27_000, "monthly", "rent")
    private val google = Bill("b2", "Google", 10_000, "annual", "subscriptions", anchorMonth = 1)
    private val car = Bill("b3", "Car loan", 20_000, "monthly", "carloan", optional = true)

    private fun store(payments: List<BillPayment> = emptyList()): Store =
        Store(bills = listOf(rent, google, car), billPayments = payments)

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
        val s = store(listOf(pay))
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
        )
        // November and December are covered; Google falls due in January, so that is the next open month
        assertEquals("2027-01", Bills.nextOpenMonth(s, "2026-11"))
        assertEquals("2027-01", Bills.nextOpenMonth(s, "2026-12"))
        // Google is annual, so only January blocks January
        val onlyRent = Store(bills = listOf(rent, google))
        assertEquals("2026-11", Bills.nextOpenMonth(onlyRent, "2026-11"))
    }
}
