package app.baki

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Day and month roll-ups, same rules as dayTotals()/monthTotals() in index.html. */
data class Totals(val income: Long, val expense: Long, val entryCount: Int, val shiftCount: Int) {
    val net: Long get() = income - expense
}

fun entriesOn(store: Store, date: String): List<Entry> = store.entries.filter { it.date == date }
fun shiftsOn(store: Store, date: String): List<Shift> = store.shifts.filter { it.date == date }

fun dayTotals(store: Store, date: String): Totals {
    val list = entriesOn(store, date)
    val shifts = shiftsOn(store, date)
    val income = list.filter { it.type == "income" }.sumOf { it.amount } + shifts.sumOf { it.gross }
    val expense = list.filter { it.type != "income" }.sumOf { it.amount } + shifts.sumOf { it.extra }
    return Totals(income, expense, list.size, shifts.size)
}

fun monthTotals(store: Store, month: String): Totals {
    val list = store.entries.filter { it.date.startsWith(month) }
    val shifts = store.shifts.filter { it.date.startsWith(month) }
    val income = list.filter { it.type == "income" }.sumOf { it.amount } + shifts.sumOf { it.gross }
    val expense = list.filter { it.type != "income" }.sumOf { it.amount } + shifts.sumOf { it.extra }
    return Totals(income, expense, list.size, shifts.size)
}

/** RM formatting without locale surprises: 1234.5 -> "1,234.50". */
fun rm(cents: Long): String {
    val neg = cents < 0
    val abs = if (neg) -cents else cents
    val whole = abs / 100
    val frac = (abs % 100).toString().padStart(2, '0')
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (neg) "-" else "") + grouped + "." + frac
}

fun rm(ringgit: Double): String = rm(Math.round(ringgit * 100).toLong())

/** Ringgit (Double) to integer cents, for the hero numbers. */
fun cents(ringgit: Double): Long = Math.round(ringgit * 100)

/** Fuel and wear funds: accrued from kilometres driven, drained by real spending. */
data class Fund(val accrued: Double, val spent: Double) { val balance: Double get() = accrued - spent }

data class Funds(val fuel: Fund, val wear: Fund)

fun fundStats(store: Store, month: String? = null): Funds {
    val cfg = store.settings.profit
    val shifts = store.shifts.filter { month == null || it.date.startsWith(month) }
    var fuelAcc = 0.0
    var wearAcc = 0.0
    for (s in shifts) {
        val m = shiftMetrics(s, cfg)
        fuelAcc += m.fuel
        wearAcc += m.wear
    }
    val expenses = store.entries.filter { it.type != "income" && (month == null || it.date.startsWith(month)) }
    val fuelSpent = expenses.filter { it.cat == "fuel" }.sumOf { it.amount } / 100.0
    val wearSpent = expenses.filter { it.cat == "maintenance" || it.cat == "carwash" || it.cat == "roadtax" }
        .sumOf { it.amount } / 100.0
    return Funds(Fund(fuelAcc, fuelSpent), Fund(wearAcc, wearSpent))
}

/** Expense totals per category, biggest first. */
fun expensesByCategory(store: Store, month: String): List<Pair<String, Long>> =
    store.entries
        .filter { it.type != "income" && it.date.startsWith(month) }
        .groupBy { it.cat }
        .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

/** Every day in the month that has activity, with its totals. */
fun monthDays(store: Store, month: String): List<Pair<String, Totals>> =
    store.entries.filter { it.date.startsWith(month) }.map { it.date }.distinct().sorted()
        .map { it to dayTotals(store, it) }

/** Shift a date by whole days. */
fun addDays(date: String, days: Int): String = LocalDate.parse(date).plus(DatePeriod(days = days)).toString()

/** Monday of the week containing [date]. */
fun weekStartOf(date: String): String {
    val d = LocalDate.parse(date)
    return addDays(date, -d.dayOfWeek.ordinal)   // Monday = 0 in kotlinx-datetime
}

fun weekTotals(store: Store, startIso: String): Totals {
    val days = (0 until 7).map { addDays(startIso, it) }
    val entries = store.entries.filter { it.date in days }
    val shifts = store.shifts.filter { it.date in days }
    val income = entries.filter { it.type == "income" }.sumOf { it.amount } + shifts.sumOf { it.gross }
    val expense = entries.filter { it.type != "income" }.sumOf { it.amount } + shifts.sumOf { it.extra }
    return Totals(income, expense, entries.size, shifts.size)
}

/** Month grid: one entry per day, null for padding before the 1st. */
fun monthGrid(month: String): List<String?> {
    val first = LocalDate.parse("$month-01")
    val next = if (first.monthNumber == 12) LocalDate(first.year + 1, 1, 1)
    else LocalDate(first.year, first.monthNumber + 1, 1)
    val length = first.daysUntil(next)
    val lead = first.dayOfWeek.ordinal
    return List(lead) { null } + (1..length).map { "$month-${it.toString().padStart(2, '0')}" }
}

/** "5 November" — the way the web app labels a day. */
fun prettyDate(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val month = d.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "${d.dayOfMonth} $month"
}

/** "MON", "TUE" … */
fun weekdayShort(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    return d.dayOfWeek.name.take(3)
}
