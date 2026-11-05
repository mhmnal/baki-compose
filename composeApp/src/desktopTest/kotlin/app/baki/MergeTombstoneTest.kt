package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A record deleted on the server must never be resurrected by a stale local copy. */
class MergeTombstoneTest {

    private val record = Entry(id = "gone", date = "2026-11-05", type = "expense", cat = "food", amount = 1200, updated = "2030-01-01T00:00:00Z")

    @Test
    fun aTombstonedRecordIsDropped() {
        val local = Store(entries = listOf(record))
        val remote = Store(entries = emptyList(), tomb = Tomb(ids = listOf("gone")))
        val merged = mergeStores(local, remote)
        assertTrue(merged.entries.isEmpty(), "stale local copy must not survive the tombstone")
        assertTrue(merged.tomb.ids.contains("gone"), "the tombstone is remembered")
    }

    @Test
    fun tombstonesSurviveLaterMerges() {
        val once = mergeStores(Store(entries = listOf(record)), Store(tomb = Tomb(ids = listOf("gone"))))
        val twice = mergeStores(once, Store())
        assertTrue(twice.entries.isEmpty())
        assertTrue(twice.tomb.ids.contains("gone"))
    }

    @Test
    fun deletedCategoriesDontComeBack() {
        val cat = Cat("testcat", "Test", "🏷️")
        val local = Store(settings = Settings(cats = Cats(catsOut = listOf(cat))))
        val remote = Store(tomb = Tomb(cats = listOf("testcat")))
        assertEquals(0, mergeStores(local, remote).settings.cats.catsOut.size)
    }
}
