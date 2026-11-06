package app.baki

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The shift in progress, on the home screen.
 *
 * A RemoteViews widget cannot animate, but a Chronometer keeps its own clock: the elapsed time ticks
 * inside the launcher without the app running and without periodic updates. Everything else is
 * pushed whenever the shift changes.
 */
class BakiLiveWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        AppCtx.attach(ctx)
        ids.forEach { id -> mgr.updateAppWidget(id, build(ctx)) }
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BakiLiveWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> mgr.updateAppWidget(id, build(ctx)) }
        }

        private fun build(ctx: Context): RemoteViews {
            AppCtx.attach(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.baki_live_widget)
            val dark = !isNight(ctx)
            views.setInt(R.id.live_root, "setBackgroundResource",
                if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            val ink = ctx.getColor(if (dark) R.color.widget_ink_dark else R.color.widget_ink_light)
            val muted = ctx.getColor(if (dark) R.color.widget_muted_dark else R.color.widget_muted_light)

            views.setTextColor(R.id.live_label, muted)
            views.setTextColor(R.id.live_clock, ink)
            views.setTextColor(R.id.live_money, muted)

            val live = loadLive()
            if (live == null) {
                // idle: a headline instead of a stopped clock, nothing else to tap
                views.setTextViewText(R.id.live_label, "BAKI")
                views.setViewVisibility(R.id.live_clock, android.view.View.GONE)
                views.setViewVisibility(R.id.live_money, android.view.View.GONE)
                views.setViewVisibility(R.id.live_toggle, android.view.View.GONE)
                views.setViewVisibility(R.id.live_idle, android.view.View.VISIBLE)
                views.setTextColor(R.id.live_idle, ink)
                views.setOnClickPendingIntent(R.id.live_root, openApp(ctx))
                return views
            }

            views.setViewVisibility(R.id.live_idle, android.view.View.GONE)
            views.setViewVisibility(R.id.live_money, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.live_clock, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.live_toggle, android.view.View.VISIBLE)
            // Chronometer counts from elapsedRealtime (uptime), not from epoch millis
            val base = android.os.SystemClock.elapsedRealtime() - (nowMillis() - live.start)
            views.setChronometer(R.id.live_clock, base, null, !isPaused(live))
            if (isPaused(live)) {
                views.setTextViewText(R.id.live_label, "ON BREAK")
            } else {
                views.setTextViewText(R.id.live_label, "LIVE SHIFT")
            }

            val total = liveTotalCents(live)
            val elapsed = liveWorkedMs(live) / 3_600_000.0
            val rate = if (elapsed > 0.05) total / 100.0 / elapsed else 0.0
            views.setTextViewText(
                R.id.live_money,
                buildString {
                    append("RM ${rm(total)}")
                    if (rate > 0) append(" · RM ${"%.2f".format(rate)}/hr")
                    append(if (isPaused(live)) " · paused" else " · since ${startClock(live.start)}")
                },
            )

            views.setImageViewResource(
                R.id.live_toggle,
                if (isPaused(live)) R.drawable.ic_widget_play else R.drawable.ic_widget_pause,
            )
            views.setOnClickPendingIntent(R.id.live_root, openApp(ctx))
            views.setOnClickPendingIntent(R.id.live_toggle, togglePause(ctx))
            return views
        }

        private fun isNight(ctx: Context): Boolean {
            val mode = ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        private fun openApp(ctx: Context): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun togglePause(ctx: Context): PendingIntent {
            val i = Intent(ctx, WidgetActions::class.java).setAction(WidgetActions.ACTION_TOGGLE_PAUSE)
            return PendingIntent.getBroadcast(
                ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
