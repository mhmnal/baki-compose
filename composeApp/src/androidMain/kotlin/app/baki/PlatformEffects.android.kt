package app.baki

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual object PlatformEffects {
    actual fun reliabilityStatus(): ReliabilityStatus {
        val ctx = AppCtx.ctx ?: return ReliabilityStatus(true, false, true)
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        val active = runCatching { nm.activeNotifications.any { it.id == LiveShiftService.NOTIF_ID } }.getOrDefault(false)
        val allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val power = ctx.getSystemService(android.os.PowerManager::class.java)
        val unrestricted = runCatching { power.isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(false)
        return ReliabilityStatus(allowed, active, unrestricted)
    }

    actual fun openBatterySettings() {
        val ctx = AppCtx.ctx ?: return
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }


    @Suppress("unused")
    private fun vibrate(ms: Long, amplitude: Int) {
        val ctx = AppCtx.ctx ?: return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!v.hasVibrator()) return
        // a missing permission must never break a data operation
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amplitude)) }
    }

    actual fun liveChanged(startMs: Long?, totalCents: Long) {
        val ctx = AppCtx.ctx ?: return
        if (startMs == null) LiveShiftService.stop(ctx)
        else {
            MainActivity.requestLiveNotificationPermission()
            LiveShiftService.update(ctx, startMs, totalCents)
        }
        // the live widget mirrors the shift: start, pause, payout and end all land here
        BakiLiveWidget.refresh(ctx)
        BakiWidget.refresh(ctx)
    }

    actual fun exportFile(name: String, mime: String, content: String): Boolean {
        val ctx = AppCtx.ctx ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("Downloads stream unavailable")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    false
                }
            } else {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
                dir.mkdirs()
                val file = java.io.File(dir, name)
                file.writeText(content)
                file.length() > 0L
            }
        }.getOrDefault(false)
    }

    actual fun dataChanged() { AppCtx.ctx?.let { BakiWidget.refresh(it); BakiLiveWidget.refresh(it) } }
    actual fun log(msg: String) { android.util.Log.i("BakiLive", msg) }
    actual fun haptic(kind: Haptic) {
        val ctx = AppCtx.ctx ?: return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val effect = when (kind) {
                    Haptic.Tick -> VibrationEffect.EFFECT_TICK
                    Haptic.Confirm -> VibrationEffect.EFFECT_CLICK
                    Haptic.Warn -> VibrationEffect.EFFECT_DOUBLE_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                val ms = when (kind) { Haptic.Tick -> 8L; Haptic.Confirm -> 20L; Haptic.Warn -> 12L }
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    actual fun tapFeedback() = haptic(Haptic.Tick)
    actual fun successFeedback() = haptic(Haptic.Confirm)

    actual fun printStatement(html: String) {}

    actual fun notice(text: String) {
        val ctx = AppCtx.ctx ?: return
        runCatching {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }


    actual fun openPhonePreview() {}

    actual fun requestLiveWidget() {
        val ctx = AppCtx.ctx ?: return
        runCatching {
            val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
            if (!mgr.isRequestPinAppWidgetSupported) return
            val provider = android.content.ComponentName(ctx, BakiLiveWidget::class.java)
            mgr.requestPinAppWidget(provider, null, null)
        }
    }
}
