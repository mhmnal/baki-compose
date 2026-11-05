package app.baki

/** Small platform shims so the domain code stays common. */
expect fun nowMillis(): Long
expect fun nowIso(): String
