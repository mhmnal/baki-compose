package app.baki

import java.io.File

actual object LocalStore {
    private var dir: File? = null

    fun attach(base: File) { dir = base }

    private fun file(): File {
        val d = dir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        d.mkdirs()
        return File(d, "store.json")
    }

    actual fun read(): String? = file().takeIf { it.exists() }?.readText()

    actual fun write(text: String) {
        val f = file()
        val tmp = File(f.parentFile, "store.json.tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    actual fun path(): String = file().absolutePath

    actual fun readText(name: String): String? =
        File(file().parentFile, name).takeIf { it.exists() }?.readText()

    actual fun writeText(name: String, text: String) {
        val f = File(file().parentFile, name)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }
}
