package app.baki

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The app's only toast-like surface. A successful add gets a brief tick that scales in with the
 * snackbar; everything else stays quiet. The snackbar's Undo action is preserved.
 */
@Composable
fun BakiSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val baki = LocalBaki.current
    SnackbarHost(hostState, modifier) { data ->
        val message = data.visuals.message
        val success = message.startsWith("Added ") || message.startsWith("Saved")
        var tick by remember(message) { mutableStateOf(false) }
        LaunchedEffect(message) { tick = true }

        Snackbar(
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(label, color = baki.primary, style = BakiType.labelStrong )
                    }
                }
            },
            shape = RoundedCornerShape(Radius.pill),
            containerColor = baki.surfaceHigh,
            contentColor = baki.onSurface,
            actionContentColor = baki.primary,
            dismissActionContentColor = baki.muted,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = success && tick,
                    enter = scaleIn(
                        initialScale = 0.6f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                    ) + fadeIn(),
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(baki.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = baki.onPrimaryContainer, style = BakiType.labelStrong )
                    }
                }
                if (success) Spacer(Modifier.width(Space.s))
                Text(message, style = BakiType.label)
            }
        }
    }
}
