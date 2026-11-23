package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecoverySnapshotTest {
    @Test
    fun snapshotIsVerifiedAndReloadable() {
        val previous = loadLocal()
        val store = Store(
            entries = listOf(Entry(id = "recovery-entry", date = "2026-11-23", amount = 123, updated = "a")),
            shifts = listOf(Shift(id = "recovery-shift", date = "2026-11-23", start = "09:00", hours = 1.0, km = 10.0, gross = 1000, updated = "a")),
        )
        try {
            assertTrue(RecoverySnapshot.save(store))
            val loaded = RecoverySnapshot.load()
            assertNotNull(loaded)
            assertEquals(store, loaded.first)
            assertTrue(RecoverySnapshot.lastSavedAt != null)
        } finally {
            LocalStore.deleteBytes("recovery-snapshot.json")
            LocalStore.deleteBytes("recovery-snapshot.prev.json")
            saveLocal(previous, markDirty = false)
        }
    }
}
