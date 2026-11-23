package app.baki

import java.time.Instant

actual fun nowMillis(): Long = System.currentTimeMillis()
actual fun nowIso(): String = Instant.now().toString()

actual fun secureRandomHex(bytes: Int): String {
    val data = ByteArray(bytes)
    java.security.SecureRandom().nextBytes(data)
    return data.joinToString("") { "%02x".format(it) }
}
