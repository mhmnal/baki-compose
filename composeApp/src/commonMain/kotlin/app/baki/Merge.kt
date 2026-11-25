package app.baki

/**
 * Same rule as mergeStores() in index.html: newest `updated` wins per id, tombstones win
 * over everything, statements take the newest import per week, profit settings are last-writer.
 */
fun mergeStores(
    a: Store,
    b: Store,
    dead: Set<String> = emptySet(),
    deadCats: Set<String> = emptySet(),
    /** Keep this device's settings when it has an unsent local change. */
    keepLocalSettings: Boolean = false,
): Store {
    // tombstones are authoritative: a record deleted on any client must not come back
    val deadIds = dead + a.tomb.ids + b.tomb.ids
    val deadCategories = deadCats + a.tomb.cats + b.tomb.cats
    val entries = LinkedHashMap<String, Entry>()
    for (e in a.entries + b.entries) {
        if (e.id in deadIds) continue
        val cur = entries[e.id]
        if (cur == null || e.updated >= cur.updated) {
            // Older clients do not know about receipts. Do not erase one when the rest of an
            // entry is edited there; an explicit empty string means the receipt was removed.
            entries[e.id] = if (e.receipt == null && cur?.receipt != null) e.copy(receipt = cur.receipt) else e
        }
    }
    val shifts = LinkedHashMap<String, Shift>()
    for (s in a.shifts + b.shifts) {
        if (s.id in deadIds) continue
        val cur = shifts[s.id]
        if (cur == null || s.updated >= cur.updated) shifts[s.id] = s
    }
    val statements = LinkedHashMap<String, Statement>(b.statements)
    for ((week, st) in a.statements) {
        val cur = statements[week]
        if (cur == null || st.importedAt > cur.importedAt) statements[week] = st
    }
    val catsIn = LinkedHashMap<String, Cat>()
    for (c in a.settings.cats.catsIn + b.settings.cats.catsIn) if (c.id !in deadCategories) catsIn[c.id] = c
    val catsOut = LinkedHashMap<String, Cat>()
    for (c in a.settings.cats.catsOut + b.settings.cats.catsOut) if (c.id !in deadCategories) catsOut[c.id] = c

    val dayNotes = LinkedHashMap<String, DayNote>()
    for ((date, note) in a.dayNotes + b.dayNotes) {
        val cur = dayNotes[date]
        if (cur == null || note.updated >= cur.updated) dayNotes[date] = note
    }
    val weekCloses = LinkedHashMap<String, WeekClose>()
    for ((week, close) in a.weekCloses + b.weekCloses) {
        val cur = weekCloses[week]
        if (cur == null || close.updated >= cur.updated) weekCloses[week] = close
    }
    val dayCloses = LinkedHashMap<String, DayClose>()
    for ((date, close) in a.dayCloses + b.dayCloses) {
        val cur = dayCloses[date]
        if (cur == null || close.updated >= cur.updated) dayCloses[date] = close
    }
    val statementMarks = LinkedHashMap<String, StatementMark>()
    for ((week, mark) in a.statementMarks + b.statementMarks) {
        val cur = statementMarks[week]
        if (cur == null || mark.updated >= cur.updated) statementMarks[week] = mark
    }
    val odometerReadings = LinkedHashMap<String, OdometerReading>()
    for (r in a.odometerReadings + b.odometerReadings) {
        val cur = odometerReadings[r.id]
        if (cur == null || r.updated >= cur.updated) odometerReadings[r.id] = r
    }
    val reserveTxns = LinkedHashMap<String, ReserveTxn>()
    for (tx in a.reserveTxns + b.reserveTxns) {
        if (tx.id in deadIds) continue
        val cur = reserveTxns[tx.id]
        if (cur == null || tx.updated >= cur.updated) reserveTxns[tx.id] = tx
    }
    val envelopeMoves = LinkedHashMap<String, Map<String, Double>>()
    for (source in listOf(a.envelopeMoves, b.envelopeMoves)) {
        for ((month, moves) in source) {
            envelopeMoves[month] = (envelopeMoves[month].orEmpty() + moves)
        }
    }

    return Store(
        v = 1,
        updatedAt = b.updatedAt.ifEmpty { a.updatedAt },
        entries = entries.values.toList(),
        shifts = shifts.values.toList(),
        statements = statements,
        settings = if (keepLocalSettings) {
            // A local setting change is still in the outbox. Do not let a pull from a stale
            // remote overwrite it before the push reaches the server.
            a.settings
        } else Settings(
            name = b.settings.name.ifEmpty { a.settings.name },
            cats = Cats(catsIn.values.toList(), catsOut.values.toList()),
            profit = b.settings.profit,
            gpsCorrection = b.settings.gpsCorrection,
            budgets = b.settings.budgets.ifEmpty { a.settings.budgets },
            dailyFoodCapCents = b.settings.dailyFoodCapCents,
            nextServiceKm = b.settings.nextServiceKm,
            nextServiceOn = b.settings.nextServiceOn,
            // Older servers do not carry the Phase 3 settings fields. Do not erase a locally
            // configured envelope/reserve just because the remote payload predates them.
            envelopeCarry = b.settings.envelopeCarry || a.settings.envelopeCarry,
            reserveTargets = a.settings.reserveTargets + b.settings.reserveTargets,
            serviceOdoBase = b.settings.serviceOdoBase ?: a.settings.serviceOdoBase,
            serviceOdoBaseOn = b.settings.serviceOdoBaseOn ?: a.settings.serviceOdoBaseOn,
        ),
        // bills merge by id (newest wins); a payment is unique per bill+period
        bills = (a.bills + b.bills)
            .groupBy { it.id }
            .map { (_, list) -> list.maxByOrNull { it.updated }!! },
        billPayments = (a.billPayments + b.billPayments)
            .groupBy { it.billId to it.period }
            .map { (_, list) -> list.maxByOrNull { it.updated }!! },
        dayNotes = dayNotes,
        dayCloses = dayCloses,
        weekCloses = weekCloses,
        statementMarks = statementMarks,
        envelopeMoves = envelopeMoves,
        reserveTxns = reserveTxns.values.toList(),
        odometerReadings = odometerReadings.values.toList(),
        // keep the union, so a removal is never forgotten by a later merge
        tomb = Tomb(
            ids = (a.tomb.ids + b.tomb.ids).distinct(),
            cats = (a.tomb.cats + b.tomb.cats).distinct(),
        ),
    )
}


