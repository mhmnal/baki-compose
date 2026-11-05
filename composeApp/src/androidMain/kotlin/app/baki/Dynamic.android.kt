package app.baki

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/** Android 12+ wallpaper colours, mapped onto the app's palette roles. */
actual fun dynamicPalette(dark: Boolean): BakiPalette? {
    val ctx = AppCtx.ctx ?: return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    return BakiPalette(
        dark = dark,
        primary = s.primary, onPrimary = s.onPrimary,
        primaryContainer = s.primaryContainer, onPrimaryContainer = s.onPrimaryContainer,
        accentStrong = if (dark) s.primaryContainer else s.primary,
        onAccent = if (dark) s.onPrimaryContainer else s.onPrimary,
        surface = if (dark) s.surface else s.surface,
        container = s.surfaceContainer,
        surfaceHigh = s.surfaceContainerHigh,
        onSurface = s.onSurface,
        muted = s.onSurfaceVariant,
        up = s.tertiary, down = s.error, warn = s.secondary,
    )
}
