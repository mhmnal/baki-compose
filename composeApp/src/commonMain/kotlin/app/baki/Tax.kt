package app.baki

/**
 * The Borang B picture for one year: money in from every source, money out by category, the mileage
 * that supports a business-use claim, and the resulting net business income.
 *
 * Malaysian e-hailing income is business income, so the return is Borang B; LHDN expects mileage logs
 * and receipts kept for seven years, and only the business share of vehicle costs is claimable.
 */
data class TaxLine(val id: String, val label: String, val amount: Long, val deductible: Boolean)

data class TaxYear(
    val year: Int,
    /** logged earnings from shifts (gross fares + incentives) */
    val shiftIncome: Long,
    /** standalone income entries (tips, bonuses, cash fares) */
    val entryIncome: Long,
    /** what Grab's imported statements say, for cross-checking — not added to income */
    val statementIncome: Double,
    val statementsImported: Int,
    val lines: List<TaxLine>,
    /** vehicle costs allowed after the business-use percentage */
    val deductible: Long,
    /** kilometres driven while working, from shifts */
    val businessKm: Double,
    val totalKm: Double,
    val businessUse: Double,
    val insuranceWindows: Int,
    val netBusinessIncome: Long,
) {
    val income: Long get() = shiftIncome + entryIncome
}

private val VEHICLE_CATS = setOf("fuel", "toll", "parking", "carwash", "maintenance")
private val BUSINESS_CATS = setOf("phone", "grabfee", "roadtax", "subscriptions")
private val NEVER = setOf("food", "other", "rent", "carloan")

fun taxYear(store: Store, year: Int, totalKmInput: Double? = null): TaxYear {
    val y = year.toString()
    val entries = store.entries.filter { it.date.startsWith(y) }
    val shifts = store.shifts.filter { it.date.startsWith(y) }
    val statements = store.statements.values.filter { it.weekStart.startsWith(y) }

    val shiftIncome = shifts.sumOf { it.gross + it.incentive }
    val entryIncome = entries.filter { it.type == "income" }.sumOf { it.amount }

    val names = store.settings.cats.catsOut.associate { it.id to it.name } +
        store.settings.cats.catsIn.associate { it.id to it.name }
    val lines = entries
        .filter { it.type != "income" }
        .groupBy { it.cat }
        .map { (cat, list) ->
            TaxLine(
                id = cat,
                label = names[cat] ?: cat,
                amount = list.sumOf { it.amount },
                deductible = cat !in NEVER && (cat in VEHICLE_CATS || cat in BUSINESS_CATS),
            )
        }
        .sortedByDescending { it.amount }

    val businessKm = shifts.sumOf { shiftKm(it, store.settings.profit) }
    val totalKm = totalKmInput?.takeIf { it >= businessKm && it > 0 } ?: businessKm
    val use = if (totalKm > 0.0) (businessKm / totalKm).coerceIn(0.0, 1.0) else 1.0

    val deductible = lines.filter { it.deductible }.sumOf { line ->
        if (line.id in VEHICLE_CATS) (line.amount * use).toLong() else line.amount
    }
    val windows = coverWindows(shifts).values.sum()

    return TaxYear(
        year = year,
        shiftIncome = shiftIncome,
        entryIncome = entryIncome,
        statementIncome = statements.sumOf { it.income },
        statementsImported = statements.size,
        lines = lines,
        deductible = deductible,
        businessKm = businessKm,
        totalKm = totalKm,
        businessUse = use,
        insuranceWindows = windows,
        netBusinessIncome = shiftIncome + entryIncome - deductible,
    )
}

