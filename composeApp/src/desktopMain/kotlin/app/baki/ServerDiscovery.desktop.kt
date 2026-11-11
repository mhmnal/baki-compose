package app.baki

/** The phone finds its own server; the desk is normally the host. */
actual suspend fun discoverBakiServer(): DiscoveredServer? = null
