package app.baki

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Desktop-only surfaces. The phone never reaches this enum. */
internal enum class DeskScreen(val label: String, val subtitle: String, val icon: ImageVector) {
    OVERVIEW("Overview", "the month at a glance", BakiIcons.Today),
    LEDGER("Ledger", "every entry, read-only", BakiIcons.Receipt),
    SHIFTS("Shifts", "what the road paid", BakiIcons.Car),
    ROUTES("Routes", "the drives behind the numbers", BakiIcons.Route),
    COMPARE("Compare", "two periods, side by side", BakiIcons.Insights),
    YEAR("Year", "the long view", BakiIcons.Doc),
    STATEMENTS("Statements", "evidence, not a second ledger", BakiIcons.Receipt),
    CLOSE("Close month", "finish the month cleanly", BakiIcons.Doc),
    SETTINGS("Settings", "connection and appearance", BakiIcons.More),
}

@Composable
fun DeskApp(repo: BakiRepo) {
    LaunchedEffect(Unit) { repo.start() }
    var screen by remember {
        mutableStateOf(
            when (PhonePreview.desktopView) {
                "entries" -> DeskScreen.LEDGER
                "shifts" -> DeskScreen.SHIFTS
                "routes" -> DeskScreen.ROUTES
                "compare" -> DeskScreen.COMPARE
                "year", "statement" -> DeskScreen.YEAR
                "settings" -> DeskScreen.SETTINGS
                else -> DeskScreen.OVERVIEW
            },
        )
    }
    var month by remember { mutableStateOf(todayIso().take(7)) }
    val scheme = MaterialTheme.colorScheme

    Row(Modifier.fillMaxSize().background(scheme.background)) {
        DeskRail(selected = screen, onSelect = { screen = it })
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DeskHeader(repo = repo, screen = screen, month = month, onMonth = { month = it })
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "desk-screen",
            ) { current ->
                when (current) {
                    DeskScreen.OVERVIEW -> DeskOverview(repo, month)
                    DeskScreen.LEDGER -> DeskLedger(repo, month)
                    DeskScreen.SHIFTS -> DeskShifts(repo, month)
                    DeskScreen.ROUTES -> DeskRoutes(repo, month)
                    DeskScreen.COMPARE -> DeskCompare(repo, month)
                    DeskScreen.YEAR -> DeskYear(repo, month)
                    DeskScreen.STATEMENTS -> DeskStatements(repo)
                    DeskScreen.CLOSE -> DeskMonthClose(repo, month)
                    DeskScreen.SETTINGS -> DeskSettings(repo)
                }
            }
        }
    }
}

@Composable
private fun DeskRail(selected: DeskScreen, onSelect: (DeskScreen) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxHeight().width(228.dp).padding(Space.m),
    ) {
        Column(Modifier.fillMaxSize().padding(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(scheme.primary))
                Spacer(Modifier.width(Space.s))
                Column {
                    Text("BAKI", color = scheme.onSurface, letterSpacing = 2.sp, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black))
                    Text("DESK", color = scheme.onSurfaceVariant, letterSpacing = 2.sp, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(Space.xl))
            DeskScreen.entries.forEach { item ->
                DeskRailItem(item, selected == item) { onSelect(item) }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerHigh) {
                Column(Modifier.padding(Space.m)) {
                    Text("READ-ONLY", color = scheme.tertiary, letterSpacing = 1.4.sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(Space.xs))
                    Text("Ledger editing happens on the phone.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DeskRailItem(item: DeskScreen, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (active) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = active
                contentDescription = item.label
            }
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(item.icon, contentDescription = null, tint = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            item.label,
            color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
        )
    }
}

@Composable
private fun DeskHeader(repo: BakiRepo, screen: DeskScreen, month: String, onMonth: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(start = Space.l, end = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(screen.label.uppercase(), color = scheme.primary, letterSpacing = 1.6.sp, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            Text(screen.subtitle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        if (screen == DeskScreen.OVERVIEW || screen == DeskScreen.LEDGER || screen == DeskScreen.SHIFTS || screen == DeskScreen.ROUTES) {
            DeskMonthStepper(month, onMonth, compact = true)
            Spacer(Modifier.width(Space.m))
        }
        Surface(shape = CircleShape, color = scheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = Space.m, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (repo.status == "offline") scheme.error else scheme.tertiary))
                Text("desk · read-only · ${repo.status}", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun DeskMonthStepper(month: String, onMonth: (String) -> Unit, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        DeskArrow("‹", "Previous month") { onMonth(addDays("$month-01", -1).take(7)) }
        Text(
            monthLabel(month),
            color = scheme.onSurface,
            style = if (compact) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = Space.xs),
        )
        DeskArrow("›", "Next month") { onMonth(addDays("$month-01", 32).take(7)) }
    }
}

@Composable
internal fun DeskArrow(label: String, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium) }
}
