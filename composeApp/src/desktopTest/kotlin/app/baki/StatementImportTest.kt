package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatementImportTest {
    @Test fun aeonWithdrawalAndPotTransfer() {
        val text = """
            AEON Bank (M) Berhad
            01 Oct 26   RESTORAN NASI KANDAR HAJI SHARFUDDIN - DuitNowQR - QR Payment
                        -RM 4.20                    RM 23.82
            01 Oct 26   Emergency - Transfer to Pot
                        -RM 0.80                    RM 23.02
            01 Oct 26   Opening balance                         RM 28.02
        """.trimIndent()
        val rows = parseAeonStatement(text)
        assertEquals(2, rows.size)
        assertEquals(420, rows[0].amountCents)
        assertEquals("out", rows[0].direction)
        assertTrue(rows[1].isTransfer)
        assertEquals("AEON Bank — Savings Account-i", rows[0].accountName)
    }

    @Test fun tngAmountAndDirection() {
        val text = """
            TNG WALLET TRANSACTION HISTORY
            20/10/2026 Success PayDirect Payment 20261020101 PENCHALA LINK 20/10/2026 12:16 AM RM3.00 RM56.69
            20/10/2026 Success Reload 20261020101 Quick Reload Payment (via GO+ Balance) RM57.69 RM114.38
            20/10/2026 Success DUITNOW_RECEIVEFROM 20261024111 ABDUL RAHMAN RM242.44 RM24.00
        """.trimIndent()
        val rows = parseTngStatement(text)
        assertEquals(3, rows.size)
        assertEquals(300, rows[0].amountCents)
        assertEquals("out", rows[0].direction)
        assertTrue(rows[1].isTransfer)
        assertEquals("in", rows[1].direction)
        assertTrue(rows[2].isTransfer)
        assertEquals("in", rows[2].direction)
    }
}
