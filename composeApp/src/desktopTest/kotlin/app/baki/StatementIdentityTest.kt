package app.baki

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementIdentityTest {
    private val name = "ALEX TAN WEI MING"
    private val suffixes = listOf("773906", "447336", "783130")

    @Test fun ownNameVariantsAreInternal() {
        assertTrue(isInternalTransfer("ENCIK ALEX TAN WEI MING - DuitNow - Fund Transfer", "aeon", name, suffixes))
        assertTrue(isInternalTransfer("ALEX TAN WEI MING - DuitNowQR", "aeon", name, suffixes))
    }

    @Test fun ownAccountSuffixIsInternalOnlyInTransferContext() {
        assertTrue(isInternalTransfer("DuitNow transfer ref 773906", "tng", name, suffixes))
        assertFalse(isInternalTransfer("PAYDIRECT PAYMENT 773906", "tng", name, suffixes))
    }

    @Test fun thirdPartyDuitNowRemainsSpending() {
        assertFalse(isInternalTransfer("AHMAD BIN ALI - DuitNow - Fund Transfer", "aeon", name, suffixes))
        assertFalse(isInternalTransfer("DUITNOW_TRANSFERTO 2026 FRIEND", "tng", name, suffixes))
    }

    @Test fun cashbackIsNotInternal() {
        assertFalse(isInternalTransfer("AEON Bank - Rewards - Cashback", "aeon", name, suffixes))
    }
}
