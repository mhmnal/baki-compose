package app.baki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Our own bottom sheet: lives in the app's composition (not a separate window), so the shape and
 * motion are the app's — a spring in, an emphasised slide out, 28dp top corners, drag to dismiss.
 */
@Composable
fun BakiSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val baki = LocalBaki.current
    var visible by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragPx = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }

    LaunchedEffect(Unit) { visible = true }

    // corners square off as the sheet is dragged down, like a sheet being pulled away
    val corner by animateDpAsState(
        targetValue = (28 * (1f - (dragOffset / dragPx).coerceIn(0f, 1f))).dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "corner",
    )

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                initialOffsetY = { it },
            ) + fadeIn(tween(120)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Surface(
                color = baki.container,
                shape = RoundedCornerShape(topStart = corner, topEnd = corner),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragOffset.roundToInt()) },
            ) {
                val visibility = this
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalAnimatedVisibility provides visibility,
                ) {
                Column(Modifier.fillMaxWidth()) {
                    // only the handle drags: the body scrolls, so the two never fight
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset > dragPx) onDismiss()
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                    onVerticalDrag = { change, delta ->
                                        change.consume()
                                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                                    },
                                )
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(baki.muted.copy(alpha = 0.5f)),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        content()
                        Spacer(Modifier.height(8.dp))
                    }
                }
                }
            }
        }
    }
}
