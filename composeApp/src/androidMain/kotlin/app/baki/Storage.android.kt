package app.baki

import java.io.File

actual object LocalStore {
    private var dir: File? = null

    fun attach(base: File) { dir = base }

    private fun baseDir(): File =
        (dir ?: File(System.getProperty("java.io.tmpdir") ?: ".")).apply { mkdirs() }

    private fun file(): File = File(baseDir(), "store.json")

    /** Everything on disk is sealed with a Keystore key; legacy plaintext is read then re-sealed. */
    private val migratedOnce = mutableSetOf<String>()

    private fun readSealed(f: File): String? {
        if (!f.exists()) return null
        val raw = f.readBytes()
        val sealed = StoreEnvelope.open(raw) != null
        val plain = runCatching { StoreCipher.decrypt(raw) }.getOrElse { return null }
        val text = plain.decodeToString()
        // first launch after the update: re-write any legacy plaintext file sealed
        if (!sealed && migratedOnce.add(f.absolutePath)) {
            runCatching { writeSealed(f, text) }
        }
        return text
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

    actual fun readText(name: String): String? = readSealed(File(baseDir(), name))

    actual fun writeText(name: String, text: String) = writeSealed(File(baseDir(), name), text)
}
