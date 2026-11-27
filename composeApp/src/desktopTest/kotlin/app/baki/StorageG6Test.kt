package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

class StorageG6Test {
    @Test
    fun legacyJsonConfigMigratesToSqlite() {
        assertEquals("sqlite", migrateStorageEngine(ClientConfig(storageEngine = "json")).storageEngine)
        assertEquals("sqlite", migrateStorageEngine(ClientConfig()).storageEngine)
    }
}
