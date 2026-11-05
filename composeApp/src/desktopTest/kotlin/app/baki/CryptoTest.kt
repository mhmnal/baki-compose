package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The envelope format and the round trip, without touching the Keystore (JVM side). */
class CryptoTest {

    @Test
    fun envelopeRoundTrips() {
        val iv = ByteArray(12) { it.toByte() }
        val body = "hello".encodeToByteArray()
        val sealed = StoreEnvelope.seal(iv, body)
        val opened = StoreEnvelope.open(sealed)
        assertNotNull(opened)
        assertEquals(iv.toList(), opened.iv.toList())
        assertEquals(body.toList(), opened.body.toList())
    }

    @Test
    fun plaintextIsNotMistakenForSealed() {
        assertNull(StoreEnvelope.open("""{"v":1}""".encodeToByteArray()))
        assertNull(StoreEnvelope.open(ByteArray(0)))
    }

    @Test
    fun aTruncatedEnvelopeIsRejected() {
        val sealed = StoreEnvelope.seal(ByteArray(12), "x".encodeToByteArray())
        assertNull(StoreEnvelope.open(sealed.copyOfRange(0, 4)))
    }

    @Test
    fun cipherRoundTripsAndHidesTheContent() {
        val secret = """{"entries":[{"amount":1250}]}"""
        val sealed = StoreCipher.encrypt(secret.encodeToByteArray())

        assertTrue(sealed.size > secret.length, "ciphertext carries an IV and a tag")
        assertFalse(String(sealed).contains("entries"), "the plaintext must not be readable")
        assertEquals(secret, String(StoreCipher.decrypt(sealed)))
    }

    @Test
    fun legacyPlaintextStillReads() {
        val legacy = "plain old file".encodeToByteArray()
        assertEquals("plain old file", String(StoreCipher.decrypt(legacy)))
    }

    @Test
    fun localStoreRoundTripsThroughEncryption() {
        val store = Store(entries = listOf(Entry(id = "x", date = "2026-11-01", type = "expense", cat = "food", amount = 1250, updated = "2026-11-01T00:00:00Z")))
        saveLocal(store)

        val raw = java.io.File(LocalStore.path()).readBytes()
        assertNotNull(StoreEnvelope.open(raw), "on disk it is a sealed envelope")
        assertFalse(String(raw).contains("food"), "and the plaintext is not in it")

        val back = loadLocal()
        assertEquals(1, back.entries.size)
        assertEquals(1250L, back.entries.first().amount)
    }
}
