package app.baki

import kotlinx.datetime.LocalDate

/** One parsed statement row, still awaiting confirmation. */
data class DraftTxn(
    val accountName: String,
    val accountKind: String,
    val source: String,
    val date: String,
    val description: String,
    val amountCents: Long,
    val direction: String,
    val isTransfer: Boolean,
    val isInternal: Boolean = false,
)

private val AEON_DATE = Regex("""(?m)^\s*(\d{2})\s+([A-Z][a-z]{2})\s+(\d{2})\s+(.+)$""")
private val TNG_DATE = Regex("""(?m)^(\d{2})/(\d{2})/(\d{4})\s+(.+)$""")
private val RM = Regex("""-?\s*RM\s*[\d,]+\.\d{2}""")
private val MONTHS = mapOf("Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
    "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12)

private fun cents(raw: String): Long {
    val n = raw.replace("RM", "").replace(",", "").replace(" ", "").toDoubleOrNull() ?: return 0
    return kotlin.math.abs(kotlin.math.round(n * 100).toLong())
}

/** Extracts AEON Bank statement rows from the text layer. */
fun parseAeonStatement(text: String): List<DraftTxn> {
    val out = mutableListOf<DraftTxn>()
    val starts = AEON_DATE.findAll(text).toList()
    for ((i, m) in starts.withIndex()) {
        val date = runCatching {
            LocalDate(2000 + m.groupValues[3].toInt(), MONTHS[m.groupValues[2]] ?: return@runCatching null, m.groupValues[1].toInt())
        }.getOrNull() ?: continue
        val block = text.substring(m.range.first, starts.getOrNull(i + 1)?.range?.first ?: text.length)
        val first = RM.replace(m.groupValues[4], " ").trim()
        val tail = block.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .filterNot { AEON_DATE.matches(it) }
            .filterNot { it.startsWith("Transaction date:", true) }
            .take(2).joinToString(" ")
        val desc = ("$first $tail").replace(RM, " ").trim().replace(Regex("\\s+"), " ")
        if (desc.contains("Opening balance", true) || desc.contains("Closing balance", true)) continue
        val amounts = RM.findAll(block).map { it.value }.toList()
        if (amounts.size < 2) continue
        val amount = amounts[amounts.size - 2]
        val direction = if (amount.trimStart().startsWith("-")) "out" else "in"
        val transfer = desc.contains("Transfer to Pot", true) || desc.contains("Fund Transfer", true)
        val cleanDesc = desc.substringBefore("Transaction date:").trim()
        out += DraftTxn("AEON Bank — Savings Account-i", "bank", "aeon", date.toString(), cleanDesc, cents(amount), direction, transfer, isInternalTransfer(cleanDesc, "aeon"))
    }
    return out
}

/** Extracts TNG eWallet transaction rows from the text layer. */
fun parseTngStatement(text: String): List<DraftTxn> {
    val out = mutableListOf<DraftTxn>()
    val starts = TNG_DATE.findAll(text).toList()
    for ((i, m) in starts.withIndex()) {
        val date = runCatching { LocalDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
            .getOrNull() ?: continue
        val block = text.substring(m.range.first, starts.getOrNull(i + 1)?.range?.first ?: text.length)
        val amount = RM.find(block)?.value ?: continue
        val first = m.groupValues[4].substringBefore("RM").trim()
        val head = (first + " " + block.lineSequence().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.filterNot { it.startsWith("Date ") }.take(3).joinToString(" ")).replace(Regex("\\s+"), " ")
        val transfer = head.contains("Reload", true) || head.contains("Cash Out", true) || head.contains("Cash In", true) || head.contains("DUITNOW_TRANS", true) || head.contains("DUITNOW_RECEI", true)
        val direction = when {
            head.contains("Reload", true) || head.contains("DUITNOW_RECEI", true) -> "in"
            else -> "out"
        }
        val desc = head.substringAfter("Success", "").trim().ifBlank { "TNG transaction" }
        out += DraftTxn("TNG eWallet", "ewallet", "tng", date.toString(), desc, cents(amount), direction, transfer, isInternalTransfer(desc, "tng"))
    }
    return out
}


private fun csvDate(raw: String): String? {
    val v = raw.trim().trim('"')
    for ((pattern, transform) in listOf<Pair<Regex, (MatchResult) -> String>>(
        Regex("""^(\d{2})/(\d{2})/(\d{4})$""") to { m -> "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}" },
        Regex("""^(\d{4})-(\d{2})-(\d{2})$""") to { m -> m.value },
        Regex("""^(\d{2})\s+([A-Z][a-z]{2})\s+(\d{2})$""") to { m ->
            val month = MONTHS[m.groupValues[2]] ?: return@to ""
            "20${m.groupValues[3]}-${month.toString().padStart(2, '0')}-${m.groupValues[1]}"
        },
    )) {
        pattern.matchEntire(v)?.let { m -> val out = transform(m); if (out.isNotBlank()) return out }
    }
    return null
}

/** RFC4180-ish line splitter: quoted fields may contain delimiters and doubled quotes. */
private fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val out = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == delimiter && !quoted -> { out += cell.toString().trim(); cell.clear() }
            else -> cell.append(c)
        }
        i++
    }
    out += cell.toString().trim()
    return out
}

