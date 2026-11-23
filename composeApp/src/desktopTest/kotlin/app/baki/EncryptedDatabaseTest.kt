package app.baki

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File

class EncryptedDatabaseTest {
    private val dbFile = File(System.getProperty("user.home") ?: ".", ".local/share/baki/baki-spike.db")

    @Test
    fun createDesktopFixtureForAndroid() {
        val out = File("/tmp/baki-desktop-spike.db")
        out.delete()
        Class.forName("org.sqlite.JDBC")
        val props = SQLiteMCSqlCipherConfig.getV4Defaults().withKey("cross-platform-key").build().toProperties()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${out.absolutePath}", props, app.baki.db.BakiDatabase.Schema)
        try {
            val db = app.baki.db.BakiDatabase(driver)
            db.probeQueries.insertProbe("cross-desktop", "from-desktop", "2026-11-23T21:00:00")
        } finally { driver.close() }
        assertTrue(out.exists())
    }

    @Test
    fun readsAndroidWrittenCipherDatabase() {
        val path = System.getProperty("baki.androidDb").orEmpty()
        if (path.isBlank() || !File(path).exists()) return
        Class.forName("org.sqlite.JDBC")
        val props = SQLiteMCSqlCipherConfig.getV4Defaults().withKey("cross-platform-key").build().toProperties()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path", props)
        try {
            val db = app.baki.db.BakiDatabase(driver)
            assertEquals("from-android", db.probeQueries.selectProbe("cross-android").executeAsOne().value_)
        } finally { driver.close() }
    }

    @Test
    fun schemaV1MigratesToV2WithoutLosingTheDatabase() {
        dbFile.delete()
        Class.forName("org.sqlite.JDBC")
        val props = SQLiteMCSqlCipherConfig.getV4Defaults().withKey("migration-key").build().toProperties()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", props)
        try {
            driver.execute(null, "CREATE TABLE probe (id TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL, updated TEXT NOT NULL)", 0)
            driver.execute(null, "PRAGMA user_version = 1", 0)
            app.baki.db.BakiDatabase.Schema.migrate(driver, 1, 2).value
            driver.close()
            val conn = java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}", props)
            val hasMarker = conn.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(probe)").use { rs ->
                    var found = false
                    while (rs.next()) if (rs.getString("name") == "marker") found = true
                    found
                }
            }
            conn.close()
            assertTrue(hasMarker)
        } finally {
            runCatching { driver.close() }
            dbFile.delete()
        }
    }

    @Test
    fun tenThousandRowEncryptedDatabaseRemainsQueryable() {
        dbFile.delete()
        val passphrase = "scale-key"
        openEncryptedSpikeDb(passphrase).close()
        Class.forName("org.sqlite.JDBC")
        val props = SQLiteMCSqlCipherConfig.getV4Defaults().withKey(passphrase).build().toProperties()
        val conn = java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}", props)
        conn.autoCommit = false
        conn.prepareStatement("INSERT INTO probe(id, value, updated) VALUES (?, ?, ?)").use { st ->
            repeat(10_000) { i ->
                st.setString(1, "scale-$i")
                st.setString(2, "value-$i")
                st.setString(3, "2026-11-23T21:00:00")
                st.addBatch()
            }
            st.executeBatch()
        }
        conn.commit()
        conn.close()
        val started = System.nanoTime()
        val handle = openEncryptedSpikeDb(passphrase)
        try {
            val count = handle.db.probeQueries.countProbe().executeAsOne()
            assertEquals(10_000, count)
            assertEquals("value-9999", handle.db.probeQueries.selectProbe("scale-9999").executeAsOne().value_)
        } finally { handle.close() }
        println("[g4] 10k encrypted open/query: ${(System.nanoTime() - started) / 1_000_000} ms")
        dbFile.delete()
    }

    @Test
    fun encryptedDatabasePersistsAndRejectsTheWrongKey() {
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
        val passphrase = "g4-spike-passphrase"
        val first = openEncryptedSpikeDb(passphrase)
        try {
            first.db.probeQueries.insertProbe("p1", "not-plaintext-secret", "2026-11-23T21:00:00")
            assertEquals(1, first.db.probeQueries.countProbe().executeAsOne())
        } finally { first.close() }
        val raw = dbFile.readBytes().decodeToString()
        assertFalse(raw.contains("not-plaintext-secret"), "database file must not expose the probe value")
        val second = openEncryptedSpikeDb(passphrase)
        try {
            assertEquals("not-plaintext-secret", second.db.probeQueries.selectProbe("p1").executeAsOne().value_)
        } finally { second.close() }
        assertFails {
            val wrong = openEncryptedSpikeDb("wrong-passphrase")
            try { wrong.db.probeQueries.selectProbe("p1").executeAsOne() } finally { wrong.close() }
        }
        dbFile.delete()
    }
}
