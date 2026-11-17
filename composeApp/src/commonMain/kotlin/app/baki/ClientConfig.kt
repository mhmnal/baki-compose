package app.baki

import kotlinx.serialization.Serializable

/** Client-side settings that belong to this device only (never pushed to the server). */
@Serializable
data class ClientConfig(
    val serverUrl: String = defaultServerUrl,
    /** Shared API token; blank works only when the server is unsecured. */
    val serverToken: String = "",
    /**
     * Safety catch. While false the app never pushes a write, so a half-finished screen
     * cannot disturb the ledger the real app is using.
     */
    val allowWrites: Boolean = false,
    /** "auto" | "light" | "dark" — the web app's Auto/Light/Dark selector. */
    val themeMode: String = "auto",
    /** "wallpaper" | "baki" — System Material You or Baki's fixed Roast & Pine palette. */
    val colorSource: String = "baki",
    /** Stronger outlines and text contrast for the fixed Baki palette. */
    val highContrast: Boolean = false,
    /** Opt-in: upload sealed traces so the desk can review them. Off by default. */
    val syncRoutes: Boolean = false,
    /** Shared passphrase for the sealed traces; blank disables route sync entirely. */
    val routeSecret: String = "",
    /** Device-only: mask ledger amounts until the eye is tapped again. */
    val discreet: Boolean = false,
    /** Prefer the standard motion scheme without expressive overshoot. */
    val reducedMotion: Boolean = false,
    /** Last successful portable backup created on this device. */
    val lastBackupAt: Long? = null,
    val lastBackupEntries: Int = 0,
    val lastBackupShifts: Int = 0,
    val lastBackupBytes: Long = 0,
    /** Last time a backup file was merged back into this device. */
    val lastRestoreAt: Long? = null,
    val lastRestoreAdded: Int = 0,
    /** Desk Ledger preferences, device-local. */
    val deskQuery: String = "",
    val deskCategory: String = "all",
    val deskSort: String = "Newest",
    /**
     * Durable outbox bit: a local mutation has not reached the server yet. Default true so the first
     * launch after this field was introduced reconciles any pre-existing local-only edits.
     */
    val pendingPush: Boolean = true,
)

private const val CONFIG_FILE = "config.json"

fun loadConfig(): ClientConfig {
    val raw = try {
        LocalStore.readText(CONFIG_FILE)
    } catch (e: Exception) {
        // a damaged config must not brick the app; keep a copy and start from defaults
        runCatching { LocalStore.quarantine(CONFIG_FILE) }
        StoreHealth.loadError = "Client config could not be read; defaults restored."
        null
    } ?: return ClientConfig()
    return runCatching { BakiJson.decodeFromString<ClientConfig>(raw) }.getOrElse {
        runCatching { LocalStore.quarantine(CONFIG_FILE) }
        StoreHealth.loadError = "Client config could not be parsed; defaults restored."
        ClientConfig()
    }
}

fun saveConfig(cfg: ClientConfig) {
    runCatching {
        LocalStore.writeText(CONFIG_FILE, BakiJson.encodeToString(ClientConfig.serializer(), cfg))
    }.onFailure { StoreHealth.saveError = "Config could not be saved: ${it.message}" }
}
