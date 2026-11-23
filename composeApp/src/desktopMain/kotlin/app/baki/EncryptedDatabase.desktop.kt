package app.baki

import app.baki.db.BakiDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.io.File

actual fun openEncryptedDb(name: String, passphrase: String): EncryptedSpikeDb {
    Class.forName("org.sqlite.JDBC")
    val dir = File(System.getProperty("user.home") ?: ".", ".local/share/baki").apply { mkdirs() }
    val file = File(dir, name)
    val props = SQLiteMCSqlCipherConfig.getV4Defaults().withKey(passphrase).build().toProperties()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", props, BakiDatabase.Schema)
    return EncryptedSpikeDb(BakiDatabase(driver)) { driver.close() }
}
