package app.baki

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseAndroidTest {
    @Test
    fun readsDesktopWrittenCipherDatabase() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        AppCtx.attach(ctx)
        val fixture = ctx.getExternalFilesDir(null)?.resolve("baki-desktop-spike.db")
        org.junit.Assume.assumeTrue("desktop fixture not supplied", fixture?.exists() == true)
        ctx.deleteDatabase("baki-spike.db")
        fixture!!.copyTo(ctx.getDatabasePath("baki-spike.db"), overwrite = true)
        val handle = openEncryptedSpikeDb("cross-platform-key")
        try {
            assertEquals("from-desktop", handle.db.probeQueries.selectProbe("cross-desktop").executeAsOne().value_)
        } finally { handle.close() }
        ctx.deleteDatabase("baki-spike.db")
    }

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
    }

    @Test
    fun writeCrossPlatformFixture() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        AppCtx.attach(ctx)
        ctx.deleteDatabase("baki-spike.db")
        val first = openEncryptedSpikeDb("cross-platform-key")
        try {
            first.db.probeQueries.insertProbe("cross-android", "from-android", "2026-11-23T21:00:00")
        } finally { first.close() }
        // Export through shell so the package can be removed after instrumentation.
        shell("run-as ${ctx.packageName} cat databases/baki-spike.db > /sdcard/Download/baki-android-spike.db")
        val wal = ctx.getDatabasePath("baki-spike.db-wal")
        if (wal.exists()) {
            shell("run-as ${ctx.packageName} cat databases/baki-spike.db-wal > /sdcard/Download/baki-android-spike.db-wal")
        }
    }

    @Test
    fun encryptedSqliteRoundTripOnAndroid() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        AppCtx.attach(ctx)
        ctx.deleteDatabase("baki-spike.db")
        val file = ctx.getDatabasePath("baki-spike.db")
        val pass = "g4-android-passphrase"
        val first = openEncryptedSpikeDb(pass)
        try {
            first.db.probeQueries.insertProbe("android-1", "android-secret", "2026-11-23T21:00:00")
            assertEquals(1, first.db.probeQueries.countProbe().executeAsOne())
        } finally { first.close() }
        assertTrue(file.exists())
        assertFalse(file.readBytes().decodeToString().contains("android-secret"))
        val second = openEncryptedSpikeDb(pass)
        try {
            assertEquals("android-secret", second.db.probeQueries.selectProbe("android-1").executeAsOne().value_)
        } finally { second.close() }
        ctx.deleteDatabase("baki-spike.db")
    }
}
