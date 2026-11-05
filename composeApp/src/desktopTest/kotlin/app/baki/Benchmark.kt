package app.baki

import kotlin.test.Test
import kotlin.time.measureTimedValue

/**
 * Not an assertion suite: a yardstick. Builds a year of realistic data and times the hot paths
 * so an optimisation can be proved rather than assumed. Run with:
 *   ./gradlew :composeApp:desktopTest --tests "app.baki.Benchmark"
 */
class Benchmark {

    private fun synthetic(entries: Int = 2400, shifts: Int = 520): Store {
        val cats = listOf("food", "fuel", "toll", "parking", "carwash", "maintenance", "other")
        val e = (0 until entries).map { i ->
            val day = 1 + (i % 365)
            val month = 1 + (day / 31)
            Entry(
                id = "e$i",
                date = "2026-%02d-%02d".format(month, 1 + (day % 28)),
                type = if (i % 9 == 0) "income" else "expense",
                cat = cats[i % cats.size],
                amount = (300 + i % 5000).toLong(),
                updated = "2026-01-01T00:00:%02dZ".format(i % 60),
            )
        }
        val s = (0 until shifts).map { i ->
            val day = 1 + (i % 365)
            val month = 1 + (day / 31)
            Shift(
                id = "s$i",
                date = "2026-%02d-%02d".format(month, 1 + (day % 28)),
                start = "%02d:00".format(i % 24),
                hours = 3.0 + (i % 5),
                km = 60.0 + (i % 90),
                gross = (9000 + i % 7000).toLong(),
                extra = if (i % 7 == 0) 250 else 0,
                grabFee = if (i % 2 == 0) 750 else 0,
                updated = "2026-01-01T00:00:%02dZ".format(i % 60),
            )
        }
        return Store(
            updatedAt = "2026-12-31T00:00:00Z",
            entries = e, shifts = s,
            settings = Settings(
                profit = Profit(loanMonthly = 1200.0, roadTaxYearly = 300.0, carInsuranceYearly = 1500.0),
                budgets = mapOf("food" to 400.0, "fuel" to 600.0, "toll" to 150.0),
            ),
        )
    }

    private inline fun time(label: String, iterations: Int = 20, block: () -> Unit) {
        // warm up
        repeat(3) { block() }
        val (_, total) = measureTimedValue { repeat(iterations) { block() } }
        val perRun = total.inWholeMicroseconds / iterations.toDouble() / 1000.0
        println(String.format("%-34s %8.2f ms/run  (%d runs)", label, perRun, iterations))
    }

    @Test
    fun measure() {
        val store = synthetic()
        println("\n=== benchmark: ${store.entries.size} entries, ${store.shifts.size} shifts ===")

        time("hourHeatmap") { hourHeatmap(store) }
        time("monthCostSeries(4)") { monthCostSeries(store, "2026-12", 4) }
        time("monthCost") { monthCost(store, "2026-12") }
        time("fundStats(all)") { fundStats(store) }
        time("expensesByCategory(month)") { expensesByCategory(store, "2026-12") }
        time("monthTotals") { monthTotals(store, "2026-12") }
        time("all shifts metrics") {
            store.shifts.forEach { shiftMetrics(it, store.settings.profit, 1.0) }
        }
        time("mergeStores") { mergeStores(store, store.copy(updatedAt = "2027-01-01T00:00:00Z")) }
        time("json encode") { BakiJson.encodeToString(Store.serializer(), store) }
        val text = BakiJson.encodeToString(Store.serializer(), store)
        time("json decode") { BakiJson.decodeFromString(Store.serializer(), text) }
        time("budgetUsage") { budgetUsage(store, "2026-12") }
        time("measuredEconomy") { measuredEconomy(store) }
        println("=== end ===\n")
    }
}