/**
 * How many records changed on both sides since the last shared point. This is a *report*, not a
 * resolver: [mergeStores] uses newest-`updated` wins and tombstones win. Surfacing the count stops
 * a cross-device overlap from disappearing silently.
 */
data class MergeReport(
    val entries: Int = 0,
    val shifts: Int = 0,
    val bills: Int = 0,
    val settings: Boolean = false,
) {
    val total: Int get() = entries + shifts + bills + if (settings) 1 else 0
}

fun mergeReport(a: Store, b: Store): MergeReport {
    val dead = a.tomb.ids + b.tomb.ids
    val ae = a.entries.associateBy { it.id }
    val be = b.entries.associateBy { it.id }
    val asx = a.shifts.associateBy { it.id }
    val bsx = b.shifts.associateBy { it.id }
    val aber = a.bills.associateBy { it.id }
    val bber = b.bills.associateBy { it.id }
    val entries = ae.keys.intersect(be.keys).count { id ->
        id !in dead && ae[id] != be[id] && ae[id]?.updated != be[id]?.updated
    }
    val shifts = asx.keys.intersect(bsx.keys).count { id ->
        id !in dead && asx[id] != bsx[id] && asx[id]?.updated != bsx[id]?.updated
    }
    val bills = aber.keys.intersect(bber.keys).count { id ->
        aber[id] != bber[id] && aber[id]?.updated != bber[id]?.updated
    }
    val settings = a.settings != b.settings
    return MergeReport(entries, shifts, bills, settings)
}
