package app.baki

import java.io.File
import java.io.FileOutputStream

actual object LocalStore {
    private var dir: File? = null

    fun attach(base: File) { dir = base }

    private fun baseDir(): File =
        (dir ?: File(System.getProperty("java.io.tmpdir") ?: ".")).apply { mkdirs() }

    private fun file(): File = File(baseDir(), "store.json")

    /** Everything on disk is sealed with a Keystore key; legacy plaintext is read then re-sealed. */
    private val migratedOnce = mutableSetOf<String>()

    /** Throws when the file exists but cannot be decrypted: the caller must not treat that as empty. */
    private fun readSealed(f: File): String? {
        if (!f.exists()) return null
        val raw = f.readBytes()
        val sealed = StoreEnvelope.open(raw) != null
        val text = StoreCipher.decrypt(raw).decodeToString()
        // first launch after the update: re-write any legacy plaintext file sealed
        if (!sealed && migratedOnce.add(f.absolutePath)) {
            runCatching { writeSealed(f, text) }
        }
        return text
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
            f.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    actual fun read(): String? = readSealed(file())

    actual fun write(text: String) = writeSealed(file(), text)

    actual fun path(): String = file().absolutePath

    actual fun readText(name: String): String? = readSealed(File(baseDir(), File(name).name))

    actual fun writeText(name: String, text: String) = writeSealed(File(baseDir(), File(name).name), text)

    actual fun quarantine(name: String): String? {
        val f = File(baseDir(), File(name).name)
        if (!f.exists()) return null
        val dest = File(baseDir(), f.name + ".corrupt-" + System.currentTimeMillis())
        return if (f.renameTo(dest)) dest.absolutePath else null
    }
}
