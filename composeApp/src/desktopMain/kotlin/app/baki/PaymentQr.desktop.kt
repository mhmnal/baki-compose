package app.baki

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
actual fun PaymentQrSection() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerLow).padding(Space.l),
    ) {
        Text("Receive QR", color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) )
        Spacer(Modifier.height(Space.s))
        Text(
            "Personal receive QR codes stay on the phone. Import and show them from Baki on Android.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
    }
}
