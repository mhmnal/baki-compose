package app.baki

import androidx.compose.runtime.Composable

/**
 * A PDF header may sit a little inside the file (some providers prepend bytes), so inspect the first
 * KiB for "%PDF-" rather than requiring it at offset zero.
 */
fun looksLikePdf(bytes: ByteArray): Boolean {
    val limit = minOf(bytes.size, 1024)
    for (i in 0..limit - 5) {
        if (bytes[i] == '%'.code.toByte() && bytes[i + 1] == 'P'.code.toByte() &&
            bytes[i + 2] == 'D'.code.toByte() && bytes[i + 3] == 'F'.code.toByte() &&
            bytes[i + 4] == '-'.code.toByte()
        ) return true
    }
    return false
}

/** Extracts the text layer of a PDF. Null when the platform cannot read it or the file is not a PDF. */
expect object PdfText {
    fun extract(bytes: ByteArray): String?
}

/** Opens a PDF picker (multi-select) and hands back bytes; null when cancelled, empty when nothing readable. */
@Composable
expect fun rememberStatementImport(onBytes: (List<ByteArray>?) -> Unit): () -> Unit
