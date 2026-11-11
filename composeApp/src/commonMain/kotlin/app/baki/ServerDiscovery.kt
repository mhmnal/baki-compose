package app.baki

/** A Baki server found on the current local network. */
data class DiscoveredServer(val url: String, val host: String, val port: Int)

/**
 * Probes the current Wi-Fi/Ethernet subnet for Baki's UDP responder. Returns null when the phone
 * isn't on a LAN, or no server answers. The server URL is never guessed from the default config.
 */
expect suspend fun discoverBakiServer(): DiscoveredServer?
