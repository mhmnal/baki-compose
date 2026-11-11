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

/** GPX 1.1 for one day's routes: opens in any map or fitness tool. */
fun exportGpx(date: String, routes: List<ShiftRoute>): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<gpx version="1.1" creator="Baki" xmlns="http://www.topografix.com/GPX/1/1">""")
    sb.appendLine("  <metadata><name>Baki $date</name></metadata>")
    routes.forEachIndexed { i, route ->
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>Shift ${i + 1} · ${route.date}</name>")
        sb.appendLine("    <trkseg>")
        route.points.forEach { p ->
            val time = kotlinx.datetime.Instant.fromEpochMilliseconds(p.t).toString()
            sb.appendLine("      <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>$time</time></trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
    }
    sb.appendLine("</gpx>")
    return sb.toString()
}

/** A print-ready monthly statement: plain HTML, so any browser can PDF it without a dependency. */
fun statementHtml(store: Store, month: String): String {
    val t = monthTotals(store, month)
    val shifts = store.shifts.filter { it.date.startsWith(month) }.sortedBy { it.date }
    val rows = store.entries.filter { it.date.startsWith(month) }.sortedBy { it.date }
        .joinToString("") { e ->
            "<tr><td>${e.date}</td><td>${e.cat}</td><td>${e.note.escapeHtml()}</td>" +
                "<td class=n>${if (e.type == "income") "+" else "−"}RM ${rmRaw(e.amount)}</td></tr>"
        }
    val shiftRows = shifts.joinToString("") { s ->
        val m = shiftMetrics(s, store.settings.profit)
        "<tr><td>${s.date} ${s.start}</td><td class=n>${"%.1f".format(s.hours)}</td>" +
            "<td class=n>${"%.1f".format(s.km)}</td><td class=n>RM ${rmRaw(s.gross)}</td>" +
            "<td class=n>RM ${rmRaw((m.net * 100).toLong())}</td></tr>"
    }
    return """<!doctype html><meta charset=utf-8><title>Baki · ${month}</title>
<style>
  body{font:13px/1.5 -apple-system,Segoe UI,Inter,sans-serif;color:#1b2417;max-width:760px;margin:40px auto;padding:0 24px}
  h1{font-size:22px;margin:0 0 2px} .sub{color:#5c6657;margin-bottom:24px}
  .hero{font-size:40px;font-weight:800;letter-spacing:-1px}
  table{width:100%;border-collapse:collapse;margin:12px 0 28px}
  th{text-align:left;font-size:10px;letter-spacing:.08em;color:#5c6657;border-bottom:1px solid #e2ddd0;padding:6px 4px}
  td{padding:6px 4px;border-bottom:1px solid #efeae0} td.n,th.n{text-align:right}
  h2{font-size:11px;letter-spacing:.08em;color:#5c6657;margin:24px 0 4px}
  @page{size:A4;margin:18mm}
  @media print{ body{margin:0} table{page-break-inside:auto} tr{page-break-inside:avoid} thead{display:table-header-group} h2{page-break-after:avoid} }
</style>
<h1>Baki · ${month}</h1><div class=sub>Shift logbook and spending</div>
<div class=hero>RM ${rmRaw(t.net)}</div>
<div class=sub>${t.entryCount} entries · ${t.shiftCount} shifts · in RM ${rmRaw(t.income)} · out RM ${rmRaw(t.expense)}</div>
<h2>SHIFTS</h2>
<table><thead><tr><th>Shift</th><th class=n>Hours</th><th class=n>KM</th><th class=n>Gross</th><th class=n>Net</th></tr></thead><tbody>$shiftRows</tbody></table>
<h2>ENTRIES</h2>
<table><thead><tr><th>Date</th><th>Category</th><th>Note</th><th class=n>Amount</th></tr></thead><tbody>$rows</tbody></table>
<div class=sub>Generated ${todayIso()} — figures in Malaysian ringgit.</div>"""
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
