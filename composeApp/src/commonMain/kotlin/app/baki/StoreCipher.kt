package app.baki

/**
 * Envelope shared by both platforms: "BAKI1" + 12-byte IV + ciphertext (GCM tag included).
 * Kept in common code so the format itself can be unit-tested on the JVM.
 */
object StoreEnvelope {
    private val MAGIC = "BAKI1".encodeToByteArray()

    fun seal(iv: ByteArray, ciphertext: ByteArray): ByteArray =
        MAGIC + byteArrayOf(iv.size.toByte()) + iv + ciphertext

    /** null when this is a legacy plaintext file or not ours. */
    fun open(bytes: ByteArray): Sealed? {
        if (bytes.size < MAGIC.size + 1) return null
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return null
        val ivLen = bytes[MAGIC.size].toInt()
        if (ivLen <= 0 || bytes.size < MAGIC.size + 1 + ivLen) return null
        val iv = bytes.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + ivLen)
        val body = bytes.copyOfRange(MAGIC.size + 1 + ivLen, bytes.size)
        return Sealed(iv, body)
    }

    data class Sealed(val iv: ByteArray, val body: ByteArray)
}

/** Encrypts local files at rest. Android uses the Keystore; desktop uses a key file. */
expect object StoreCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}
