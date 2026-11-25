package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatementParseTest {

    private fun sample(): String =
        StatementParseTest::class.java.classLoader.getResourceAsStream("statement-w44.txt")
            ?.bufferedReader()?.readText() ?: error("missing statement-w44.txt")

    @Test
    fun readsTheRealGrabStatement() {
        val st = assertNotNull(StatementParse.parse(sample()), "parser rejected a real statement")
        assertEquals("2026-10-26", st.weekStart)
        assertEquals(507.34, st.income, 0.001)
        assertEquals(-37.50, st.deductions, 0.001)
        assertEquals(469.84, st.weekly, 0.001)
        assertEquals(488.70, st.transportNet, 0.001)
        assertEquals(101.20, st.pickupFares, 0.001)
        assertEquals(381.50, st.tripFares, 0.001)
        assertEquals(6.00, st.otherIncome, 0.001)
        assertEquals(16.64, st.incentives, 0.001)
        assertEquals(2.00, st.tips, 0.001)
        // five daily charges: three "Grab Daily E-hailing Insurance" + two "ZTM ... DEDUCT"
        assertEquals(5, st.insuranceCount)
        assertTrue(st.consistent, "income + deductions must equal the weekly payout")
    }

    @Test
    fun gsfMetadataRoundTripsWithoutChangingPayout() {
        val st = Statement(
            weekStart = "2026-11-16",
            income = 417.70,
            deductions = -30.00,
            weekly = 387.70,
            incentives = 16.80,
            tips = 4.00,
            netGsf = 101.71,
            netGsfRate = 19.9,
            deliveryIncentive = 1.50,
        )
        val encoded = BakiJson.encodeToString(Statement.serializer(), st)
        val decoded = BakiJson.decodeFromString(Statement.serializer(), encoded)
        assertEquals(387.70, decoded.weekly, 0.001)
        assertEquals(101.71, decoded.netGsf, 0.001)
        assertEquals(19.9, decoded.netGsfRate, 0.001)
        assertEquals(1.50, decoded.deliveryIncentive, 0.001)
        assertEquals(16.80, decoded.incentives, 0.001)
        assertEquals(4.00, decoded.tips, 0.001)
    }

    @Test
    fun rejectsTextThatIsNotAStatement() {
        assertEquals(null, StatementParse.parse("hello, this is a receipt for nasi lemak RM 5.00"))
    }
}
