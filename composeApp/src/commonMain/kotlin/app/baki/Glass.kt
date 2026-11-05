package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Liquid glass via Haze's refraction engine. The content is captured once by
 * `hazeSource(hazeState)`; this surface samples it, so the bend is real.
 *
 * LOW tier degrades to a plain Material surface and never touches Haze.
 */
@Composable
fun GlassSurface(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    tier: Int,
    modifier: Modifier = Modifier,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Default,
    content: @Composable BoxScope.() -> Unit,
) {
    val baki = LocalBaki.current

    if (tier <= 0) {
        Box(modifier.clip(shape).background(baki.container)) { content() }
        return
    }

    val style: GlassStyle = remember(tier) {
        val base = if (tier >= 2) GlassStyle.regular else GlassStyle.clear
        base.then {
            shape(shape)
            chromaticAberrationStrength(0.45f)
            specularIntensity(0.7f)
            surfaceProfile(dev.chrisbanes.haze.glass.SurfaceProfile.Lip)
        }
    }

    Box(
        modifier
            .clip(shape)
            // keep the content visible through the glass, but stop labels colliding with it
            .background(baki.surface.copy(alpha = 0.45f))
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
                performanceMode = performanceMode,
            ),
    ) {
        content()
    }
}
