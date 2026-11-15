package app.baki

import java.time.Instant

actual fun nowMillis(): Long =
    System.getProperty("baki.testNowMillis")?.toLongOrNull() ?: System.currentTimeMillis()
actual fun nowIso(): String = Instant.now().toString()
