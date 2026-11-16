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
    /** Save and open a print-ready document; phones have no printer workflow. */
    fun printStatement(html: String)
    /** Ask the launcher to place the live widget; desktop has no home screen. */
    fun requestLiveWidget()
    /** Desktop-only: launch the isolated interactive phone preview process. */
    fun openPhonePreview()
    /** Short, non-blocking feedback (a Toast on Android, a log line on desktop). */
    fun notice(text: String)
    /** Schedule or cancel the weekly Wednesday statement reminder. */
    fun setStatementReminder(enabled: Boolean)
    /** True when the OS will not deliver notifications (Android 13+ permission denied). */
    fun notificationsBlocked(): Boolean
    /** Open this app's notification settings, where the user can allow the reminder. */
    fun openNotificationSettings()
}

/** Notification actions arrive as intents; the UI drains this (state, so Compose reacts). */
object BakiIntents {
    var pending by mutableStateOf<String?>(null)
        internal set

    fun deliver(action: String) { pending = action }
}
