package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Side effects that only make sense on a real device: the live-shift notification
 * and haptics. Desktop implements them as no-ops.
 */
/** The haptic vocabulary: each kind means one thing, everywhere. */
enum class Haptic {
    /** navigation, selection, a chip press */
    Tick,
    /** something was saved or committed */
    Confirm,
    /** something was removed or a field is invalid */
    Warn,
}

expect object PlatformEffects {
    /** Fire one of the vocabulary haptics; a device without a vibrator simply does nothing. */
    fun haptic(kind: Haptic)
    /** startMs = null means "no live shift": tear the notification down. */
    fun liveChanged(startMs: Long?, totalCents: Long)
    fun tapFeedback()
    fun log(msg: String)
    fun dataChanged()
    /** Hand a file to the platform's share sheet (no-op on desktop). */
    fun exportFile(name: String, mime: String, content: String)
    fun successFeedback()
    /** Ask the launcher to place the live widget; desktop has no home screen. */
    fun requestLiveWidget()
}

/** Notification actions arrive as intents; the UI drains this (state, so Compose reacts). */
object BakiIntents {
    var pending by mutableStateOf<String?>(null)
        internal set

    fun deliver(action: String) { pending = action }
}
