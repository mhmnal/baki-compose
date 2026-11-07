package app.baki

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual object RouteCrypto {

    private fun key(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(requireRouteSecret(secret).encodeToByteArray())
        return SecretKeySpec(digest, "AES")
    }

    actual fun seal(secret: String, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
        return RouteEnvelope.wrap(iv, cipher.doFinal(plain))
    }

    actual fun open(secret: String, sealed: ByteArray): ByteArray {
        val (iv, body) = RouteEnvelope.unwrap(sealed) ?: error("not a route envelope")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }
}
