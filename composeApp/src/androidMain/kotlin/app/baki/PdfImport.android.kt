package app.baki

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

actual object PdfText {
    actual fun extract(bytes: ByteArray): String? = runCatching {
        AppCtx.ctx?.let { PDFBoxResourceLoader.init(it) }
        PDDocument.load(bytes).use { doc -> PDFTextStripper().getText(doc) }
    }.getOrNull()
}

@Composable
actual fun rememberStatementImport(onBytes: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) { onBytes(null); return@rememberLauncherForActivityResult }
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        onBytes(bytes)
    }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}