/** Provider-neutral CSV parser: header detection, common date/amount columns, signed/debit-credit. */
fun parseCsvStatement(text: String): List<DraftTxn> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()
    val delimiter = listOf(',', '\t', ';').maxByOrNull { c -> lines.first().count { it == c } } ?: ','
    fun split(line: String) = splitCsvLine(line, delimiter)
    val rows = lines.map { line ->
        split(line)
    }
    val header = rows.first().map { it.lowercase() }
    fun col(vararg names: String): Int = header.indexOfFirst { h -> names.any { h.contains(it) } }
    val dateCol = col("date", "transaction date", "posted")
    val descCol = col("description", "details", "merchant", "narrative", "reference")
    val amountCol = col("amount", "transaction amount", "value")
    val debitCol = col("debit", "withdrawal", "spent")
    val creditCol = col("credit", "deposit", "paid in")
    if (dateCol < 0 || descCol < 0 || (amountCol < 0 && debitCol < 0 && creditCol < 0)) return emptyList()
    val source = when {
        text.contains("TNG", true) -> "tng"
        text.contains("AEON", true) -> "aeon"
        else -> "csv"
    }
    val account = when (source) {
        "tng" -> "TNG eWallet"
        "aeon" -> "AEON Bank — Savings Account-i"
        else -> "Imported CSV"
    }
    val kind = if (source == "tng") "ewallet" else "bank"
    return rows.drop(1).mapNotNull { row ->
        if (row.size <= maxOf(dateCol, descCol, amountCol, debitCol, creditCol)) return@mapNotNull null
        val date = csvDate(row[dateCol]) ?: return@mapNotNull null
        val desc = row[descCol].replace(Regex("\\s+"), " ").trim()
        if (desc.isBlank()) return@mapNotNull null
        val debit = if (debitCol >= 0) row[debitCol] else ""
        val credit = if (creditCol >= 0) row[creditCol] else ""
        val amountRaw = when {
            debit.isNotBlank() -> "-$debit"
            credit.isNotBlank() -> credit
            amountCol >= 0 -> row[amountCol]
            else -> ""
        }
        val value = amountRaw.replace("RM", "").replace(",", "").replace("(", "-").replace(")", "").trim()
        val n = value.toDoubleOrNull() ?: return@mapNotNull null
        if (n == 0.0) return@mapNotNull null
        val transfer = desc.contains("transfer", true) || desc.contains("reload", true) || desc.contains("cash out", true) || desc.contains("top up", true)
        DraftTxn(account, kind, source, date, desc, kotlin.math.abs(kotlin.math.round(n * 100).toLong()), if (n < 0) "out" else "in", transfer, isInternalTransfer(desc, source))
    }
}

fun parseStatementText(text: String): List<DraftTxn> = when {
    text.contains("TNG WALLET TRANSACTION HISTORY", true) -> parseTngStatement(text)
    text.contains("AEON Bank", true) -> parseAeonStatement(text)
    else -> parseCsvStatement(text)
}

/** Plain keyword category hint; the phone form still lets the user change it. */
fun statementCategoryHint(description: String): String {
    val d = description.lowercase()
    return when {
        d.contains("petrol") || d.contains("fuel") || d.contains("petronas") || d.contains("shell") -> "fuel"
        d.contains("toll") || d.contains("plus") || d.contains("lpt") -> "toll"
        d.contains("restoran") || d.contains("food") || d.contains("mcd") || d.contains("kfc") -> "food"
        else -> "other"
    }
}


/** Same-app sub-account movement: Pot, GO+, reloads and wallet cash-outs. */
fun isInternalTransfer(description: String, source: String): Boolean {
    val d = description.lowercase()
    return when (source) {
        "aeon" -> d.contains("transfer to pot") || d.contains("transfer from pot") || d.contains(" pot")
        "tng" -> d.contains("go+") || d.contains("reload") || d.contains("cash out") || d.contains("cash in") || d.contains("ewallet cash")
        else -> d.contains("transfer to pot") || d.contains("go+") || d.contains("reload") || d.contains("cash out")
    }
}
