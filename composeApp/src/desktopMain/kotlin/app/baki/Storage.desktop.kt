package app.baki

import java.io.File

actual object LocalStore {
    private val dir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".local/share/baki").also { it.mkdirs() }
    }
    private val file by lazy { File(dir, "store.json") }

    actual fun read(): String? = if (file.exists()) file.readText() else null

    actual fun write(text: String) {
        dir.mkdirs()
        val tmp = File(dir, "store.json.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)          // atomic-ish: never leave half a store behind
    }

    actual fun path(): String = file.absolutePath

    actual fun readText(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    actual fun writeText(name: String, text: String) {
        dir.mkdirs()
        File(dir, name).writeText(text)
    }
}
