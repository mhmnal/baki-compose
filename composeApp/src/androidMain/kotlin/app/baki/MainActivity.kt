package app.baki

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppCtx.ctx = applicationContext
        LocalStore.attach(filesDir)
        askNotificationPermission()
        askLocationPermission()
        drainIntent(intent)

        setContent {
            val dark = isSystemInDarkTheme()
            // status/navigation icons must invert with the theme, not follow the system default
            SideEffect {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        drainIntent(intent)
    }

    /** Notification actions arrive as "payout" / "end"; the UI picks them up. */
    private fun drainIntent(intent: Intent?) {
        val act = intent?.getStringExtra("baki.action")
        android.util.Log.i("BakiLive", "drainIntent: $act (intent=${intent?.action})")
        act?.let { BakiIntents.deliver(it) }
        readStatement(intent)
    }

    /** A PDF shared from another app becomes a statement import. */
    private fun readStatement(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        val mime = intent.type ?: runCatching { contentResolver.getType(uri) }.getOrNull()
        if (mime != "application/pdf") return
        Thread {
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 25 * 1024 * 1024) return@runCatching null
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            }.getOrNull()
            if (bytes != null) runOnUiThread { SharedStatement.pending = bytes }
        }.start()
    }

    private val askLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* optional */ }

    private fun askLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
