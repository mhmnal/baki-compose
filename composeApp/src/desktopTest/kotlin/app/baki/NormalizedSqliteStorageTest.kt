package app.baki

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class NormalizedSqliteStorageTest {
    @Test
    fun importsTheJsonSnapshotDirectlyWhenNoG5aDatabaseExists() {
        val previous = loadLocal()
        val tag = Random.nextLong().toString().takeLast(10)
        val dbName = "g5b-json-$tag.db"
        val dbFile = File(System.getProperty("user.home") ?: ".", ".local/share/baki/$dbName")
        dbFile.delete()
        val store = Store(entries = listOf(Entry(id = "json-direct", date = "2026-11-01", amount = 99, updated = "a")))
        var direct: NormalizedSqliteStorage? = null
        try {
            saveLocal(store, markDirty = false)
            direct = NormalizedSqliteStorage(dbName, "g5b-json-key")
            val imported = direct.loadWithOperations(OperationLog("g5b-json-$tag.log", "g5b-json-$tag.meta"))
            assertEquals(store, imported)
        } finally {
            direct?.close()
            saveLocal(previous, markDirty = false)
            dbFile.delete()
        }
    }

    @Test
    fun importsCoreTablesAndMigratesTheG5aSnapshot() {
        val previous = loadLocal()
        val tag = Random.nextLong().toString().takeLast(10)
        val dbName = "g5b-test-$tag.db"
        val dbFile = File(System.getProperty("user.home") ?: ".", ".local/share/baki/$dbName")
        dbFile.delete()
        val key = "g5b-test-key"
        val store = Store(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", type = "expense", cat = "food", amount = 123, updated = "a"),
                Entry(id = "e2", date = "2026-11-02", type = "expense", cat = "fuel", amount = 456, odo = 42_000, updated = "b"),
            ),
            shifts = listOf(Shift(id = "s1", date = "2026-11-01", start = "09:00", hours = 2.0, km = 20.0, gross = 5_000, updated = "c")),
            bills = listOf(Bill(id = "b1", name = "Phone", amount = 3_000, category = "phone", updated = "d")),
            billPayments = listOf(BillPayment("b1", "2026-11", 3_000, "e1", "2026-11-01", "e")),
            weekCloses = mapOf("2026-10-26" to WeekClose("2026-10-26", 5_000, "2026-11-01", "f")),
            reserveTxns = listOf(ReserveTxn("r1", "fuel", 1_000, "2026-11-01", updated = "g")),
            odometerReadings = listOf(OdometerReading("o1", "2026-11-01", 42_000, "fill", "h")),
            settings = Store().settings.copy(nextServiceKm = 50_000),
            tomb = Tomb(ids = listOf("dead"), cats = listOf("old-cat")),
        )
        var normalized: NormalizedSqliteStorage? = null
        try {
            saveLocal(store, markDirty = false)
            // Seed a G5a snapshot, then prove the normalized storage imports from it.
            SqliteLedgerStorage(dbName, key).save(store, markDirty = false, recordOutbox = false)
            val storage = NormalizedSqliteStorage(dbName, key)
            normalized = storage
            val imported = storage.loadWithOperations(OperationLog("g5b-empty-$tag.log", "g5b-empty-$tag.meta"))
            assertEquals(store, imported, "normalized import must preserve the whole Store")
            val exported = storage.exportJson()!!
            assertEquals(store, BakiJson.decodeFromString<Store>(exported), "SQLite export must reconstruct the Store")

            val next = store.copy(entries = store.entries + Entry(id = "e3", date = "2026-11-03", amount = 789, updated = "i"))
            val log = OperationLog("g5b-$tag.log", "g5b-$tag.meta")
            StoreWrite.operationLog = log
            val started = System.nanoTime()
            storage.save(next, markDirty = true, recordOutbox = true)
            println("[g5b] single entry save: ${(System.nanoTime() - started) / 1_000_000} ms")
            assertEquals(1, log.pendingCount())
            assertEquals(next, storage.loadWithOperations(log))

            val updated = next.copy(entries = next.entries.map {
                if (it.id == "e1") it.copy(amount = 999, note = "changed", updated = "z") else it
            })
            storage.save(updated, markDirty = true, recordOutbox = false)
            assertEquals(updated, storage.loadWithOperations(log))

            val deleted = updated.copy(entries = updated.entries.filterNot { it.id == "e2" })
            storage.save(deleted, markDirty = true, recordOutbox = false)
            assertEquals(deleted, storage.loadWithOperations(log))
            log.acknowledgeAll()

            Class.forName("org.sqlite.JDBC")
            val props = org.sqlite.mc.SQLiteMCSqlCipherConfig.getV4Defaults().withKey(key).build().toProperties()
            java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}", props).use { conn ->
                fun count(table: String): Int = conn.createStatement().use { st ->
                    st.executeQuery("SELECT count(*) FROM $table").use { rs -> rs.next(); rs.getInt(1) }
                }
                assertEquals(2, count("entries"))
                assertEquals(1, count("shifts"))
                assertEquals(1, count("bills"))
                assertEquals(1, count("bill_payments"))
                assertEquals(1, count("week_closes"))
                assertEquals(1, count("reserve_txns"))
                assertEquals(1, count("odometer_readings"))
                assertTrue(count("tombstones") >= 2)
            }
        } finally {
            StoreWrite.operationLog = null
            normalized?.close()
            saveLocal(previous, markDirty = false)
            dbFile.delete()
        }
    }
}
