package app.baki

/** Full store as pretty JSON — the same shape the server and the web app use. */
fun backupJson(store: Store): String =
    kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }
        .encodeToString(Store.serializer(), store)

/** Entries and shifts as CSV, for spreadsheets. */
fun exportCsv(store: Store): String {
    val sb = StringBuilder()
    sb.appendLine("kind,date,time,category,note,amount_rm,payout_rm,hours,km,band,net_rm")
    for (e in store.entries.sortedBy { it.date }) {
        val sign = if (e.type == "income") "" else "-"
        sb.appendLine(listOf("entry", e.date, "", e.cat, e.note.csv(), "$sign${e.amount / 100.0}", "", "", "", "", "").joinToString(","))
    }
    for (s in store.shifts.sortedBy { it.date }) {
        val m = shiftMetrics(s, store.settings.profit)
        sb.appendLine(
            listOf(
                "shift", s.date, s.start, "shift", s.note.csv(), s.gross / 100.0, s.gross / 100.0,
                s.hours, "%.1f".format(m.km), m.band, "%.2f".format(m.net),
            ).joinToString(",")
        )
    }
    return sb.toString()
}

private fun String.csv(): String =
    if (any { it == ',' || it == '"' || it == '\n' }) "\"" + replace("\"", "\"\"") + "\"" else this

fun todayStamp(): String = todayIso().replace("-", "")
