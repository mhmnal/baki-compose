package app.baki

private const val SQLITE_KEY_FILE = "sqlite.key"

/** The database passphrase is itself sealed by the platform StoreCipher. */
fun sqlitePassphrase(): String {
    val existing = runCatching { LocalStore.readText(SQLITE_KEY_FILE) }.getOrNull()?.trim()
    if (existing != null && existing.length >= 32) return existing
    val fresh = secureRandomHex(32)
    LocalStore.writeText(SQLITE_KEY_FILE, fresh)
    return fresh
}
