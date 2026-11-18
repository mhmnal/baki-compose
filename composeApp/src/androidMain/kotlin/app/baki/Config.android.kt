package app.baki

import java.time.LocalDate

/** The laptop's LAN address: the phone has never been able to reach 127.0.0.1 here. */
actual val defaultServerUrl: String
    get() = if (AppCtx.ctx?.packageName?.endsWith(".verify") == true) "" else "http://192.168.1.18:8787"
actual fun todayIso(): String = LocalDate.now().toString()