/** Printable summary. Deliberately plain: an accountant or LHDN officer should read it without context. */
fun taxPackHtml(t: TaxYear, driver: String = "Baki"): String {
    fun rmRaw(c: Long) = "RM " + "%,.2f".format(c / 100.0)
    val rows = t.lines.joinToString("") { line ->
        "<tr><td>${line.label}</td><td class='n'>${rmRaw(line.amount)}</td>" +
            "<td class='n'>${if (line.deductible) "yes" else "—"}</td></tr>"
    }
    return """
<!doctype html><html><head><meta charset="utf-8"><title>Baki tax pack ${t.year}</title>
<style>
  @page { size: A4; margin: 18mm }
  body { font: 12px/1.5 -apple-system, Segoe UI, Roboto, sans-serif; color: #222; }
  h1 { font-size: 20px; margin: 0 0 4px } h2 { font-size: 14px; margin: 22px 0 6px }
  table { width: 100%; border-collapse: collapse } th, td { padding: 5px 6px; border-bottom: 1px solid #e3e6ea; text-align: left }
  td.n, th.n { text-align: right; font-variant-numeric: tabular-nums }
  .muted { color: #667 } .big { font-size: 16px; font-weight: 600 }
</style></head><body>
<h1>Tax pack ${t.year} — e-hailing (Borang B)</h1>
<div class="muted">Prepared from Baki records. Keep this with your receipts for seven years.</div>

<h2>Income</h2>
<table>
  <tr><td>Logged shift earnings</td><td class="n">${rmRaw(t.shiftIncome)}</td></tr>
  <tr><td>Other income entries (tips, bonuses, cash)</td><td class="n">${rmRaw(t.entryIncome)}</td></tr>
  <tr><td class="big">Total income</td><td class="n big">${rmRaw(t.income)}</td></tr>
</table>
<div class="muted">Grab statements imported: ${t.statementsImported}
${if (t.statementsImported > 0) "· statement income ${"%,.2f".format(t.statementIncome)} (cross-check, not added)" else ""}</div>

<h2>Expenses</h2>
<table><tr><th>Category</th><th class="n">Amount</th><th class="n">Deductible</th></tr>
$rows
<tr><td class="big">Deductible after ${"%.0f".format(t.businessUse * 100)}% business use</td><td class="n big">${rmRaw(t.deductible)}</td></tr>
</table>

<h2>Mileage</h2>
<table>
  <tr><td>Kilometres while working</td><td class="n">${"%,.0f".format(t.businessKm)} km</td></tr>
  <tr><td>Total kilometres for the year</td><td class="n">${"%,.0f".format(t.totalKm)} km</td></tr>
  <tr><td>Business-use share</td><td class="n">${"%.0f".format(t.businessUse * 100)}%</td></tr>
  <tr><td>Daily insurance charges (Grab)</td><td class="n">${t.insuranceWindows}</td></tr>
</table>

<h2>Result</h2>
<div class="big">Net business income: ${rmRaw(t.netBusinessIncome)}</div>
<div class="muted">Tolls are reimbursed inside Grab fares and are not a separate deduction. Food and other
personal spending are listed but not deducted. Only the business share of vehicle costs is claimed.</div>
</body></html>
""".trimIndent()
}

fun taxPackCsv(t: TaxYear): String {
    val sb = StringBuilder()
    sb.appendLine("kind,label,amount_rm,deductible")
    sb.appendLine("income,Shift earnings,${t.shiftIncome / 100.0},yes")
    sb.appendLine("income,Other income,${t.entryIncome / 100.0},yes")
    t.lines.forEach { sb.appendLine("expense,${it.label.csvEsc()},${it.amount / 100.0},${if (it.deductible) "yes" else "no"}") }
    sb.appendLine("total,Income,${t.income / 100.0},")
    sb.appendLine("total,Deductible,${t.deductible / 100.0},")
    sb.appendLine("total,Net business income,${t.netBusinessIncome / 100.0},")
    sb.appendLine("mileage,Business km,${"%.0f".format(t.businessKm)},")
    sb.appendLine("mileage,Total km,${"%.0f".format(t.totalKm)},")
    sb.appendLine("mileage,Business use,${"%.2f".format(t.businessUse)},")
    return sb.toString()
}

private fun String.csvEsc(): String =
    if (any { it == ',' || it == '"' || it == '\n' }) "\"" + replace("\"", "\"\"") + "\"" else this
