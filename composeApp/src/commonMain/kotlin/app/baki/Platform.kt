package app.baki

/** Small platform shims so the domain code stays common. */
expect fun nowMillis(): Long
expect fun nowIso(): String

/** Cryptographically secure random bytes, hex encoded. */
expect fun secureRandomHex(bytes: Int): String
