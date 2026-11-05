package app.baki

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget: today's kept amount, read straight from the same store.json the app uses.
 * Tapping opens the app.
 */
class BakiWidget : AppWidgetProvider() {

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BakiWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> mgr.updateAppWidget(id, build(ctx)) }
        }

        private fun build(ctx: Context): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.baki_widget)
            val store = loadLocal()
            val today = todayIso()
            val totals = dayTotals(store, today)

            views.setTextViewText(R.id.widget_amount, "RM ${rm(totals.net)}")
            views.setTextViewText(R.id.widget_sub, "${totals.entryCount} entries · ${totals.shiftCount} shifts")

            val open = Intent(ctx, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_amount, pending)
            views.setOnClickPendingIntent(R.id.widget_label, pending)
            return views
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> mgr.updateAppWidget(id, build(ctx)) }
    }
}
