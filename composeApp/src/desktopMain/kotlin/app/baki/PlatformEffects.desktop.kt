package app.baki

/** Desktop has no shade and no vibration: the live shift lives in the window only. */
actual object PlatformEffects {
    /** Write the statement, then hand it to the default browser: Ctrl/⌘+P gives a clean PDF. */
    actual fun printStatement(html: String) {
        runCatching {
            val dir = java.io.File(System.getProperty("java.io.tmpdir"), "scheme").apply { mkdirs() }
            val file = java.io.File(dir, "scheme-statement-${todayStamp()}.html")
            file.writeText(html)
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(file.toURI())
            }
        }
    }

    actual fun requestLiveWidget() {}

    actual fun notice(text: String) {
        println("[scheme] $text")
    }

    actual fun setStatementReminder(enabled: Boolean) {
        // the desk has no notification channel; the phone owns the weekly nudge
    }
    actual fun notificationsBlocked(): Boolean = false
    actual fun openNotificationSettings() {}
    actual fun haptic(kind: Haptic) {}
    actual fun liveChanged(startMs: Long?, totalCents: Long) = Unit
    actual fun log(msg: String) = println(msg)
    actual fun dataChanged() = Unit

    actual fun exportFile(name: String, mime: String, content: String) {
        val f = java.io.File(System.getProperty("user.home"), "scheme-$name")
        f.writeText(content)
        println("exported to ${f.absolutePath}")
    }
    actual fun tapFeedback() = Unit
    actual fun successFeedback() = Unit
}

/** No Monet on the desktop. */
actual fun dynamicColorScheme(dark: Boolean): androidx.compose.material3.ColorScheme? = null
