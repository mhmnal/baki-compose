package app.baki

import app.baki.db.BakiDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual fun openEncryptedDb(name: String, passphrase: String): EncryptedSpikeDb {
    val ctx = requireNotNull(AppCtx.ctx) { "Android context is not attached" }
    System.loadLibrary("sqlcipher")
    val factory = SupportOpenHelperFactory(passphrase.encodeToByteArray())
    val driver = AndroidSqliteDriver(BakiDatabase.Schema, ctx, name, factory = factory)
    return EncryptedSpikeDb(BakiDatabase(driver)) { driver.close() }
}
