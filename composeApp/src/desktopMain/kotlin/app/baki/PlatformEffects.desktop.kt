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
            val launcher = System.getProperty("jpackage.app-path")
                ?.takeIf { java.io.File(it).canExecute() }
            val log = java.io.File(System.getProperty("user.home") ?: ".", ".local/share/baki/preview-launch.log")
            log.parentFile?.mkdirs()
            val child: Process = if (launcher != null) {
                // Spawn through a shell so the preview flag is exported in the child even though
                // the native launcher does not accept --phone-preview as an application argument.
                val quoted = launcher.replace("\"", "\\\"")
                val command = "exec env BAKI_SHELL=phone \"$quoted\""
                log.appendText("${java.time.Instant.now()} launching shell: $command\n")
                ProcessBuilder("/bin/bash", "-c", command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(java.io.File(log.parentFile, "preview-window.log")))
                    .start()
            } else {
                val command = ProcessHandle.current().info().command().orElse("")
                val cp = System.getProperty("java.class.path").orEmpty()
                if (command.substringAfterLast('/').startsWith("java") && cp.isNotBlank()) {
                    log.appendText("${java.time.Instant.now()} launching java: $command -cp <classpath> app.baki.MainKt\n")
                    ProcessBuilder(command, "-cp", cp, "app.baki.MainKt")
                        .also { it.environment()["BAKI_SHELL"] = "phone" }
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(java.io.File(log.parentFile, "preview-window.log")))
                        .start()
                } else error("No usable Baki launcher path")
            }
            log.appendText("${java.time.Instant.now()} child pid=${child.pid()}\n")
            Thread {
                val code = runCatching { child.waitFor() }.getOrElse { -1 }
                log.appendText("${java.time.Instant.now()} child pid=${child.pid()} exited code=$code\n")
            }.apply { isDaemon = true }.start()
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
