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
actual fun rememberStatementImport(onBytes: (ByteArray?) -> Unit): () -> Unit = {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Grab statement PDF", "pdf")
    }
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        onBytes(runCatching { chooser.selectedFile.readBytes() }.getOrNull())
    } else onBytes(null)
}
