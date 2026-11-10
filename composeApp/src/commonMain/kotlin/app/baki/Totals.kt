package app.baki

import kotlin.math.roundToLong

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Day and month roll-ups, same rules as dayTotals()/monthTotals() in index.html. */
data class Totals(val income: Long, val expense: Long, val entryCount: Int, val shiftCount: Int) {
    val net: Long get() = income - expense
}

/** The operating truth: shift earnings after accrued running costs, never ledger fee entries. */
data class OperatingTotals(
    val earnings: Long,
    val costs: Long,
    val net: Long,
    val hours: Double,
    val shiftCount: Int,
) {
    val perHour: Double? get() = if (hours > 0.01) net / 100.0 / hours else null
}

private fun operatingFor(store: Store, shifts: List<Shift>): OperatingTotals {
    val cfg = store.settings.profit
    val rows = shifts.map { shiftMetrics(it, cfg, overheadPerHour(store, it.date.take(7))) }
    val earnings = cents(rows.sumOf { it.gross })
    val net = cents(rows.sumOf { it.net })
    return OperatingTotals(earnings, earnings - net, net, rows.sumOf { it.hrs }, rows.size)
}

fun dayOperating(store: Store, date: String): OperatingTotals =
    operatingFor(store, store.shifts.filter { it.date == date })

fun monthOperating(store: Store, month: String): OperatingTotals =
    operatingFor(store, store.shifts.filter { it.date.startsWith(month) })

fun entriesOn(store: Store, date: String): List<Entry> = store.entries.filter { it.date == date }
fun shiftsOn(store: Store, date: String): List<Shift> = store.shifts.filter { it.date == date }

fun dayTotals(store: Store, date: String): Totals {
    val list = entriesOn(store, date)
    val shifts = shiftsOn(store, date)
    val income = list.filter { it.type == "income" }.sumOf { it.amount } +
        shifts.sumOf { it.gross + it.incentive }
    val expense = list.filter { it.type != "income" }.sumOf { it.amount } +
        shifts.sumOf { if (it.reimbursedTolls) 0L else it.extra }
    return Totals(income, expense, list.size, shifts.size)
}

