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
                ?: error("No Baki launcher path available")
            val log = java.io.File(System.getProperty("user.home") ?: ".", ".local/share/baki/preview-launch.log")
            log.parentFile?.mkdirs()
            // A tiny script avoids argv/quoting differences between the running app and a terminal.
            val script = java.io.File(log.parentFile, "open-phone-preview.sh").apply {
                writeText(
                    "#!/bin/bash\n" +
                        "unset JAVA_HOME _JPACKAGE_LAUNCHER LD_LIBRARY_PATH\n" +
                        "exec env BAKI_SHELL=phone ${launcher.replace(" ", "\\ ")}\n",
                )
                setExecutable(true)
            }
            log.appendText("${java.time.Instant.now()} launching script: ${script.absolutePath}\n")
            val child = ProcessBuilder(script.absolutePath)
                .also {
                    it.environment()["BAKI_SHELL"] = "phone"
                    it.environment().remove("JAVA_HOME")
                    it.environment().remove("_JPACKAGE_LAUNCHER")
                    it.environment().remove("LD_LIBRARY_PATH")
                }
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(java.io.File(log.parentFile, "preview-window.log")))
                .start()
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
