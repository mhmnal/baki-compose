package app.baki

import androidx.compose.runtime.Composable

/** Extracts the text layer of a PDF. Null when the platform cannot read it or the file is not a PDF. */
expect object PdfText {
    fun extract(bytes: ByteArray): String?
}

/** Opens a PDF picker (multi-select) and hands back bytes; null when cancelled, empty when nothing readable. */
@Composable
expect fun rememberStatementImport(onBytes: (List<ByteArray>?) -> Unit): () -> Unit
