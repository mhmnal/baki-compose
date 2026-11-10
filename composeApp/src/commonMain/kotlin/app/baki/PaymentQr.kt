package app.baki

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

/** Device-local metadata for a personal receive QR. The image itself lives in a sealed sidecar. */
@Serializable
data class PaymentQrMeta(val id: String, val label: String)

/**
 * A phone-only wallet for personal receive QR codes. Android renders the import/list/viewer; the
 * desktop explains that QR codes belong to the phone. No QR image is ever synced to the server.
 */
@Composable
expect fun PaymentQrSection()
