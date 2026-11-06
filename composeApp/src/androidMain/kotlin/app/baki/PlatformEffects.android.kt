package app.baki

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual object PlatformEffects {

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
        else LiveShiftService.update(ctx, startMs, totalCents)
    }

    actual fun exportFile(name: String, mime: String, content: String) {
        val ctx = AppCtx.ctx ?: return
        runCatching {
            val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, name).apply { writeText(content) }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(send, "Export $name")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        }
    }

    actual fun dataChanged() { AppCtx.ctx?.let { BakiWidget.refresh(it) } }
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
}
