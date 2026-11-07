package app.baki

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteCryptoTest {

    private val secret = "kopi-o-kosong"

    @Test
    fun sealedTracesOpenWithTheSameKey() {
        val plain = BakiJson.encodeToString(
            ShiftRoute.serializer(),
            ShiftRoute(
                shiftId = "s1", date = "2026-11-06",
                points = listOf(
                    RoutePoint(3.1390, 101.6869, 1_760_000_000_000),
                    RoutePoint(3.1400, 101.6880, 1_760_000_060_000),
                ),
            ),
        ).encodeToByteArray()

        val sealed = RouteCrypto.seal(secret, plain)
        assertFalse(sealed.contentEquals(plain), "the trace must not travel in the clear")
        assertContentEquals(plain, RouteCrypto.open(secret, sealed))
    }

    @Test
    fun theWrongKeyCannotOpenATrace() {
        val sealed = RouteCrypto.seal(secret, "{}".encodeToByteArray())
        assertFailsWith<Exception> { RouteCrypto.open("wrong-key", sealed) }
    }

    @Test
    fun aTamperedEnvelopeIsRejected() {
        val sealed = RouteCrypto.seal(secret, "route".encodeToByteArray())
        val broken = sealed.copyOf()
        broken[broken.lastIndex] = (broken.last().toInt() xor 0xFF).toByte()
        assertFailsWith<Exception> { RouteCrypto.open(secret, broken) }
    }

    @Test
    fun envelopesAreRecognisableAndJunkIsNot() {
        val sealed = RouteCrypto.seal(secret, "x".encodeToByteArray())
        assertTrue(RouteEnvelope.unwrap(sealed) != null)
        assertNull(RouteEnvelope.unwrap("just some bytes".encodeToByteArray()))
    }

    @Test
    fun wireFormatRoundTripsThroughBase64() {
        val sealed = RouteCrypto.seal(secret, "trace".encodeToByteArray())
        val text = RouteBase64.encode(sealed)
        assertContentEquals(sealed, RouteBase64.decode(text))
        assertEquals(text, RouteBase64.encode(RouteBase64.decode(text)))
    }
}
