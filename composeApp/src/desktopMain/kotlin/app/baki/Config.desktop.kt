package app.baki

import java.time.LocalDate

actual val defaultServerUrl: String = "http://127.0.0.1:8787"
actual fun todayIso(): String =
    System.getProperty("baki.testToday") ?: LocalDate.now().toString()
