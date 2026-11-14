package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Receipts are small JPEGs encoded as base64 so they travel with the synced store. */
const val MAX_RECEIPT_CHARS = 180_000

expect fun decodeReceiptImage(base64: String): ImageBitmap?

@Composable
expect fun rememberReceiptPicker(onPicked: (String?) -> Unit): () -> Unit
