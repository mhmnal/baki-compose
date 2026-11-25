package app.baki

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Synthetic preview history. Only the desktop preview shell uses this; it never touches the
 * production store or the sync server.
 *
 * The same seed and end date produce the same Store, so screenshots and bug reports are stable.
 */
object PreviewScenario {
    private val zone = TimeZone.currentSystemDefault()

    fun generate(
        end: LocalDate = LocalDate.parse(todayIso()),
        seed: Long = nowMillis(),
        days: Int = 75,
    ): Store {
        val base = Store()
        val catIds = (base.settings.cats.catsOut + base.settings.cats.catsIn).map { it.id }.toSet()
        fun cat(vararg preferred: String): String =
            preferred.firstOrNull { it in catIds } ?: preferred.firstOrNull() ?: "other"

        val food = cat("food", "meals")
        val fuel = cat("fuel", "petrol")
        val toll = cat("toll")
        val parking = cat("parking")
        val wear = cat("wear", "maintenance")
        val other = cat("other")
        val income = cat("income", "other")

        val entries = mutableListOf<Entry>()
        val shifts = mutableListOf<Shift>()
        val odometers = mutableListOf<OdometerReading>()
        val reserve = mutableListOf<ReserveTxn>()
        var odo = 38_400
        var fuelCounter = 0

        for (offset in (days - 1) downTo 0) {
            val date = end.minus(offset, DateTimeUnit.DAY)
            val dateIso = date.toString()
            val r = Random(seed xor (date.toEpochDays() * 1_000_003L))
            val today = date == end
            val working = today || r.nextDouble() < 0.70
            val tripCount = if (working) {
                if (r.nextDouble() < 0.20) 2 else 1
            } else 0

            repeat(tripCount) { index ->
                val startHour = if (index == 0) r.nextInt(7, 11) else r.nextInt(16, 19)
                val startMinute = if (r.nextBoolean()) 0 else 30
                val hours = r.nextDouble(3.8, if (index == 0) 7.8 else 5.8)
                val km = r.nextDouble(55.0, if (index == 0) 190.0 else 130.0)
                val gross = r.nextLong(9_500, 29_500)
                val incentive = if (r.nextDouble() < 0.35) r.nextLong(300, 3_500) else 0
                val payoutAt = LocalDateTime(date.plus(if (r.nextDouble() < 0.25) 2 else 1, DateTimeUnit.DAY), LocalTime(10, 15))
                    .toInstant(zone).toEpochMilliseconds()
                val full = gross + incentive
                val payouts = when {
                    r.nextDouble() < 0.62 -> listOf(Payout(full, payoutAt))
                    r.nextDouble() < 0.78 -> listOf(Payout((full * r.nextDouble(0.45, 0.88)).roundToLong(), payoutAt))
                    r.nextDouble() < 0.90 -> emptyList()
                    else -> listOf(Payout(full + r.nextLong(100, 1_200), payoutAt))
                }
                val stamp = at(date, startHour, startMinute)
                shifts += Shift(
                    id = "preview-shift-$dateIso-$index",
                    date = dateIso,
                    start = "%02d:%02d".format(startHour, startMinute),
                    hours = (hours * 10).roundToLong() / 10.0,
                    km = (km * 10).roundToLong() / 10.0,
                    odoStart = odo.toDouble(),
                    odoEnd = (odo + km).toDouble(),
                    gross = gross,
                    incentive = incentive,
                    grabFee = null,
                    note = if (index == 0) "preview run" else "evening run",
                    updated = stamp,
                    payouts = payouts,
                )
                odo += km.roundToLong().toInt()
            }

            fun expense(amount: Long, category: String, note: String, withOdo: Int? = null) {
                val stamp = at(date, 12, 0)
                entries += Entry(
                    id = "preview-entry-${entries.size}",
                    date = dateIso,
                    type = "expense",
                    cat = category,
                    amount = amount.coerceAtLeast(1),
                    note = note,
                    updated = stamp,
                    loggedAt = stamp,
                    odo = withOdo,
                )
            }

            if (working) {
                expense(r.nextLong(900, 2_800), food, "preview lunch")
                if (r.nextBoolean()) expense(r.nextLong(150, 700), food, "preview coffee")
                if (r.nextBoolean()) expense(r.nextLong(150, 950), toll, "preview toll")
                if (r.nextDouble() < 0.52) {
                    fuelCounter++
                    val hasOdo = fuelCounter % 3 != 0
                    val fillOdo = if (hasOdo) {
                        odo += 0
                        odo
                    } else null
                    expense(r.nextLong(4_200, 8_800), fuel, "preview fuel", fillOdo)
                    if (hasOdo) {
                        odometers += OdometerReading(
                            id = "preview-odo-${odometers.size}",
                            date = dateIso,
                            km = fillOdo ?: odo,
                            note = "preview fill",
                            updated = at(date, 12, 0),
                        )
                    }
                }
                if (r.nextDouble() < 0.28) expense(r.nextLong(100, 500), parking, "preview parking")
                if (r.nextDouble() < 0.18) expense(r.nextLong(600, 2_400), other, "preview supplies")
            } else {
                expense(r.nextLong(1_200, 3_800), food, "preview groceries")
                if (r.nextDouble() < 0.35) expense(r.nextLong(2_500, 9_000), other, "preview household")
                if (r.nextDouble() < 0.15) expense(r.nextLong(800, 2_500), wear, "preview repair")
            }

            if (r.nextDouble() < 0.14) {
                val stamp = at(date, 19, 0)
                entries += Entry(
                    id = "preview-entry-${entries.size}",
                    date = dateIso,
                    type = "income",
                    cat = income,
                    amount = r.nextLong(400, 2_500),
                    note = "preview tip",
                    updated = stamp,
                    loggedAt = stamp,
                )
            }

            if (date.dayOfMonth == 1 || date.dayOfMonth == 15) {
                val fund = if (date.dayOfMonth == 1) "fuel" else "wear"
                reserve += ReserveTxn(
                    id = "preview-reserve-${reserve.size}",
                    fund = fund,
                    amount = if (fund == "fuel") 6_000 else 4_000,
                    date = dateIso,
                    note = "preview reserve top-up",
                    updated = at(date, 9, 0),
                )
            }
        }

        val bills = listOf(
            Bill(id = "preview-bill-phone", name = "Phone", amount = 6_000, category = other, dueDay = 5, updated = at(end, 9, 0)),
            Bill(id = "preview-bill-rent", name = "Room", amount = 85_000, category = other, dueDay = 1, updated = at(end, 9, 0)),
            Bill(id = "preview-bill-insurance", name = "Car insurance", amount = 12_000, cadence = "annual", anchorMonth = 11, dueDay = 20, category = other, updated = at(end, 9, 0)),
            Bill(id = "preview-bill-missing", name = "Gym", amount = 5_000, category = other, dueDay = null, updated = at(end, 9, 0)),
            Bill(id = "preview-bill-old", name = "Old subscription", amount = 2_500, category = other, dueDay = 12, updated = at(end, 9, 0)),
        )
        val payments = mutableListOf<BillPayment>()
        fun payBill(bill: Bill, period: String, date: LocalDate, active: Boolean = true) {
            val stamp = at(date, 9, 0)
            val entryId = "preview-bill-entry-${entries.size}"
            entries += Entry(
                id = entryId,
                date = date.toString(),
                type = "expense",
                cat = bill.category,
                amount = bill.amount,
                note = "${bill.name} · $period",
                updated = stamp,
                loggedAt = stamp,
            )
            payments += BillPayment(
                billId = bill.id,
                period = period,
                amount = bill.amount,
                entryId = entryId,
                paidAt = stamp,
                updated = stamp,
                active = active,
            )
        }
        val thisMonth = LocalDate(end.year, end.month, 1)
        val monthPeriod = "%04d-%02d".format(thisMonth.year, thisMonth.monthNumber)
        payBill(bills[0], monthPeriod, end.minus(4, DateTimeUnit.DAY))
        payBill(bills[1], monthPeriod, end.minus(2, DateTimeUnit.DAY))
        payments += BillPayment(
            billId = bills[4].id,
            period = "2026-10",
            amount = bills[4].amount,
            entryId = "preview-missing-link",
            paidAt = at(end.minus(30, DateTimeUnit.DAY), 9, 0),
            updated = at(end.minus(30, DateTimeUnit.DAY), 9, 0),
            active = false,
        )

        val statements = mutableMapOf<String, Statement>()
        val marks = mutableMapOf<String, StatementMark>()
        val weeks = mutableMapOf<String, WeekClose>()
        val startMonday = mondayOf(end)
        for (i in 1..5) {
            val weekStart = startMonday.minus(i * 7, DateTimeUnit.DAY)
            val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
            val weekShifts = shifts.filter { it.date >= weekStart.toString() && it.date <= weekEnd.toString() }
            if (weekShifts.isEmpty()) continue
            val incomeCents = weekShifts.sumOf { it.gross + it.incentive }
            val incomeRm = incomeCents / 100.0
            val deductions = (incomeRm * 0.075).roundToLong() / 100.0
            val weekly = (incomeRm - deductions).coerceAtLeast(0.0)
            val key = weekStart.toString()
            statements[key] = Statement(
                weekStart = key,
                rangeText = "${weekStart.dayOfMonth}–${weekEnd.dayOfMonth} ${weekEnd.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                income = incomeRm,
                deductions = deductions,
                weekly = weekly,
                insuranceCount = weekShifts.size,
                consistent = true,
                importedAt = at(weekEnd.plus(2, DateTimeUnit.DAY), 9, 0),
            )
            marks[key] = StatementMark(reconciled = i > 2, updated = at(weekEnd, 20, 0))
            if (i > 1) {
                val received = (weekly * 100).roundToLong() - if (i == 3) 2_100 else 0
                weeks[key] = WeekClose(
                    weekStart = key,
                    receivedCents = received,
                    closedAt = at(weekEnd.plus(1, DateTimeUnit.DAY), 20, 0),
                    updated = at(weekEnd.plus(1, DateTimeUnit.DAY), 20, 0),
                )
            }
        }

        val settings = base.settings.copy(
            dailyFoodCapCents = 2_500,
            nextServiceKm = 40_000,
            nextServiceOn = end.plus(2, DateTimeUnit.MONTH).toString(),
            budgets = mapOf(food to 450.0, fuel to 280.0, toll to 90.0),
            reserveTargets = mapOf("fuel" to 180.0, "wear" to 140.0),
        )

        return Store(
            updatedAt = at(end, 12, 0),
            entries = entries,
            shifts = shifts,
            statements = statements,
            settings = settings,
            bills = bills,
            billPayments = payments,
            weekCloses = weeks,
            statementMarks = marks,
            reserveTxns = reserve,
            odometerReadings = odometers,
        )
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): String =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone).toString()

    private fun mondayOf(date: LocalDate): LocalDate =
        date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
}
