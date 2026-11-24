package app.baki

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import java.lang.ref.WeakReference
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

private const val ACTION_OPEN_QR = "app.baki.OPEN_QR"
private const val ACTION_QUICK_ADD = "app.baki.QUICK_ADD"

class MainActivity : ComponentActivity() {

    companion object {
        private var current: WeakReference<MainActivity>? = null

        /** Called when a live shift starts; the foreground-service notification needs this on 13+. */
        fun requestLiveNotificationPermission() {
            current?.get()?.requestNotificationPermission()
        }
    }

    private var notificationRequested = false
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val live = runCatching { loadLive() }.getOrNull()
                if (live != null) LiveShiftService.update(this, live.start, liveTotalCents(live))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        current = WeakReference(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppCtx.ctx = applicationContext
        LocalStore.attach(filesDir)
        seedVerifyStatements()
        installShortcuts()
        drainIntent(intent)
        if (isFinishing) return
        askLocationPermission()

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

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        drainIntent(intent)
    }

    /** Notification actions arrive as "payout" / "end"; the UI picks them up. */
    private fun drainIntent(intent: Intent?) {
        // A shortcut or notification can open the one capture path without navigating the app first.
        if (intent?.action == ACTION_QUICK_ADD) {
            QuickCaptureRequest.open(
                QuickAddRequest(
                    type = intent.getStringExtra("baki.type") ?: "expense",
                    category = intent.getStringExtra("baki.category"),
                    source = intent.getStringExtra("baki.source") ?: "shortcut",
                ),
            )
            return
        }
        // an old shortcut may still point at MainActivity until the next launch; never expose the ledger
        if (intent?.action == ACTION_OPEN_QR) {
            startActivity(
                Intent(this, QrActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            return
        }
        val act = intent?.getStringExtra("scheme.action")
        android.util.Log.i("BakiLive", "drainIntent: $act (intent=${intent?.action})")
        if (act == "quick_add") {
            QuickCaptureRequest.open(QuickAddRequest(source = "notification"))
        } else {
            act?.let { BakiIntents.deliver(it) }
        }
        readStatement(intent)
    }

    /** Long-press the launcher icon: one capture path, plus the existing payment QR. */
    private fun installShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        runCatching {
            val manager = getSystemService(ShortcutManager::class.java) ?: return
            fun quick(id: String, label: String, category: String?): ShortcutInfo {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_QUICK_ADD
                    putExtra("baki.type", "expense")
                    category?.let { putExtra("baki.category", it) }
                    putExtra("baki.source", "shortcut")
                }
                return ShortcutInfo.Builder(this, id)
                    .setShortLabel(label)
                    .setLongLabel(if (category != null) "Add $label" else "Add expense")
                    .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build()
            }
            val qr = ShortcutInfo.Builder(this, "payment_qr")
                .setShortLabel("My QR")
                .setLongLabel("Show payment QR")
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_QR))
                .build()
            manager.dynamicShortcuts = listOf(
                quick("quick_expense", "Expense", null),
                quick("quick_fuel", "Fuel", "fuel"),
                quick("quick_toll", "Toll", "toll"),
                qr,
            )
        }
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
        // some providers hand PDFs over as octet-stream; accept the plausible types and sniff below
        if (mime != null && mime != "application/pdf" && mime != "application/octet-stream") return
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
            if (bytes == null) return@Thread
            if (!looksLikePdf(bytes)) {
                runOnUiThread { PlatformEffects.notice("That file doesn't look like a PDF.") }
                return@Thread
            }
            runOnUiThread { SharedStatement.pending = bytes }
        }.start()
    }

    private val askLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* optional */ }

    /** Test-only seed for the isolated .verify package; never present in the normal app. */
    private fun seedVerifyStatements() {
        if (packageName.endsWith(".verify").not()) return
        // A clean-restore fixture, if supplied by the Android test harness.
        val fixture = getExternalFilesDir(null)?.resolve("baki-restore-test.json") ?: java.io.File("/sdcard/Download/baki-restore-test.json")
        if (fixture.exists()) {
            runCatching {
                val text = fixture.readText()
                val bundle = BakiJson.decodeFromString<BakiBackupBundle>(text)
                saveLocal(bundle.store)
            }
            return
        }
        val current = runCatching { loadLocal() }.getOrNull() ?: return
        if (current.importedTxns.isNotEmpty()) return
        val stamp = nowIso()
        runCatching { saveConfig(loadConfig().copy(allowWrites = true)) }
        val aeon = Account(id = "test-aeon", name = "AEON Bank — Savings Account-i", kind = "bank")
        val tng = Account(id = "test-tng", name = "TNG eWallet", kind = "ewallet")
        val batch = StatementBatch(
            id = "test-batch", accountId = aeon.id, source = "aeon", importedAt = stamp,
            periodStart = "2026-10-01", periodEnd = "2026-10-02", rowCount = 2, duplicateCount = 0,
        )
        val rows = listOf(
            ImportedTxn(
                id = "test-row-1", accountId = aeon.id, date = "2026-10-01",
                description = "TEST COFFEE · DuitNowQR", amount = 1230, direction = "out",
                source = "aeon", status = "pending", batchId = batch.id,
                suggestedEntryId = null, suggestedPairId = "", updated = stamp,
            ),
            ImportedTxn(
                id = "test-row-2", accountId = tng.id, date = "2026-10-02",
                description = "TEST RELOAD · transfer", amount = 5000, direction = "in",
                transfer = true, source = "tng", status = "matched", entryId = null,
                batchId = "", updated = stamp,
            ),
        )
        saveLocal(current.copy(accounts = listOf(aeon, tng), importedTxns = rows, statementBatches = listOf(batch)))
    }

    /** Requested from the live-shift start path, not at launch. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationRequested) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        notificationRequested = true
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun askLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

}
