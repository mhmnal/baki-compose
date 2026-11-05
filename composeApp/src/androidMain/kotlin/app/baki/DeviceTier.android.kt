package app.baki

import android.app.ActivityManager
import android.content.Context
import android.os.Build

actual object DeviceTier {

    private val info: Triple<Boolean, Double, Int> by lazy {
        val ctx = AppCtx.ctx
        val am = ctx?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice ?: false
        val mem = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(mem) }
        val gb = if (mem.totalMem > 0) mem.totalMem / 1e9 else 0.0
        Triple(lowRam, gb, Runtime.getRuntime().availableProcessors())
    }

    actual val level: Int by lazy {
        val (lowRam, gb, cores) = info
        when {
            // Android itself says this is a low-RAM device, or the numbers agree
            lowRam || (gb in 0.1..3.0) || cores <= 4 -> 0
            gb >= 6.0 && cores >= 8 -> 2
            else -> 1
        }
    }

    actual val reason: String by lazy {
        val (lowRam, gb, cores) = info
        val mem = if (gb > 0) "%.1f GB".format(gb) else "RAM unknown"
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        // GLES version is a decent proxy for how the glass shader will be compiled
        val gles = AppCtx.ctx?.let {
            (it.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.deviceConfigurationInfo?.glEsVersion
        } ?: "?"
        "$mem · $cores cores · $model · GLES $gles" + if (lowRam) " · low-RAM" else ""
    }
}
