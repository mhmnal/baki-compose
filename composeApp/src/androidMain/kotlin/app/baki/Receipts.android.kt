package app.baki

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

actual fun decodeReceiptImage(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
actual fun rememberReceiptPicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) { onPicked(null); return@rememberLauncherForActivityResult }
        val encoded = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            compressReceipt(bytes)
        }.getOrNull()
        onPicked(encoded)
    }
    return remember(launcher) { { launcher.launch("image/*") } }
}

private fun compressReceipt(bytes: ByteArray): String? {
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val max = maxOf(bitmap.width, bitmap.height)
    if (max > 1200) {
        val scale = 1200f / max
        bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }
    var quality = 72
    while (quality >= 32) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val data = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        if (data.length <= MAX_RECEIPT_CHARS) return data
        quality -= 10
    }
    // Last resort: shrink further.
    val scale = 700f / maxOf(bitmap.width, bitmap.height)
    bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 45, out)
    val data = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return data.takeIf { it.length <= MAX_RECEIPT_CHARS }
}
