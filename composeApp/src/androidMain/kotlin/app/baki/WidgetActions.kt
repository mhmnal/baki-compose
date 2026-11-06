package app.baki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Widget buttons act without opening the app. The receiver runs in the app process (or starts it),
 * attaches the same encrypted store the UI uses, flips the pause, and pushes a fresh widget.
 */
class WidgetActions : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE_PAUSE = "app.baki.widget.TOGGLE_PAUSE"
    }

    override fun onReceive(ctx: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> togglePause(ctx)
        }
    }

    private fun togglePause(ctx: Context) {
        AppCtx.ctx = ctx.applicationContext
        LocalStore.attach(ctx.filesDir)

        val live = loadLive() ?: return
        val now = nowMillis()
        val next = if (isPaused(live)) {
            live.copy(
                pauses = live.pauses.mapIndexed { i, p ->
                    if (i == live.pauses.lastIndex && p.to == null) p.copy(to = now) else p
                },
            )
        } else {
            live.copy(pauses = live.pauses + LivePause(from = now))
        }
        saveLive(next)
        PlatformEffects.haptic(Haptic.Tick)
        // the notification and both widgets must agree with the new state
        PlatformEffects.liveChanged(next.start, liveTotalCents(next))
        BakiLiveWidget.refresh(ctx)
        BakiWidget.refresh(ctx)
    }
}
