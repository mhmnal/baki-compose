package app.baki

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

actual fun dynamicColorScheme(dark: Boolean): ColorScheme? {
    val ctx = AppCtx.ctx ?: return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
}
