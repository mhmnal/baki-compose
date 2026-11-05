package app.baki

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service for an in-progress shift: a live chronometer in the shade with
 * Payout and End actions that deep-link back into the app.
 */
class LiveShiftService : Service() {

    companion object {
        private const val CHANNEL = "live_shift"
        private const val NOTIF_ID = 4211
        const val EXTRA_START = "start"
        const val EXTRA_TOTAL = "total"

        fun update(ctx: Context, startMs: Long, totalCents: Long) {
            val i = Intent(ctx, LiveShiftService::class.java)
                .putExtra(EXTRA_START, startMs)
                .putExtra(EXTRA_TOTAL, totalCents)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                // Android 12+ blocks starting a FGS from the background; the in-app card still works
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LiveShiftService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val start = intent?.getLongExtra(EXTRA_START, 0L) ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL, 0L) ?: 0L
        ensureChannel()
        // a location-typed FGS needs the permission at this moment, or the start is refused
        runCatching { startForeground(NOTIF_ID, build(start, total)) }
        if (DistanceTracker.hasPermission(this)) {
            DistanceTracker.start(this) { /* distance is persisted by the tracker */ }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        DistanceTracker.stop()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Live shift", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The clock and earnings for a shift in progress"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun action(action: String): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            this.action = action
            putExtra("baki.action", action)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun build(startMs: Long, totalCents: Long): Notification {
        val open = action("open")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("RM ${rm(totalCents)} · live shift")
            .setContentText("since ${startClock(startMs)} — tap to open Baki")
            .setWhen(startMs)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "+ Payout", action("payout"))
            .addAction(0, "End shift", action("end"))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
