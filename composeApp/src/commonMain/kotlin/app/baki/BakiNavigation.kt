package app.baki

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Baki's own floating navigation dock. It keeps the M3 tab semantics but uses the soft-burst
 * selection mark instead of the stock navigation indicator.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BakiBottomNavigation(
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.m),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            labels.forEachIndexed { i, label ->
                BottomItem(
                    label = label,
                    icon = icons[i],
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bottomItemScale",
    )
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("nav-$label")
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(if (selected) MaterialShapes.SoftBurst.toShape() else CircleShape)
                .background(if (selected) scheme.primary else ColorTransparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
        )
    }
}

private val ColorTransparent = androidx.compose.ui.graphics.Color.Transparent

/**
 * Floating wide rail with animated width, the same shape mark, and a labelled expand/collapse.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BakiWideNavigation(
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (Int) -> Unit,
    showAdd: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val width by animateDpAsState(
        targetValue = if (expanded) 236.dp else 92.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "railWidth",
    )
    Surface(
        modifier = modifier.width(width).fillMaxHeight().padding(vertical = Space.m, horizontal = Space.s),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxHeight().padding(horizontal = if (expanded) Space.m else Space.s)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(scheme.primary),
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = if (expanded) "Collapse navigation" else "Expand navigation"
                    },
                ) {
                    Text(if (expanded) "‹" else "›", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (showAdd) {
                Spacer(Modifier.height(Space.s))
                if (expanded) {
                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("+ Add spending", maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                } else {
                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
            Spacer(Modifier.height(Space.l))
            labels.forEachIndexed { i, label ->
                RailItem(
                    label = label,
                    icon = icons[i],
                    selected = selected == i,
                    expanded = expanded,
                    onClick = { onSelect(i) },
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("nav-$label")
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            }
            .padding(horizontal = if (expanded) Space.s else 0.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(if (selected) MaterialShapes.SoftBurst.toShape() else CircleShape)
                .background(if (selected) scheme.primary else ColorTransparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.width(Space.m))
            Text(
                label,
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            )
        }
    }
}
