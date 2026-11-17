package app.baki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The real phone shell, in its own 390×844 window so sheets and dialogs size correctly. */
@Composable
fun PhonePreviewPhone(repo: BakiRepo) {
    App(repo)
}

/** Desktop-only controls for the phone preview; lives in a separate window. */
@Composable
fun PhonePreviewControls(repo: BakiRepo) {
    BakiTheme(
        mode = repo.config.themeMode,
        colorSource = repo.config.colorSource,
        highContrast = repo.config.highContrast,
        reducedMotion = repo.config.reducedMotion,
    ) {
        val scheme = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize().background(scheme.background).padding(Space.l).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("PHONE PREVIEW", color = scheme.primary, letterSpacing = 1.6.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(2.dp))
                    Text("interactive · isolated preview copy", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "v$APP_VERSION",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(start = Space.s, top = 1.dp),
                )
            }
            Surface(shape = MaterialTheme.shapes.large, color = scheme.tertiaryContainer) {
                Text(
                    "Writes stay in the preview store. No server is attached.",
                    color = scheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Space.m),
                )
            }
            DeskLabel("APPEARANCE")
            ConnectedChoiceGroup(
                options = listOf("Auto", "Light", "Dark"),
                selected = when (repo.config.themeMode) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    else -> "Auto"
                },
                onSelect = { repo.setThemeMode(it.lowercase()) },
            )
            ConnectedChoiceGroup(
                options = listOf("System", "Baki"),
                selected = if (repo.config.colorSource == "baki") "Baki" else "System",
                onSelect = { repo.setColorSource(if (it == "Baki") "baki" else "wallpaper") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("High contrast", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text("stronger text and outlines", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = repo.config.highContrast, onCheckedChange = { repo.setHighContrast(it) })
            }
            DeskLabel("MOTION LAB")
            ConnectedChoiceGroup(
                options = listOf("Expressive", "Standard", "Slow"),
                selected = when (PhonePreview.motionMode) {
                    "standard" -> "Standard"
                    "slow" -> "Slow"
                    else -> "Expressive"
                },
                onSelect = { PhonePreview.motionMode = it.lowercase() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reduced motion", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text("standard scheme, no overshoot", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = repo.config.reducedMotion, onCheckedChange = { repo.setReducedMotion(it) })
            }
            Text(
                "Use Slow for spring shape and overshoot. Use Standard for the utilitarian baseline. Real frame pacing still needs the Android emulator/phone.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            DeskLabel("TRY")
            Text(
                "Open quick actions, switch days, pull a sheet, and watch the shared bounds/springs. Navigation is live.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
