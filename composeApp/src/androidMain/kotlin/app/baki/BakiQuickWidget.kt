package app.baki

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * A quick-log widget that only launches the validated capture flow. It never writes the ledger
 * from a widget process; every button goes through MainActivity and BakiRepo.
 */
class BakiQuickWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> runCatching { mgr.updateAppWidget(id, build(ctx)) } }
    }

    private fun build(ctx: Context): RemoteViews {
        val views = RemoteViews(ctx.packageName, R.layout.baki_quick_widget)
        views.setOnClickPendingIntent(R.id.quick_food, capture(ctx, 11, null, "widget_food"))
        views.setOnClickPendingIntent(R.id.quick_toll, capture(ctx, 12, "toll", "widget_toll"))
        views.setOnClickPendingIntent(R.id.quick_fuel, capture(ctx, 13, "fuel", "widget_fuel"))
        views.setOnClickPendingIntent(R.id.quick_more, capture(ctx, 14, null, "widget_more"))
        return views
    }

    private fun capture(ctx: Context, request: Int, category: String?, source: String): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = "app.baki.QUICK_ADD"
            putExtra("baki.type", "expense")
            category?.let { putExtra("baki.category", it) }
            putExtra("baki.source", source)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
