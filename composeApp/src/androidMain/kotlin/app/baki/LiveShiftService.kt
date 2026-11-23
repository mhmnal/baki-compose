package app.baki

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val live = runCatching { loadLive() }.getOrNull() ?: return
            runCatching { startForeground(NOTIF_ID, build(live.start, liveTotalCents(live))) }
            refreshHandler.postDelayed(this, 15_000L)
        }
    }

    private fun startRefresh() {
        refreshHandler.removeCallbacks(refresh)
        refreshHandler.postDelayed(refresh, 15_000L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY restarts the service with a null intent after the process is killed: rebuild
        // the notification from the stored shift instead of showing 00:00 / RM 0.00
        val stored = if (intent == null) runCatching { loadLive() }.getOrNull() else null
        if (intent == null && stored == null) { stopSelf(); return START_NOT_STICKY }
        val start = intent?.getLongExtra(EXTRA_START, 0L) ?: stored?.start ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL, 0L) ?: stored?.let { liveTotalCents(it) } ?: 0L
        ensureChannel()
        // a location-typed FGS needs the permission at this moment, or the start is refused
        runCatching { startForeground(NOTIF_ID, build(start, total)) }
        startRefresh()
        if (DistanceTracker.hasPermission(this)) {
            DistanceTracker.start(this)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refresh)
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
            putExtra("scheme.action", action)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun build(startMs: Long, totalCents: Long): Notification {
        val open = action("open")
        val live = runCatching { loadLive() }.getOrNull()
        val paused = live?.let { isPaused(it) } ?: false
        val store = runCatching { loadLocal() }.getOrNull()
        val detail = live?.let { liveNotificationLine(store, it) } ?: "since ${startClock(startMs)} — tap to open Baki"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("RM ${rm(totalCents)} · live shift")
            .setContentText(detail)
            .setWhen(startMs)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "+ Payout", action("payout"))
            .addAction(0, if (paused) "Resume" else "Pause", action("pause"))
            .addAction(0, "End shift", action("end"))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
