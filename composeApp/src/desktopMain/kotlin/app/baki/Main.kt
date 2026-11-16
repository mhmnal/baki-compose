package app.baki

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.prefs.Preferences

/** Remembers where the window was, like a desktop app should. */
private val prefs = Preferences.userRoot().node("app/scheme/window")

private var instanceLock: java.nio.channels.FileLock? = null
private var instanceChannel: java.nio.channels.FileChannel? = null

/** Two instances overlap and produce hard window edges over each other; only one may run. */
private fun acquireSingleInstance(): Boolean {
    val dir = java.io.File(System.getProperty("user.home"), ".local/share/baki")
    dir.mkdirs()
    val channel = java.nio.channels.FileChannel.open(
        java.io.File(dir, "instance.lock").toPath(),
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.WRITE,
    )
    val lock = try { channel.tryLock() } catch (e: Exception) { null }
    if (lock == null) { channel.close(); return false }
    instanceChannel = channel
    instanceLock = lock
    return true
}

fun main() {
    if (!acquireSingleInstance()) {
        println("Baki is already running")
        return
    }
    application {
    // A previous 1440×920 default is larger than a 1440×900 desktop and clips the right edge.
    // Clamp remembered size and position to the usable screen before creating the window.
    val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
    val savedW = prefs.getInt("w", 1280).coerceIn(1080, (screen.width - 80).coerceAtLeast(1080))
    val savedH = prefs.getInt("h", 820).coerceIn(700, (screen.height - 100).coerceAtLeast(700))
    // XWayland can report a larger virtual screen than the visible desktop. Centering is the
    // only reliable way to keep the frame on-screen across Wayland, X11 and HiDPI scaling.

    val state = rememberWindowState(
        size = DpSize(savedW.dp, savedH.dp),
        position = WindowPosition.Absolute(80.dp, 40.dp),
    )

    Window(
        onCloseRequest = {
            prefs.putInt("w", state.size.width.value.toInt())
            prefs.putInt("h", state.size.height.value.toInt())
            exitApplication()
        },
        title = "Baki — shift logbook",
        state = state,
    ) {
        window.minimumSize = java.awt.Dimension(1080, 700)
        PhonePreview.desktopView = System.getenv("BAKI_VIEW")?.takeIf { it.isNotBlank() }
        App()
    }
}
}
