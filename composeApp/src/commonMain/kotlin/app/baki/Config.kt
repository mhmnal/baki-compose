package app.baki

/** Where the tracker server lives on each platform, and what "today" means. */
expect val defaultServerUrl: String
expect fun todayIso(): String
