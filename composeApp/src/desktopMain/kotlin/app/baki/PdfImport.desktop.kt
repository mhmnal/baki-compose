package app.baki

import androidx.compose.runtime.Composable

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

actual object PdfText {
    actual fun extract(bytes: ByteArray): String? = runCatching {
        PDDocument.load(bytes).use { doc -> PDFTextStripper().getText(doc) }
    }.getOrNull()
}

@Composable
actual fun rememberStatementImport(onBytes: (List<ByteArray>?) -> Unit): () -> Unit = {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Statement PDF / CSV", "pdf", "csv")
    }
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        onBytes(chooser.selectedFiles.mapNotNull { file -> runCatching { file.readBytes() }.getOrNull() })
    } else onBytes(null)
}
