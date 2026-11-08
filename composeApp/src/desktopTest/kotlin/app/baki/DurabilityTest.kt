package app.baki

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the storage/sync durability invariants that are easy to regress silently. */
class DurabilityTest {

    private fun source(path: String): String {
        var dir = File(System.getProperty("user.dir"))
        while (true) {
            val candidate = File(dir, "src/$path")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile ?: error("cannot locate sources")
        }
    }

    private fun repo() = source("commonMain/kotlin/app/baki/Repo.kt")
    private fun storage() = source("commonMain/kotlin/app/baki/Storage.kt")

    @Test
    fun theOutboxBitIsPersistedInTheDeviceConfig() {
        assertEquals(true, ClientConfig().pendingPush)
        val encoded = BakiJson.encodeToString(ClientConfig.serializer(), ClientConfig(pendingPush = false))
        assertTrue(encoded.contains("\"pendingPush\":false"))
        val r = repo()
        assertTrue(r.contains("get() = config.pendingPush"), "pendingPush must read from persisted config")
        assertTrue(r.contains("StoreWrite.onLocalSave = { markDirty() }"), "local saves must mark the outbox")
    }

    @Test
    fun everyPushCarriesEveryTombstone() {
        val r = repo()
        assertTrue(r.contains("(extraIds + store.tomb.ids).distinct()"), "all local tombstones must travel")
        assertEquals(1, Regex("client\\.push\\(store").findAll(r).count(), "pushes must go through one helper")
    }

    @Test
    fun backgroundSyncDoesNotFlipTheHeader() {
        val r = repo()
        assertTrue(r.contains("sync(showProgress = false)"), "background syncs must stay quiet")
        assertTrue(r.contains("if (showProgress) status = \"syncing\""), "only manual syncs show syncing")
    }

    @Test
    fun theRetryLoopPushesFullHistoryWithTombstones() {
        val r = repo()
        assertTrue(r.contains("private suspend fun pushAll()"), "a single retry-push path is required")
        assertTrue(r.contains("deletedIds = deleted"), "deletions must travel with the retry push")
        // both sync branches (304 and fresh data) go through pushAll, not an ad-hoc push
        assertTrue(r.split("pushAll()").size - 1 >= 3, "sync branches do not both use pushAll")
    }

    @Test
    fun anUnreadableStoreIsQuarantinedAndRecoveredFromBackupOrServer() {
        val s = storage()
        assertTrue(s.contains("quarantine(\"store.json\")"), "damaged store must be kept, not overwritten")
        assertTrue(s.contains("store.json.bak"), "the previous local write must be tried")
        assertTrue(s.contains("StoreHealth.loadError"), "the failure must be surfaced")
        assertTrue(s.contains("store.json\")"), "the store file name must be explicit")
    }

    @Test
    fun localWritesAreDurable() {
        val m = source("desktopMain/kotlin/app/baki/Storage.desktop.kt")
        assertTrue(m.contains("fd.sync()"), "the temp file must be flushed before rename")
        assertTrue(m.contains(".bak"), "a previous local copy must be kept")
        assertTrue(m.contains("looksSealed"), "a corrupt store must not rotate over a good backup")
        assertTrue(m.contains("actual fun quarantine"), "quarantine must be platform-implemented")
    }
}
