package app.baki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standard Material 3 modal bottom sheet.
 *
 * The sheet owns the scrim, drag handle, 28dp shape and dismiss gesture. The app deliberately
 * does not blur the content behind it: MD3 uses a standard scrim and tonal container instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BakiSheet(
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
            if (footer != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                ) {
                    footer()
                }
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 20.dp))
            }
        }
    }
}
