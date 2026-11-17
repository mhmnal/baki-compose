package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupSyncPayoutTest {

    @Test fun moneyInputIsExactAndBounded() {
        assertEquals("12.34", cleanMoneyInput("12,345"))
        assertEquals("123.45", cleanMoneyInput("123.456"))
        assertEquals(1230L, parseMoneyCents("12.3"))
        assertEquals(12L, parseMoneyCents("0.12"))
        assertEquals(100L, parseMoneyCents("1"))
        assertEquals(null, parseMoneyCents("."))
    }

    @Test fun payoutTruthSeparatesRecordedAndReceived() {
        val shift = Shift(id = "s", date = "2026-11-16", hours = 4.0, gross = 12000, incentive = 500, payouts = listOf(Payout(8000, 1), Payout(2000, 2)))
        val truth = payoutTruth(shift)
        assertEquals(12500L, truth.recorded)
        assertEquals(10000L, truth.received)
        assertEquals(2500L, truth.outstanding)
        assertTrue(!truth.isSettled)
        assertEquals(2, truth.count)
    }

    @Test fun mergeReportSeesOnlyOverlappingEdits() {
        val a = Store(entries = listOf(Entry("same", "2026-11-16", amount = 100, updated = "A")))
        val b = Store(entries = listOf(Entry("same", "2026-11-16", amount = 200, updated = "B")))
        assertEquals(1, mergeReport(a, b).entries)
        assertEquals(1, mergeReport(a, b).total)
    }

    @Test fun backupSummaryRecognizesBundleAndBareStore() {
        val store = Store(entries = listOf(Entry("e", "2026-11-16", amount = 100)))
        val bundle = BakiBackupBundle(store = store, exportedAt = "2026-11-16T00:00:00Z")
        val encoded = kotlinx.serialization.json.Json { encodeDefaults = true }
            .encodeToString(BakiBackupBundle.serializer(), bundle)
        val summary = assertNotNull(summarizeBackup(encoded))
        assertEquals(1, summary.entries)
        assertTrue(summary.hasDeviceConfig.not()) // no config was supplied
    }
}

class BackupEncryptionTest {
    @Test fun encryptedBackupRoundTripsAndRejectsWrongPassphrase() {
        val json = """{"v":1,"entries":[]}"""
        val sealed = assertNotNull(encryptBackup(json, "correct horse battery staple"))
        assertTrue(isEncryptedBackup(sealed))
        assertEquals(json, decryptBackup(sealed, "correct horse battery staple"))
        assertEquals(null, decryptBackup(sealed, "wrong passphrase"))
    }

    @Test fun plainJsonIsNotMistakenForEncrypted() {
        assertTrue(!isEncryptedBackup("""{"entries":[]}"""))
        assertEquals(null, decryptBackup("""{"entries":[]}""", "anything"))
    }
}
