package app.baki

/** Where the local copy of the store lives. Mirrors localStorage in the web app. */
expect object LocalStore {
    fun read(): String?
    fun write(text: String)
    fun path(): String

    /** Small side files (client config) that are never pushed to the server. */
    fun readText(name: String): String?
    fun writeText(name: String, text: String)
}

/** Load the local copy, or an empty store on first run. */
fun loadLocal(): Store {
    val raw = LocalStore.read() ?: return Store()
    return runCatching { BakiJson.decodeFromString<Store>(raw) }.getOrElse { Store() }
}

fun saveLocal(store: Store) {
    LocalStore.write(BakiJson.encodeToString(Store.serializer(), store))
    PlatformEffects.dataChanged()   // keep the home-screen widget in step
}
