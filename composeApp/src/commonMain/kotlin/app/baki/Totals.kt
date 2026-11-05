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

/** The last day the newest imported statement already accounts for. */
fun statementCoveredUntil(store: Store): String? {
    val st = store.statements.values.maxByOrNull { it.weekStart } ?: return null
    return addDays(st.weekStart, 6)
}

/**
 * Money earned after the last statement period: Grab has not settled it yet.
 * Returns null when there is no statement to anchor against.
 */
fun payoutDue(store: Store): Double? {
    val covered = statementCoveredUntil(store) ?: return null
    val pending = store.shifts.filter { it.date > covered }
    if (pending.isEmpty()) return 0.0
    return pending.sumOf { it.gross } / 100.0
}

/** A fuel purchase that carries an odometer reading. */
data class MeasuredEconomy(val km: Double, val litres: Double, val kml: Double, val fills: Int, val from: String, val to: String)

/**
 * Fuel economy from fill-ups that carry an odometer reading. Partial fills make tank-to-tank
 * unreliable, so this spans first reading to last and counts every litre bought in between —
 * same rule as the web app.
 */
fun measuredEconomy(store: Store): MeasuredEconomy? {
    val price = store.settings.profit.fuelPrice.takeIf { it > 0 } ?: 1.99
    val rows = store.entries
        .filter { it.cat == "fuel" && (it.odo ?: 0) > 0 }
        .sortedBy { it.date }
    if (rows.size < 2) return null
    val first = rows.first()
    val last = rows.last()
    val km = (last.odo!! - first.odo!!).toDouble()
    val litres = rows.drop(1).sumOf { (it.amount / 100.0) / price }
    if (km <= 0 || litres <= 0) return null
    return MeasuredEconomy(km, litres, km / litres, rows.size, first.date, last.date)
}

/** One 4-hour block of a weekday, with the hours spent there and the money earned. */
data class HourBlock(val day: Int, val block: Int, val hours: Double, val net: Double) {
    val rate: Double get() = if (hours > 0) net / hours else 0.0
}

/**
 * Spreads each shift's net across the clock so you can see which windows actually pay.
 * Day 0 = Monday, block 0 = 00:00-04:00.
 */
fun hourHeatmap(store: Store): List<HourBlock> {
    if (store.shifts.isEmpty()) return emptyList()
    val cfg = store.settings.profit

    // One pass to total the hours per month, so the overhead lookup is a map read instead of
    // scanning every shift once per shift (this was O(n^2): 12.7 ms for 520 shifts).
    val hoursByMonth = HashMap<String, Double>(16)
    for (sh in store.shifts) {
        if (sh.hours > 0) {
            val m = sh.date.substring(0, 7)
            hoursByMonth[m] = (hoursByMonth[m] ?: 0.0) + sh.hours
        }
    }
    val monthly = monthlyOverhead(cfg)

    // date parsing and string splitting are the real cost per shift, so memoise both
    val dowCache = HashMap<String, Int>(64)
    val acc = HashMap<Int, DoubleArray>(64)   // key = day * 6 + block
    for (sh in store.shifts) {
        if (sh.hours <= 0) continue
        val month = sh.date.substring(0, 7)
        val monthHours = hoursByMonth[month] ?: 0.0
        val overheadd = if (monthHours > 0.0) monthly / monthHours else 0.0
        val m = shiftMetrics(sh, cfg, overheadd)
        val dow = dowCache.getOrPut(sh.date) {
            runCatching { LocalDate.parse(sh.date).dayOfWeek.ordinal }.getOrDefault(0)
        }
        // "HH:MM" — slicing avoids allocating a list per shift
        val startHour = if (sh.start.length >= 2) {
            (sh.start.substring(0, 2).toIntOrNull() ?: 0) +
                (if (sh.start.length >= 5) (sh.start.substring(3, 5).toIntOrNull() ?: 0) / 60.0 else 0.0)
        } else 0.0
        var remaining = sh.hours
        var cursor = startHour
        val perHour = m.net / sh.hours
        while (remaining > 0.001) {
            val block = (cursor.toInt() / 4).coerceIn(0, 5)
            val blockEnd = (block + 1) * 4.0
            val take = minOf(remaining, blockEnd - cursor)
            if (take <= 0) break
            val arr = acc.getOrPut(dow * 6 + block) { DoubleArray(2) }
            arr[0] += take
            arr[1] += perHour * take
            cursor += take
            remaining -= take
            if (cursor >= 24.0) break
        }
    }
    return acc.entries.map { (k, v) -> HourBlock(k / 6, k % 6, v[0], v[1]) }
}

/** The block with the best rate, once it has a believable sample. */
fun bestHourBlock(store: Store): HourBlock? =
    hourHeatmap(store).filter { it.hours >= 0.5 }.maxByOrNull { it.rate }

// ---------------------------------------------------------------- cost per km

data class MonthCost(val month: String, val km: Double, val running: Double, val upkeep: Double) {
    val total: Double get() = running + upkeep
    val perKm: Double get() = if (km > 0.01) total / km else 0.0
}

/**
 * What a kilometre cost that month: fuel + wear from the distance actually driven, plus real
 * spending on upkeep (service, wash, road tax). Running cost is derived from km so a month with no
 * fill-up does not look artificially cheap.
 */
fun monthCost(store: Store, month: String): MonthCost {
    val cfg = store.settings.profit
    val shifts = store.shifts.filter { it.date.startsWith(month) }
    var km = 0.0
    var running = 0.0
    for (sh in shifts) {
        val m = shiftMetrics(sh, cfg)
        km += m.km
        running += m.fuel + m.wear
    }
    val upkeepCats = setOf("maintenance", "carwash", "roadtax")
    val upkeep = store.entries
        .filter { it.type != "income" && it.cat in upkeepCats && it.date.startsWith(month) }
        .sumOf { it.amount } / 100.0
    return MonthCost(month, km, running, upkeep)
}

/** The last [count] months ending at [endMonth], oldest first. */
fun monthCostSeries(store: Store, endMonth: String, count: Int = 4): List<MonthCost> =
    (count - 1 downTo 0).map { back ->
        val m = addDays("$endMonth-01", -back * 28).take(7)   // step back roughly a month
        monthCost(store, m)
    }.distinctBy { it.month }

// ---------------------------------------------------------------- budgets

data class BudgetUse(val cat: String, val limit: Double, val spent: Double) {
    val remaining: Double get() = limit - spent
    val fraction: Double get() = if (limit > 0) (spent / limit).coerceIn(0.0, 1.5) else 0.0
    val over: Boolean get() = spent > limit
}

/** Envelopes for the month, worst first — the ones that need attention stay on top. */
fun budgetUsage(store: Store, month: String): List<BudgetUse> {
    val limits = store.settings.budgets
    if (limits.isEmpty()) return emptyList()
    val spent = store.entries
        .filter { it.type != "income" && it.date.startsWith(month) }
        .groupBy { it.cat }
        .mapValues { (_, rows) -> rows.sumOf { it.amount } / 100.0 }
    return limits.map { (cat, limit) -> BudgetUse(cat, limit, spent[cat] ?: 0.0) }
        .sortedByDescending { it.fraction }
}
