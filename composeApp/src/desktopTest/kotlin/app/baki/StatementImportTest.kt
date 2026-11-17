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

    @Test fun genericCsvDebitCredit() {
        val text = """
            Date,Description,Debit,Credit
            01/10/2026,AEON QR Payment,12.30,
            02/10/2026,Transfer to Pot,,
        """.trimIndent()
        val rows = parseCsvStatement(text)
        assertEquals(1, rows.size)
        assertEquals(1230, rows[0].amountCents)
        assertEquals("out", rows[0].direction)
    }

    @Test fun quotedCsvDescription() {
        val text = "Date,Description,Amount\n01/10/2026,\"Coffee, milk\",-12.30"
        val rows = parseCsvStatement(text)
        assertEquals(1, rows.size)
        assertEquals("Coffee, milk", rows[0].description)
    }

    @Test fun internalSubAccountMovesAreClassifiedAndThirdPartyIsNot() {
        val aeon = parseAeonStatement("""
            AEON Bank (M) Berhad
            01 Oct 26   Emergency - Transfer to Pot
                        -RM 0.80                    RM 23.02
            01 Oct 26   RESTORAN NASI KANDAR - DuitNowQR - QR Payment
                        -RM 4.20                    RM 18.82
        """.trimIndent())
        assertTrue(aeon[0].isInternal)
        assertTrue(!aeon[1].isInternal)
        val tng = parseTngStatement("""
            TNG WALLET TRANSACTION HISTORY
            20/10/2026 Success Reload 20261020101 Quick Reload Payment (via GO+ Balance) RM57.69 RM114.38
            20/10/2026 Success DUITNOW_RECEIVEFROM 20261024111 FRIEND RM24.00 RM138.38
        """.trimIndent())
        assertTrue(tng[0].isInternal)
        assertTrue(!tng[1].isInternal, "third-party DuitNow is not an internal move")
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
