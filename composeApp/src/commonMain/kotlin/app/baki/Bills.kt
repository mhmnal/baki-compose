package app.baki

/**
 * Bills are commitments tracked by **period**, not by due date: you can settle next month before this
 * one ends, and prepay further ahead. A payment links to an ordinary expense entry dated the day the
 * money left, so cash flow stays truthful while the commitment is marked covered.
 */
object Bills {

    fun monthPeriod(dateIso: String): String = dateIso.take(7)

    /** Monthly bills use "YYYY-MM"; annual bills use "YYYY". */
    fun periodOf(bill: Bill, monthLabel: String): String =
        if (bill.cadence == "annual") monthLabel.take(4) else monthLabel

    /** The period after [period] for this cadence. */
    fun nextPeriod(bill: Bill, period: String): String {
        if (bill.cadence == "annual") return (period.take(4).toInt() + 1).toString()
        val y = period.take(4).toInt()
        val m = period.substring(5, 7).toInt()
        return if (m == 12) "%04d-01".format(y + 1) else "%04d-%02d".format(y, m + 1)
    }

    /** True when this bill falls due in [monthLabel] (monthly always, annual only in its anchor month). */
    fun fallsIn(bill: Bill, monthLabel: String): Boolean =
        bill.cadence == "monthly" || bill.anchorMonth == monthLabel.substring(5, 7).toInt()

    fun isCovered(store: Store, bill: Bill, period: String): Boolean =
        store.billPayments.any { it.billId == bill.id && it.period == period }

    fun paidOn(store: Store, bill: Bill, period: String): String? =
        store.billPayments.firstOrNull { it.billId == bill.id && it.period == period }?.paidAt?.take(10)

    /** Committed (non-optional) bills for a month, whether or not they are paid. */
    fun due(store: Store, monthLabel: String): List<Bill> =
        store.bills.filter { it.optional.not() && fallsIn(it, monthLabel) }

    fun unpaid(store: Store, monthLabel: String): List<Bill> =
        due(store, monthLabel).filter { !isCovered(store, it, periodOf(it, monthLabel)) }

    /** The next month that still has uncovered bills, starting from [monthLabel]. */
    fun nextOpenMonth(store: Store, monthLabel: String): String {
        var m = monthLabel
        repeat(24) {
            if (unpaid(store, m).isEmpty()) m = shiftMonthLabel(m, 1) else return m
        }
        return m
    }

    fun shiftMonthLabel(monthLabel: String, delta: Int): String {
        var y = monthLabel.take(4).toInt()
        var m = monthLabel.substring(5, 7).toInt() + delta
        while (m > 12) { m -= 12; y += 1 }
        while (m < 1) { m += 12; y -= 1 }
        return "%04d-%02d".format(y, m)
    }
}
