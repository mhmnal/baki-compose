package app.baki

import androidx.compose.runtime.Composable

/** Extracts the text layer of a PDF. Null when the platform cannot read it or the file is not a PDF. */
expect object PdfText {
    fun extract(bytes: ByteArray): String?
}

/** Opens a PDF picker and hands back its bytes (null when the user cancels). */
@Composable
expect fun rememberStatementImport(onBytes: (ByteArray?) -> Unit): () -> Unit
