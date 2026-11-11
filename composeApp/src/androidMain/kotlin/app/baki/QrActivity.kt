package app.baki

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val QrInk = Color(0xFF1C2117)

/** Customer-facing only: a QR, a label selector, Close. No dashboard, no amounts, no route back. */
class QrActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QR_ID = "app.baki.QR_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LocalStore.attach(filesDir)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishAndRemoveTask()
        })

        val selectedId = intent?.getStringExtra(EXTRA_QR_ID)
        setContent {
            val faces = bakiFontFamilies()
            BakiType.install(faces.first, faces.second, faces.third)
            BakiTheme(mode = "light") {
                val view = LocalView.current
                androidx.compose.runtime.SideEffect {
                    val bars = WindowCompat.getInsetsController(window, view)
                    bars.isAppearanceLightStatusBars = true
                    bars.isAppearanceLightNavigationBars = true
                }
                QrCustomerScreen(initialId = selectedId, onClose = { finishAndRemoveTask() })
            }
        }
    }
}

@Composable
private fun QrCustomerScreen(initialId: String?, onClose: () -> Unit) {
    var items by remember { mutableStateOf(loadQrIndex()) }
    var selected by remember {
        mutableIntStateOf(items.indexOfFirst { it.id == initialId }.takeIf { it >= 0 } ?: 0)
    }
    val meta = items.getOrNull(selected)

    Column(
        Modifier.fillMaxSize().background(Color.White).systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Receive payment", color = QrInk, style = BakiType.title )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = QrInk, style = BakiType.labelStrong) }
        }

        if (items.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { index, item ->
                    val on = index == selected
                    Text(
                        item.label,
                        color = if (on) QrInk else QrInk.copy(alpha = 0.55f),
                        style = BakiType.labelStrong,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Color(0xFFE6F0DC) else Color.Transparent)
                            .clickable { selected = index }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (meta == null) {
                Text("No QR saved", color = QrInk, style = BakiType.bodyStrong)
            } else {
                val bitmap = remember(meta.id) { loadQrBitmap(meta.id) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Payment QR for ${meta.label}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
                    )
                } else {
                    Text("This QR could not be read", color = QrInk, style = BakiType.bodyStrong)
                }
            }
        }

        Text(
            "Scan with any banking or e-wallet app",
            color = QrInk.copy(alpha = 0.6f), style = BakiType.caption,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
        )
    }
}
