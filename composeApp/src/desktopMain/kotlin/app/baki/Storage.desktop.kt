package app.baki

import java.io.File
import java.io.FileOutputStream

actual object LocalStore {
    private val dir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".local/share/scheme").also { it.mkdirs() }
    }

    private fun file(): File = File(dir, "store.json")

    /** Throws when the file exists but cannot be decrypted: the caller must not treat that as empty. */
    private fun readSealed(f: File): String? {
        if (!f.exists()) return null
        return StoreCipher.decrypt(f.readBytes()).decodeToString()
    }

    private fun writeSealed(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(StoreCipher.encrypt(text.encodeToByteArray()))
            out.fd.sync()
        }
        // never rotate a damaged/empty store over the last good backup
        if (looksSealed(f)) {
            runCatching { f.copyTo(File(f.parentFile, f.name + ".bak"), overwrite = true) }
        }
        if (!tmp.renameTo(f)) {
            // cross-device or a locked file: fall back to a direct write rather than losing the save
            f.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    private fun looksSealed(f: File): Boolean {
        if (!f.exists() || f.length() < 8) return false
        return runCatching {
            f.inputStream().use { input ->
                val magic = ByteArray(5)
                input.read(magic) == 5 && String(magic, Charsets.US_ASCII) == "BAKI1"
            }
        }.getOrDefault(false)
    }

    actual fun read(): String? = readSealed(file())

    actual fun write(text: String) = writeSealed(file(), text)

    actual fun path(): String = file().absolutePath

    actual fun readText(name: String): String? = readSealed(File(dir, File(name).name))

    actual fun writeText(name: String, text: String) = writeSealed(File(dir, File(name).name), text)

    actual fun quarantine(name: String): String? {
        val f = File(dir, File(name).name)
        if (!f.exists()) return null
        val dest = File(dir, f.name + ".corrupt-" + System.currentTimeMillis())
        return if (f.renameTo(dest)) dest.absolutePath else null
    }
}
