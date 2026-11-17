package app.baki

/** Full store as pretty JSON — the same shape the server and the web app use. */
fun backupJson(store: Store): String =
    kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }
        .encodeToString(Store.serializer(), store)

@kotlinx.serialization.Serializable
data class BakiBackupBundle(
    val format: String = "baki-backup-v2",
    val exportedAt: String = nowIso(),
    val store: Store,
    val config: ClientConfig? = null,
    val calibration: CalibrationFile? = null,
    val routes: List<ShiftRoute> = emptyList(),
)

/** Portable backup: ledger, device settings, calibration and local routes. */
suspend fun backupBundleJson(repo: BakiRepo): String {
    val bundle = BakiBackupBundle(
        exportedAt = nowIso(),
        store = repo.store,
        config = repo.config,
        calibration = Calibration.snapshot(),
        routes = RouteStore.allRoutes(),
    )
    return kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }
        .encodeToString(BakiBackupBundle.serializer(), bundle)
}

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


/** A file-picker summary shown before a restore mutates anything. */
data class BackupSummary(
    val exportedAt: String,
    val entries: Int,
    val shifts: Int,
    val bills: Int,
    val statements: Int,
    val routes: Int,
    val hasDeviceConfig: Boolean,
    val bytes: Int,
)

fun summarizeBackup(text: String): BackupSummary? {
    val bundle = runCatching {
        BakiJson.decodeFromString(BakiBackupBundle.serializer(), text)
    }.getOrNull()
    val store = bundle?.store ?: runCatching {
        BakiJson.decodeFromString(Store.serializer(), text)
    }.getOrNull() ?: return null
    return BackupSummary(
        exportedAt = bundle?.exportedAt ?: store.updatedAt,
        entries = store.entries.size,
        shifts = store.shifts.size,
        bills = store.bills.count { it.active },
        statements = store.statements.size,
        routes = bundle?.routes?.size ?: 0,
        hasDeviceConfig = bundle?.config != null,
        bytes = text.length,
    )
}

/** Portable backup envelope: plaintext JSON, or this AES-GCM wrapper when a passphrase is used. */
const val ENCRYPTED_BACKUP_PREFIX = "BAKI-BACKUP-1:"

fun encryptBackup(json: String, passphrase: String): String? {
    if (passphrase.isBlank()) return null
    return runCatching {
        ENCRYPTED_BACKUP_PREFIX + RouteBase64.encode(
            RouteCrypto.seal(passphrase, json.encodeToByteArray()),
        )
    }.getOrNull()
}

fun isEncryptedBackup(text: String): Boolean = text.trimStart().startsWith(ENCRYPTED_BACKUP_PREFIX)

fun decryptBackup(text: String, passphrase: String): String? {
    if (!isEncryptedBackup(text) || passphrase.isBlank()) return null
    return runCatching {
        val sealed = RouteBase64.decode(text.trimStart().removePrefix(ENCRYPTED_BACKUP_PREFIX))
        RouteCrypto.open(passphrase, sealed).decodeToString()
    }.getOrNull()
}

/** Plain annual ledger CSV. No tax classification: tax handling lives outside Baki. */
fun annualCsv(store: Store, year: Int): String {
    val y = year.toString()
    val sb = StringBuilder()
    sb.appendLine("kind,date,time,category,note,amount_rm,hours,km,net_rm")
    for (e in store.entries.filter { it.date.startsWith(y) }.sortedBy { it.date }) {
        val sign = if (e.type == "income") "" else "-"
        sb.appendLine(listOf("entry", e.date, "", e.cat, e.note.csv(), "$sign${e.amount / 100.0}", "", "", "").joinToString(","))
    }
    for (s in store.shifts.filter { it.date.startsWith(y) }.sortedBy { it.date }) {
        val m = shiftMetrics(s, store.settings.profit)
        sb.appendLine(
            listOf("shift", s.date, s.start, "shift", s.note.csv(), s.gross / 100.0, s.hours, "%.1f".format(m.km), "%.2f".format(m.net)).joinToString(","),
        )
    }
    return sb.toString()
}

/** Clean printable annual summary; deliberately no tax calculations or deductible claims. */
fun annualSummaryHtml(store: Store, year: Int): String {
    val y = year.toString()
    val months = (1..12).map { "%04d-%02d".format(year, it) to monthTotals(store, "%04d-%02d".format(year, it)) }
    val total = months.sumOf { it.second.net }
    val shifts = store.shifts.filter { it.date.startsWith(y) }
    val km = shifts.sumOf { shiftKm(it, store.settings.profit) }
    val hours = shifts.sumOf { it.hours }
    val rows = months.joinToString("") { (m, t) ->
        "<tr><td>${m}</td><td class=n>RM ${rmRaw(t.income)}</td><td class=n>RM ${rmRaw(t.expense)}</td><td class=n>RM ${rmRaw(t.net)}</td></tr>"
    }
    return """<!doctype html><meta charset=utf-8><title>Baki annual summary $year</title>
<style>body{font:13px/1.5 -apple-system,Segoe UI,sans-serif;max-width:760px;margin:40px auto;padding:0 24px;color:#1b2417}
h1{font-size:22px;margin:0}.sub{color:#5c6657}.hero{font-size:40px;font-weight:800;margin:12px 0}
table{width:100%;border-collapse:collapse;margin:16px 0}th,td{padding:6px 4px;border-bottom:1px solid #e2ddd0}th{text-align:left;font-size:10px;letter-spacing:.08em;color:#5c6657}.n{text-align:right}
@media print{body{margin:0}@page{size:A4;margin:18mm}}</style>
<h1>Baki annual summary $year</h1><div class=sub>Shift logbook and spending. Tax handling is external.</div>
<div class=hero>RM ${rmRaw(total)}</div>
<div class=sub>${months.sumOf { it.second.entryCount }} entries · ${shifts.size} shifts · ${"%.0f".format(km)} km · ${"%.1f".format(hours)}h</div>
<table><thead><tr><th>Month</th><th class=n>In</th><th class=n>Out</th><th class=n>Net</th></tr></thead><tbody>$rows</tbody></table>
<div class=sub>Generated ${todayIso()}. This is a record summary, not a tax return.</div>"""
}