fun monthTotals(store: Store, month: String): Totals {
    val list = store.entries.filter { it.date.startsWith(month) }
    val shifts = store.shifts.filter { it.date.startsWith(month) }
    val income = list.filter { it.type == "income" }.sumOf { it.amount } +
        shifts.sumOf { it.gross + it.incentive }
    val expense = list.filter { it.type != "income" }.sumOf { it.amount } +
        shifts.sumOf { if (it.reimbursedTolls) 0L else it.extra }
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
    val fuelSpent = expenses.filter { isFuelCat(it.cat) }.sumOf { it.amount } / 100.0
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
    val income = entries.filter { it.type == "income" }.sumOf { it.amount } +
        shifts.sumOf { it.gross + it.incentive }
    val expense = entries.filter { it.type != "income" }.sumOf { it.amount } +
        shifts.sumOf { if (it.reimbursedTolls) 0L else it.extra }
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

/** Local HH:mm for a creation timestamp; null for rows that predate the field. */
fun loggedTime(iso: String): String? {
    if (iso.isBlank()) return null
    return runCatching {
        val t = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
        "%02d:%02d".format(t.hour, t.minute)
    }.getOrNull()
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
    return pending.sumOf { it.gross + it.incentive } / 100.0
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
        .filter { isFuelCat(it.cat) && (it.odo ?: 0) > 0 }
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

/**
 * Consecutive days whose shift earnings beat the target rate, counting back from today.
 * A day with no shift yet does not break the streak (it is still "alive" until midnight).
 */
fun targetStreak(store: Store, today: String): Int {
    val cfg = store.settings.profit
    val target = cfg.targetRate
    if (target <= 0) return 0
    val byDay = store.shifts.groupBy { it.date }
    var day = if (byDay.containsKey(today)) today else addDays(today, -1)
    var streak = 0
    while (true) {
        val shifts = byDay[day] ?: break
        val hours = shifts.sumOf { it.hours }
        if (hours <= 0.01) break
        val net = shifts.sumOf {
            shiftMetrics(it, cfg, overheadPerHour(store, day.substring(0, 7))).net
        }
        if (net / hours < target) break
        streak++
        day = addDays(day, -1)
    }
    return streak
}

/** One remembered shortcut: the same category at the same amount, used more than once. */
data class QuickAdd(val cat: String, val cents: Long, val count: Int)

/** The amounts you keep typing — the fastest possible entry is the one you repeat. */
fun quickAdds(store: Store, limit: Int = 4, sinceDays: Int = 60): List<QuickAdd> {
    val cutoff = addDays(todayIso(), -sinceDays)
    return store.entries
        .filter { it.type != "income" && it.amount > 0 && it.date >= cutoff }
        .groupBy { it.cat to it.amount }
        .map { (key, rows) -> QuickAdd(key.first, key.second, rows.size) }
        .filter { it.count >= 2 }
        .sortedByDescending { it.count }
        .take(limit)
}


/** What one week of driving actually came to. */
data class WeeklyReview(
    val days: Int,
    val hours: Double,
    val net: Long,
    val perHour: Double,
    val fuelPerKm: Double,
    val bestDay: String,
    val bestNet: Long,
)

/** The week that starts on [weekStart]; null when nothing was driven. */
/**
 * The fuel category is "fuel" in current stores and "petrol" in older ones. Every query that
 * means "fuel" goes through here, so a store with either id reports fuel correctly.
 */
fun isFuelCat(id: String): Boolean = id == "fuel" || id == "petrol"

fun weeklyReview(store: Store, weekStart: String): WeeklyReview? {
    val end = addDays(weekStart, 7)
    val shifts = store.shifts.filter { it.date >= weekStart && it.date < end }
    if (shifts.isEmpty()) return null
    val cfg = store.settings.profit
    val month = weekStart.substring(0, 7)
    val overhead = overheadPerHour(store, month)
    val hours = shifts.sumOf { it.hours }
    val net = shifts.sumOf { shiftMetrics(it, cfg, overhead).net }
    val perHour = if (hours > 0.01) net / hours else 0.0
    val fuel = store.entries
        .filter { it.date >= weekStart && it.date < end && it.type != "income" && isFuelCat(it.cat) }
        .sumOf { it.amount }
    val km = shifts.sumOf { it.km }
    val fuelPerKm = if (km > 0.5) fuel / 100.0 / km else 0.0
    val byDay = shifts.groupBy { it.date }
        .mapValues { (_, rows) -> rows.sumOf { shiftMetrics(it, cfg, overhead).net } }
    val best = byDay.maxByOrNull { it.value }
    return WeeklyReview(
        days = byDay.size,
        hours = hours,
        net = (net * 100).roundToLong(),
        perHour = perHour,
        fuelPerKm = fuelPerKm,
        bestDay = best?.key.orEmpty(),
        bestNet = ((best?.value ?: 0.0) * 100).roundToLong(),
    )
}

/** The most recent week that has at least one shift, so the card is never empty. */
fun latestWeekWithData(store: Store, today: String): Pair<String, WeeklyReview>? {
    var start = weekStartOf(today)
    repeat(8) {
        weeklyReview(store, start)?.let { return start to it }
        start = addDays(start, -7)
    }
    return null
}


/**
 * Toll outlay for a month. Grab reimburses tolls instantly from its own trip data, so this is a
 * pass-through: the money returns inside gross earnings. It is shown for route decisions and for the
 * tax pack (where the toll and its reimbursement cancel), never as a cost that reduces net.
 */
data class TollSummary(val paid: Long, val reimbursed: Long) {
    val recordedBack: Boolean get() = reimbursed > 0
}

fun tollSummary(entries: List<Entry>, month: String): TollSummary {
    val inMonth = entries.filter { it.date.startsWith(month) }
    val paid = inMonth.filter { it.cat == "toll" && it.type != "income" }.sumOf { it.amount }
    val back = inMonth.filter { it.cat == "tollback" && it.type == "income" }.sumOf { it.amount }
    return TollSummary(paid, back)
}


/** How the imported statement compares with what the app logged for the same Monday–Sunday week. */
data class StatementCheck(
    val loggedGross: Long,
    val loggedShifts: Int,
    /** insurance windows the app derives for shifts that start in the week */
    val appInsurance: Int,
    val statementInsurance: Int,
) {
    val grossDelta: Long get() = loggedGross
    val insuranceMatches: Boolean get() = appInsurance == statementInsurance
}

fun statementCheck(store: Store, statement: Statement): StatementCheck {
    val start = statement.weekStart
    val end = addDays(start, 7)
    val week = store.shifts.filter { it.date >= start && it.date < end }
    val windows = coverWindows(store.shifts)
    val appInsurance = week.sumOf { windows[it.id] ?: 0 }
    return StatementCheck(
        loggedGross = week.sumOf { it.gross + it.incentive },
        loggedShifts = week.size,
        appInsurance = appInsurance,
        statementInsurance = statement.insuranceCount,
    )
}
