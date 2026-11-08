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

    /** What this month actually asks for: every active, non-optional bill falling due in it. */
    fun committed(store: Store, monthLabel: String): Long =
        due(store, monthLabel).sumOf { it.amount }

    /** The month's commitments including the annual bills spread over twelve months. */
    fun averageMonthly(store: Store): Long {
        val active = store.bills.filter { it.active && it.optional.not() }
        val monthly = active.filter { it.cadence == "monthly" }.sumOf { it.amount }
        val annual = active.filter { it.cadence == "annual" }.sumOf { it.amount }
        return monthly + annual / 12
    }

    /** The live link for this bill and period, if its expense still exists. */
    fun payment(store: Store, bill: Bill, period: String): BillPayment? {
        val link = store.billPayments.firstOrNull {
            it.active && it.billId == bill.id && it.period == period &&
                store.entries.any { e -> e.id == it.entryId }
        } ?: return null
        return link
    }

    fun isCovered(store: Store, bill: Bill, period: String): Boolean = payment(store, bill, period) != null

    fun paidOn(store: Store, bill: Bill, period: String): String? =
        payment(store, bill, period)?.paidAt?.take(10)

    /** Committed (non-optional) bills for a month, whether or not they are paid. */
    fun due(store: Store, monthLabel: String): List<Bill> =
        store.bills.filter { it.active && it.optional.not() && fallsIn(it, monthLabel) }

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

    /**
     * Entries that could be the payment for [bill] in [period]: expenses inside that period which match
     * the bill's amount or its category, and are not already linked to any bill.
     */
    fun linkCandidates(store: Store, bill: Bill, period: String): List<Entry> {
        val linked = store.billPayments.map { it.entryId }.toSet()
        val month = if (bill.cadence == "annual") period.take(4) else period.take(7)
        return store.entries
            .filter { it.type != "income" && it.id !in linked }
            .filter { it.date.startsWith(month) }
            .filter { it.amount == bill.amount || it.cat == bill.category }
            .sortedByDescending { it.date }
    }

    /** One payment, resolved against its bill and expense, newest first, for the history list. */
    data class PaymentView(
        val billId: String,
        val billName: String,
        val period: String,
        val amount: Long,
        val paidAt: String,
        val entryExists: Boolean,
        val active: Boolean,
    )

    fun history(store: Store, limit: Int = 60): List<PaymentView> {
        val bills = store.bills.associateBy { it.id }
        val entries = store.entries.map { it.id }.toSet()
        return store.billPayments
            .sortedWith(compareByDescending<BillPayment> { it.paidAt }.thenByDescending { it.updated })
            .take(limit)
            .map { p ->
                PaymentView(
                    billId = p.billId,
                    billName = bills[p.billId]?.name ?: "Deleted bill",
                    period = p.period,
                    amount = p.amount,
                    paidAt = p.paidAt,
                    entryExists = p.entryId in entries,
                    active = p.active,
                )
            }
    }

    fun shiftMonthLabel(monthLabel: String, delta: Int): String {
        var y = monthLabel.take(4).toInt()
        var m = monthLabel.substring(5, 7).toInt() + delta
        while (m > 12) { m -= 12; y += 1 }
        while (m < 1) { m += 12; y -= 1 }
        return "%04d-%02d".format(y, m)
    }
}
