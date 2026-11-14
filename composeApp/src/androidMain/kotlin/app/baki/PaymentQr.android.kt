package app.baki

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.MaterialTheme

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import kotlinx.serialization.builtins.ListSerializer

private const val QR_INDEX = "payment-qr.json"
private const val QR_MAX = 3

internal fun loadQrIndex(): List<PaymentQrMeta> {
    val raw = runCatching { LocalStore.readText(QR_INDEX) }.getOrNull() ?: return emptyList()
    return runCatching {
        BakiJson.decodeFromString(ListSerializer(PaymentQrMeta.serializer()), raw)
    }.getOrDefault(emptyList())
}

private fun saveQrIndex(items: List<PaymentQrMeta>) {
    LocalStore.writeText(QR_INDEX, BakiJson.encodeToString(ListSerializer(PaymentQrMeta.serializer()), items))
}

private fun qrName(id: String) = "qr-$id.b64"

private fun saveQrImage(id: String, bytes: ByteArray) {
    LocalStore.writeText(qrName(id), Base64.encodeToString(bytes, Base64.NO_WRAP))
}

internal fun loadQrBitmap(id: String): android.graphics.Bitmap? {
    val raw = runCatching { LocalStore.readText(qrName(id)) }.getOrNull() ?: return null
    val bytes = runCatching { Base64.decode(raw, Base64.NO_WRAP) }.getOrNull() ?: return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

@Composable
actual fun PaymentQrSection() {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var items by remember { mutableStateOf(loadQrIndex()) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var pendingLabel by remember { mutableStateOf("Receive QR") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty() && BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
            pending = bytes
            pendingLabel = "Receive QR"
        } else {
            PlatformEffects.notice("That file isn't a readable image.")
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.m),
    ) {
        Text("Receive QR", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
        Text(
            "Your own receive QR codes, kept on this phone. No bank login and no speed-of-light refresh: " +
                "if a code rotates, re-import it.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.m))

        if (items.isEmpty()) {
            Text("No QR saved yet.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium )
            Spacer(Modifier.height(Space.s))
        } else {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().background(scheme.surface)
                        .clickable {
                            context.startActivity(
                                Intent(context, QrActivity::class.java)
                                    .putExtra(QrActivity.EXTRA_QR_ID, item.id)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                            )
                        }.heightIn(min = Control.primary).padding(vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(MaterialTheme.shapes.medium).background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text("QR", color = scheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) ) }
                    Spacer(Modifier.width(Space.s))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
                        Text("tap to show", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall )
                    }
                    TextButton(
                        onClick = {
                            items = items.filterNot { it.id == item.id }
                            saveQrIndex(items)
                            LocalStore.writeText(qrName(item.id), "")
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Remove", color = scheme.error, style = MaterialTheme.typography.labelSmall ) }
                }
            }
        }

        Button(
            onClick = { picker.launch("image/*") },
            enabled = items.size < QR_MAX,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
            shape = CircleShape,
        ) {
            Text(if (items.size >= QR_MAX) "Maximum 3 QR codes" else "Add receive QR", style = MaterialTheme.typography.labelLarge )
        }
        Spacer(Modifier.height(Space.s))
    }

    pending?.let { bytes ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = scheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Name this QR", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = pendingLabel,
                    onValueChange = { pendingLabel = it.take(30) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = bakiFieldColorsPublic(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pendingLabel.isNotBlank() && items.size < QR_MAX,
                    onClick = {
                        val id = "qr-${System.currentTimeMillis()}"
                        val label = pendingLabel.trim()
                        saveQrImage(id, bytes)
                        items = items + PaymentQrMeta(id, label)
                        saveQrIndex(items)
                        pending = null
                    },
                ) { Text("Add QR", color = scheme.primary) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel", color = scheme.onSurfaceVariant) } },
        )
    }

}

@Composable
private fun PaymentQrViewer(meta: PaymentQrMeta, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    val bitmap = remember(meta.id) { loadQrBitmap(meta.id) }

    // Keep the screen awake for the customer's scan; brightness is deliberately left to the user.
    // The QR is white, so force dark system-bar icons while it is open.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            view.keepScreenOn = false
            oldStatus?.let { controller?.isAppearanceLightStatusBars = it }
            oldNav?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.White).systemBarsPadding(),
        ) {
            Text(
                meta.label,
                color = Color(0xFF1C2117), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
            )
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Text("Close", color = Color(0xFF1C2117), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) )
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Payment QR for ${meta.label}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(horizontal = Space.l, vertical = 72.dp),
                )
            } else {
                Text(
                    "This QR image could not be read. Remove it and import again.",
                    color = Color(0xFF1C2117), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }
}
