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

    actual fun openPhonePreview() {
        runCatching {
            // jpackage knows the installed launcher; ProcessHandle often points at the bundled JVM.
            val launcher = System.getProperty("jpackage.app-path")?.takeIf { java.io.File(it).canExecute() }
            val command = ProcessHandle.current().info().command().orElse("")
            val cp = System.getProperty("java.class.path").orEmpty()
            val args = when {
                launcher != null -> listOf(launcher, "--phone-preview")
                command.substringAfterLast('/').startsWith("java") && cp.isNotBlank() ->
                    listOf(command, "-cp", cp, "app.baki.MainKt")
                command.isNotBlank() -> listOf(command, "--phone-preview")
                else -> error("No Baki launcher path available")
            }
            val log = java.io.File(System.getProperty("user.home") ?: ".", ".local/share/baki/preview-launch.log")
            log.parentFile?.mkdirs()
            log.appendText("${java.time.Instant.now()} launching: $args\n")
            ProcessBuilder(args)
                .also { it.environment()["BAKI_SHELL"] = "phone" }
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(java.io.File(log.parentFile, "preview-window.log")))
                .start()
        }.onFailure { println("[scheme] could not open phone preview: ${it.message}") }
    }

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
@androidx.compose.runtime.Composable
actual fun rememberDynamicColorScheme(dark: Boolean): androidx.compose.material3.ColorScheme? = null
