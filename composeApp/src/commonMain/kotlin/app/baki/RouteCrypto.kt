package app.baki

/**
 * Seals a route before it leaves the device. The ledger and the movement history have different
 * trust levels: the desk may see the ledger over plain HTTP on the LAN, but a trace is sealed with a
 * key that only the two clients know.
 *
 * Envelope: "RT1" | ivLen (1 byte) | iv | ciphertext (GCM tag included).
 */
object RouteEnvelope {
    private val MAGIC = "RT1".encodeToByteArray()

    fun wrap(iv: ByteArray, ciphertext: ByteArray): ByteArray =
        MAGIC + byteArrayOf(iv.size.toByte()) + iv + ciphertext

    /** null when the bytes are not one of ours. */
    fun unwrap(bytes: ByteArray): Pair<ByteArray, ByteArray>? {
        if (bytes.size < MAGIC.size + 1) return null
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return null
        val ivLen = bytes[MAGIC.size].toInt()
        if (ivLen <= 0 || bytes.size < MAGIC.size + 1 + ivLen + 16) return null
        val iv = bytes.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + ivLen)
        val body = bytes.copyOfRange(MAGIC.size + 1 + ivLen, bytes.size)
        return iv to body
    }
}

/** AES-GCM with a key derived from the shared passphrase. Implemented per platform via JCE. */
expect object RouteCrypto {
    fun seal(secret: String, plain: ByteArray): ByteArray
    fun open(secret: String, sealed: ByteArray): ByteArray
}

/** The shared secret must never be empty: an unsealed trace is a privacy bug, not a fallback. */
fun requireRouteSecret(secret: String): String {
    require(secret.isNotBlank()) { "a route key is required before routes can sync" }
    return secret
}
