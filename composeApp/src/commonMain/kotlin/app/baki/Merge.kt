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

    return Store(
        v = 1,
        updatedAt = b.updatedAt.ifEmpty { a.updatedAt },
        entries = entries.values.toList(),
        shifts = shifts.values.toList(),
        statements = statements,
        settings = Settings(
            name = b.settings.name.ifEmpty { a.settings.name },
            cats = Cats(catsIn.values.toList(), catsOut.values.toList()),
            profit = b.settings.profit,
            gpsCorrection = b.settings.gpsCorrection,
            budgets = b.settings.budgets,
            dailyFoodCapCents = b.settings.dailyFoodCapCents,
            nextServiceKm = b.settings.nextServiceKm,
            nextServiceOn = b.settings.nextServiceOn,
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
        statementMarks = statementMarks,
        // keep the union, so a removal is never forgotten by a later merge
        tomb = Tomb(
            ids = (a.tomb.ids + b.tomb.ids).distinct(),
            cats = (a.tomb.cats + b.tomb.cats).distinct(),
        ),
    )
}
