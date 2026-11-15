package app.baki

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Monet scheme, recomputed when the wallpaper palette changes. Remembering only by `dark` meant
 * a wallpaper change while Baki was alive left the old scheme on screen indefinitely.
 */
actual fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun rememberDynamicColorScheme(dark: Boolean): ColorScheme? {
    val ctx = AppCtx.ctx ?: return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(ctx) {
        val manager = WallpaperManager.getInstance(ctx)
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> revision++ }
        val handler = Handler(Looper.getMainLooper())
        runCatching { manager.addOnColorsChangedListener(listener, handler) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                revision++
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                ctx,
                receiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess

        onDispose {
            runCatching { manager.removeOnColorsChangedListener(listener) }
            if (registered) runCatching { ctx.unregisterReceiver(receiver) }
        }
    }

    return remember(dark, revision) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    }
}
