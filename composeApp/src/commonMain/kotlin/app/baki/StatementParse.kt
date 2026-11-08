package app.baki

/**
 * Parses a Grab weekly driver statement ("Penyata Pemandu" / "Driver Statement").
 *
 * The statement carries two sides: income (fare breakdown) and deductions (daily insurance, service
 * fees). Tolls are **not** a line item — Grab folds the reimbursement into the fare — so the parser
 * never invents one. English and Bahasa Malaysia labels are both accepted.
 */
object StatementParse {

    private val MONTHS = mapOf(
        "januari" to 1, "februari" to 2, "mac" to 3, "april" to 4, "mei" to 5, "jun" to 6,
        "julai" to 7, "ogos" to 8, "september" to 9, "oktober" to 10, "november" to 11, "disember" to 12,
        "january" to 1, "february" to 2, "march" to 3, "may" to 5, "june" to 6, "july" to 7,
        "august" to 8, "october" to 10, "december" to 12,
    )

    private fun num(raw: String): Double? =
        raw.replace(",", "").replace("RM", "").trim().toDoubleOrNull()

    /** "26 Oktober, 2026 - 1 November, 2026" → "2026-10-26". */
    fun weekStart(text: String): String? {
        val m = Regex(
            """(\d{1,2})\s+([A-Za-z]+)\s*,?\s*(\d{4})\s*[-–—]\s*(\d{1,2})\s+([A-Za-z]+)\s*,?\s*(\d{4})""",
        ).find(text) ?: return null
        val (day, month, year) = m.destructured
        val mo = MONTHS[month.lowercase()] ?: return null
        return "%04d-%02d-%02d".format(year.toInt(), mo, day.toInt())
    }

    /** The first money amount on a line whose label matches one of [labels]. */
    private fun amountAfter(lines: List<String>, vararg labels: String): Double? {
        for (line in lines) {
            val lower = line.lowercase()
            if (labels.any { lower.contains(it.lowercase()) }) {
                val amount = Regex("""-?\d[\d,]*\.\d{2}""").findAll(line).lastOrNull()?.value ?: continue
                return num(amount)
            }
        }
        return null
    }

    /**
     * null when the text is not a statement (no week range or no income total), so the importer can say
     * "that doesn't look like a Grab statement" instead of saving zeros.
     */
    fun parse(text: String): Statement? {
        val normalised = text.replace('\u00A0', ' ')
        val lines = normalised.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val start = weekStart(normalised) ?: return null

        val income = amountAfter(lines, "Jumlah Pendapatan", "Total Income")
        val weekly = amountAfter(lines, "Jumlah Mingguan", "Weekly Total")
        if (income == null && weekly == null) return null

        val deductions = amountAfter(lines, "Jumlah Penolakan", "Total Deductions")
        // two labels for the same daily charge: the newer "Grab Daily E-hailing Insurance-<date>" and
        // the older "ZTM GRAB DAILY INSURANCE DEDUCT"
        val insurance = Regex(
            """Grab Daily E-hailing Insurance|ZTM GRAB DAILY INSURANCE DEDUCT""",
            RegexOption.IGNORE_CASE,
        ).findAll(normalised).count()

        fun amount(vararg labels: String) = amountAfter(lines, *labels) ?: 0.0

        return Statement(
            weekStart = start,
            rangeText = Regex("""\d{1,2}\s+[A-Za-z]+\s*,?\s*\d{4}\s*[-–—]\s*\d{1,2}\s+[A-Za-z]+\s*,?\s*\d{4}""")
                .find(normalised)?.value ?: "",
            income = income ?: weekly ?: 0.0,
            deductions = deductions ?: 0.0,
            weekly = weekly ?: ((income ?: 0.0) + (deductions ?: 0.0)),
            transportNet = amount("Pendapatan bersih pengangkutan", "Transport Net Income", "Transport net"),
            pickupFares = amount("Tambang pengambilan", "Pickup Fares", "Pickup fares"),
            tripFares = amount("Tambang perjalanan", "Trip Fares", "Trip fares"),
            otherIncome = amount("Pendapatan lain", "Other Income", "Other income"),
            incentives = amount("Insentif", "Incentives"),
            tips = amount("Tip", "Tips"),
            insuranceCount = insurance,
            // the statement must balance: gross income plus (negative) deductions equals the weekly payout
            consistent = weekly != null && deductions != null &&
                kotlin.math.abs((income ?: 0.0) + deductions - weekly) < 0.02,
            importedAt = nowIso(),
        )
    }
}
