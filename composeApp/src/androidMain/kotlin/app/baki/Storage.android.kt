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
        // never rotate a damaged/empty store over the last good backup
        if (looksSealed(f)) {
            runCatching { f.copyTo(File(f.parentFile, f.name + ".bak"), overwrite = true) }
        }
        if (!tmp.renameTo(f)) {
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

    actual fun readText(name: String): String? = readSealed(File(baseDir(), File(name).name))

    actual fun writeText(name: String, text: String) = writeSealed(File(baseDir(), File(name).name), text)

    actual fun exists(name: String): Boolean = File(baseDir(), File(name).name).exists()

    actual fun readBytes(name: String): ByteArray? {
        val f = File(baseDir(), File(name).name)
        return if (f.exists()) f.readBytes() else null
    }

    actual fun writeBytes(name: String, bytes: ByteArray) {
        val f = File(baseDir(), File(name).name)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!tmp.renameTo(f)) {
            f.writeBytes(bytes)
            tmp.delete()
        }
    }

    actual fun appendBytes(name: String, bytes: ByteArray) {
        val f = File(baseDir(), File(name).name)
        f.parentFile?.mkdirs()
        FileOutputStream(f, true).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
    }

    actual fun deleteBytes(name: String) {
        File(baseDir(), File(name).name).delete()
    }

    actual fun quarantine(name: String): String? {
        val f = File(baseDir(), File(name).name)
        if (!f.exists()) return null
        val dest = File(baseDir(), f.name + ".corrupt-" + System.currentTimeMillis())
        return if (f.renameTo(dest)) dest.absolutePath else null
    }
}
