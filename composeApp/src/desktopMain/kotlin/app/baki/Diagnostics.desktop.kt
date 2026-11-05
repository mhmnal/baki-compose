package app.baki

/** The desktop build has no GPS tracker. */
actual fun trackerInfo(): TrackerInfo = TrackerInfo(provider = "none (desktop)")
