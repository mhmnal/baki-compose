package app.baki

/** One expense recovered from free text. */
data class QuickEntry(val cat: String, val cents: Long)

/** What a typed line means, before anyone commits it. */
data class QuickParse(
    val date: String,
    val entries: List<QuickEntry>,
    /** amounts with no category word near them; the UI must ask instead of guessing */
    val loose: List<Long>,
    /** payouts found in the text ("payout 32.50") */
    val payouts: List<Long>,
) {
    val empty: Boolean get() = entries.isEmpty() && payouts.isEmpty() && loose.isEmpty()
}

private class Token(val text: String, val num: Long?)

/** English and Malay words that mean one of your categories. */
private val ALIASES = mapOf(
    "toll" to listOf("toll", "tolls", "tol"),
    "petrol" to listOf("petrol", "fuel", "gas", "minyak", "petrol"),
    "parking" to listOf("parking", "parkir"),
    "food" to listOf("food", "makan", "lunch", "dinner", "breakfast", "kopi"),
    "carwash" to listOf("wash", "cuci", "carwash"),
    "service" to listOf("service", "servis", "workshop"),
)

/** Payouts are not a spending category, so they are matched on their own words. */
private val PAYOUT_WORDS = setOf("payout", "payouts", "dapat", "earning", "earnings")

private val DATE_WORDS = mapOf(
    "yesterday" to -1, "semalam" to -1, "kelmarin" to -2, "today" to 0,
)

/** A single line entry above RM 1,000 is a typo or an odometer, not an expense. */
private const val MAX_CENTS = 100_000L

/**
 * Turns "toll 4.20 parking 2 semalam" into two expenses dated yesterday.
 *
 * Deliberately dumb and predictable: a keyword binds to the nearest free amount after it, or before
 * it when there is none after; dates come from a small word list or dd/mm; anything left over is
 * "loose" and handed back so the UI can ask instead of guessing. No model, no surprises.
 */
fun parseQuickEntry(text: String, today: String, cats: List<Cat>): QuickParse {
    val lower = text.lowercase()

    var date = today
    DATE_WORDS.entries.firstOrNull { lower.contains(it.key) }?.let { date = addDays(today, it.value) }
    Regex("""\b(\d{1,2})[/-](\d{1,2})\b""").find(lower)?.let { m ->
        val day = m.groupValues[1].toIntOrNull() ?: 0
        val month = m.groupValues[2].toIntOrNull() ?: 0
        if (day in 1..31 && month in 1..12) {
            date = "%04d-%02d-%02d".format(today.substring(0, 4).toInt(), month, day)
        }
    }

    // keyword -> category, from your own categories plus the alias table
    val keyToCat = LinkedHashMap<String, String>()
    cats.forEach { c ->
        keyToCat[c.id] = c.id
        c.name.lowercase().split(Regex("\\s+")).forEach { word -> if (word.length >= 3) keyToCat[word] = c.id }
    }
    ALIASES.forEach { (cat, words) ->
        if (cats.any { it.id == cat }) words.forEach { keyToCat[it] = cat }
    }

    val tokens = Regex("""[a-z]+|\d+(?:[.,]\d{1,2})?""").findAll(lower).map { m ->
        val raw = m.value
        if (raw.first().isDigit()) Token(raw, toCents(raw)) else Token(raw, null)
    }.toList()

    val claimed = BooleanArray(tokens.size)
    val entries = ArrayList<QuickEntry>(3)
    val payouts = ArrayList<Long>(1)

    fun nearestAmount(at: Int): Long? {
        for (look in 1..3) {
            val j = at + look
            if (j < tokens.size && !claimed[j]) {
                tokens[j].num?.let { claimed[j] = true; return it }
            }
        }
        for (look in 1..3) {
            val j = at - look
            if (j >= 0 && !claimed[j]) {
                tokens[j].num?.let { claimed[j] = true; return it }
            }
        }
        return null
    }

    tokens.forEachIndexed { i, tok ->
        if (tok.text in PAYOUT_WORDS) {
            nearestAmount(i)?.let { payouts += it }
            return@forEachIndexed
        }
        val cat = keyToCat[tok.text] ?: return@forEachIndexed
        val amount = nearestAmount(i) ?: return@forEachIndexed
        entries += QuickEntry(cat, amount)
    }

    val loose = tokens.mapIndexedNotNull { i, t ->
        if (!claimed[i] && t.num != null && t.text.first().isDigit() && t.num!! <= MAX_CENTS) t.num else null
    }

    return QuickParse(date = date, entries = entries, loose = loose, payouts = payouts)
}

/** "4" is four ringgit, "4.5" is four fifty, "4.20" is four twenty. */
private fun toCents(raw: String): Long {
    val normal = raw.replace(',', '.')
    return if (normal.contains('.')) {
        (normal.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
    } else {
        (normal.toLongOrNull() ?: 0L) * 100
    }
}
