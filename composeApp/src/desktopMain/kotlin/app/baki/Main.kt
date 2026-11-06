package app.baki

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.prefs.Preferences

/** Remembers where the window was, like a desktop app should. */
private val prefs = Preferences.userRoot().node("app/baki/window")

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
    val savedW = prefs.getInt("w", 1440)
    val savedH = prefs.getInt("h", 920)
    val savedX = prefs.getInt("x", Int.MIN_VALUE)
    val savedY = prefs.getInt("y", Int.MIN_VALUE)
    val state = rememberWindowState(
        size = DpSize(savedW.dp, savedH.dp),
        position = if (savedX == Int.MIN_VALUE) WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
        else WindowPosition.Absolute(savedX.dp, savedY.dp),
    )

    Window(
        onCloseRequest = {
            prefs.putInt("w", state.size.width.value.toInt())
            prefs.putInt("h", state.size.height.value.toInt())
            prefs.putInt("x", state.position.x.value.toInt())
            prefs.putInt("y", state.position.y.value.toInt())
            exitApplication()
        },
        title = "Baki — shift logbook",
        state = state,
    ) {
        window.minimumSize = java.awt.Dimension(1080, 700)
        App()
    }
}
}
