package app.baki

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
    val baki = LocalBaki.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(baki.container).padding(Space.l),
    ) {
        Text("Receive QR", color = baki.onSurface, style = BakiType.bodyStrong )
        Spacer(Modifier.height(Space.s))
        Text(
            "Personal receive QR codes stay on the phone. Import and show them from Baki on Android.",
            color = baki.muted, style = BakiType.caption,
        )
    }
}
