package app.baki

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Wednesday nudge: the statement lands midweek, and importing it keeps the week's reconciliation honest. */
class StatementReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Weekly statement", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Wednesday reminder to import the Grab statement"
                },
            )
        }
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, 41, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("Grab statement day")
            .setContentText("Import last week's PDF so the books stay current.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(41, notification) }
        return Result.success()
    }

    companion object {
        const val CHANNEL = "baki-statements"
        const val WORK = "baki-statement-reminder"
    }
}

object StatementReminder {
    /** (Re)schedules for the next Wednesday 10:00, or cancels when the user turns it off. */
    fun setEnabled(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(StatementReminderWorker.WORK)
            return
        }
        val now = Calendar.getInstance()
        val target = now.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, 10)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        while (target.get(Calendar.DAY_OF_WEEK) != Calendar.WEDNESDAY ||
            target.timeInMillis <= now.timeInMillis
        ) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<StatementReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(
            StatementReminderWorker.WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
