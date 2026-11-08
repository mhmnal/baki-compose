package app.baki

import java.io.File
import java.io.FileOutputStream

actual object LocalStore {
    private val dir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".local/share/baki").also { it.mkdirs() }
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
        if (f.exists()) runCatching { f.copyTo(File(f.parentFile, f.name + ".bak"), overwrite = true) }
        if (!tmp.renameTo(f)) {
            // cross-device or a locked file: fall back to a direct write rather than losing the save
            f.writeBytes(tmp.readBytes())
            tmp.delete()
        }
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
