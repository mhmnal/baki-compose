package app.baki

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop keeps a random key file beside the data (0600). Weaker than the Android Keystore — the
 * key is on the same disk — but it still means the JSON is not readable at a glance.
 */
actual object StoreCipher {

    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private val keyFile: File by lazy {
        val dir = File(System.getProperty("user.home") ?: ".", ".local/share/scheme").apply { mkdirs() }
        File(dir, ".key").also { f ->
            if (!f.exists()) {
                val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                f.writeBytes(bytes)
                runCatching { f.setReadable(false, false); f.setReadable(true, true) }
            }
        }
    }

    private fun key(): SecretKey = SecretKeySpec(keyFile.readBytes(), "AES")

    actual fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return StoreEnvelope.seal(cipher.iv, cipher.doFinal(plain))
    }

    actual fun decrypt(sealed: ByteArray): ByteArray {
        val env = StoreEnvelope.open(sealed) ?: return sealed
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, env.iv))
        return cipher.doFinal(env.body)
    }
}
