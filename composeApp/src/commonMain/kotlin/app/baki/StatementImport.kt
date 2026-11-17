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
        out += DraftTxn("AEON Bank — Savings Account-i", "bank", "aeon", date.toString(), cleanDesc, cents(amount), direction, transfer)
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
        out += DraftTxn("TNG eWallet", "ewallet", "tng", date.toString(), desc, cents(amount), direction, transfer)
    }
    return out
}

fun parseStatementText(text: String): List<DraftTxn> = when {
    text.contains("TNG WALLET TRANSACTION HISTORY", true) -> parseTngStatement(text)
    text.contains("AEON Bank", true) -> parseAeonStatement(text)
    else -> emptyList()
}
