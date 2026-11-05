package app.baki

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * cover-cases.json was produced by the shipping web app: for each awkward case it lists the ids
 * it would charge. Kotlin must agree exactly, or the two apps will bill different days.
 */
class CoverCrossCheckTest {

    private fun resource(name: String) =
        CoverCrossCheckTest::class.java.classLoader.getResourceAsStream(name)?.bufferedReader()?.readText()
            ?: error("missing $name")

    private val cases by lazy {
        BakiJson.parseToJsonElement(resource("cover-cases.json")) as kotlinx.serialization.json.JsonArray
    }

    @Test
    fun kotlinAgreesWithTheWebAppOnEveryCase() {
        val tz = TimeZone.currentSystemDefault()
        var checked = 0
        for (el in cases) {
            val obj = el as kotlinx.serialization.json.JsonObject
            fun str(k: String) = (obj[k] as kotlinx.serialization.json.JsonPrimitive).content
            val name = str("name")
            val ids = (obj["ids"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            val want = (obj["charged"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }.toSet()

            // rebuild the same shifts the fixture describes
            val shifts = ids.indices.map { i ->
                val parts = nameToShift(name, i)
                Shift(id = ids[i], date = parts.first, start = parts.second, hours = 3.0)
            }
            val got = coverChargedIds(shifts, tz)
            assertEquals(want, got, "case '$name'")
            checked++
        }
        println("[cover] matched the web app on $checked cases")
    }

    /** The fixture carries ids in order; dates come from the case definition below. */
    private fun nameToShift(name: String, index: Int): Pair<String, String> {
        val table = mapOf(
            "same day twice" to listOf("2026-11-01" to "09:00", "2026-11-01" to "15:00"),
            "exactly 24h" to listOf("2026-11-02" to "08:00", "2026-11-03" to "08:00"),
            "23h59m" to listOf("2026-11-04" to "08:00", "2026-11-05" to "07:59"),
            "24h01m" to listOf("2026-11-06" to "08:00", "2026-11-07" to "08:01"),
            "out of order" to listOf("2026-11-09" to "18:00", "2026-11-08" to "10:00"),
            "overnight 22:00->00:30" to listOf("2026-11-10" to "22:00", "2026-11-11" to "00:30"),
            "three across two days" to listOf("2026-11-12" to "06:00", "2026-11-12" to "20:00", "2026-11-13" to "07:00"),
            "gap then charge" to listOf("2026-11-14" to "09:00", "2026-11-16" to "10:00"),
        )
        return table.getValue(name)[index]
    }
}
