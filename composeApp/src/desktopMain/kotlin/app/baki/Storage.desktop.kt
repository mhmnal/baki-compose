package app.baki

import java.io.File

actual object LocalStore {
    private val dir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".local/share/baki").also { it.mkdirs() }
    }

    private fun file(): File = File(dir, "store.json")

    private fun readSealed(f: File): String? {
        if (!f.exists()) return null
        val plain = runCatching { StoreCipher.decrypt(f.readBytes()) }.getOrElse { return null }
        return plain.decodeToString()
    }

    private fun writeSealed(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(StoreCipher.encrypt(text.encodeToByteArray()))
        tmp.renameTo(f)
    }

    actual fun read(): String? = readSealed(file())

    actual fun write(text: String) = writeSealed(file(), text)

    actual fun path(): String = file().absolutePath

    actual fun readText(name: String): String? = readSealed(File(dir, name))

    actual fun writeText(name: String, text: String) = writeSealed(File(dir, name), text)
}
