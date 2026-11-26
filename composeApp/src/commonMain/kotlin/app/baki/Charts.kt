package app.baki

import androidx.compose.material3.toShape

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.sp
import kotlin.math.abs


/** Net per day across the month, as bars above and below a zero line. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoursHeatmap(store: Store) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(store) { hourHeatmap(store) }
    if (blocks.isEmpty()) return
    val peak = blocks.maxOf { it.rate }.coerceAtLeast(1.0)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")

    Column {
        Text(
            "/ NET BY HOUR",
            color = scheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(Space.l))
            listOf("00", "04", "08", "12", "16", "20").forEach {
                Text(it, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium )
            }
        }
        Spacer(Modifier.height(3.dp))
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        for (d in 0..6) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(days[d], color = scheme.onSurfaceVariant, modifier = Modifier.width(14.dp), style = MaterialTheme.typography.bodyMedium )
                for (b in 0..5) {
                    val cell = blocks.firstOrNull { it.day == d && it.block == b }
                    val rate = cell?.rate ?: 0.0
                    val has = (cell?.hours ?: 0.0) >= 0.25
                    val fraction = if (has) (rate / peak).coerceIn(0.08, 1.0).toFloat() else 0f
                    val start = "%02d:00".format(b * 4)
                    val end = "%02d:00".format((b + 1) * 4)
                    val blockDescription = if (has) {
                        "${dayNames[d]} $start–$end, RM ${"%.0f".format(rate)} per hour"
                    } else {
                        "${dayNames[d]} $start–$end, no driving"
                    }
                    Box(
                        Modifier.weight(1f).height(16.dp)
                            .clip(if (has) MaterialTheme.shapes.small else MaterialTheme.shapes.extraSmall)
                            .background(if (has) scheme.primary.copy(alpha = 0.15f + fraction * 0.85f) else scheme.surfaceContainerHigh)
                            .semantics { contentDescription = blockDescription },
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text("darker = higher net · empty = not driven", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
    }
}


