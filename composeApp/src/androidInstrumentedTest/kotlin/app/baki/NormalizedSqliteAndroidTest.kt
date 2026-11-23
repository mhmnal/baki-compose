package app.baki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NormalizedSqliteAndroidTest {
    @Test
    fun importsProductionSizedJsonOnAndroid() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        AppCtx.attach(ctx)
        ctx.deleteDatabase("g5-android.db")
        val entries = (1..131).map { i ->
            Entry(
                id = "android-entry-$i", date = "2026-11-${(i % 22 + 1).toString().padStart(2, '0')}",
                type = if (i % 9 == 0) "income" else "expense", cat = if (i % 3 == 0) "fuel" else "food",
                amount = (100 + i).toLong(), updated = "2026-11-23T21:00:00",
            )
        }
        val store = Store(entries = entries)
        saveLocal(store, markDirty = false)
        val storage = NormalizedSqliteStorage("g5-android.db", "g5-android-key")
        val loaded = storage.loadWithOperations(OperationLog(fixedOperationLogName(), "operations.meta"))
        assertEquals(131, loaded.entries.size)
        assertEquals(store, loaded)
        val next = loaded.copy(entries = loaded.entries + Entry(id = "bench", date = "2026-11-23", amount = 1, updated = "z"))
        val started = System.nanoTime()
        storage.save(next, markDirty = false, recordOutbox = false)
        println("[g5-android] single entry save: ${(System.nanoTime() - started) / 1_000_000} ms")
        assertEquals(132, storage.loadWithOperations(OperationLog(fixedOperationLogName(), "operations.meta")).entries.size)
        val exported = BakiJson.decodeFromString<Store>(storage.exportJson()!!)
        assertEquals(132, exported.entries.size)
        LocalStore.deleteBytes("operations.log")
        ctx.deleteDatabase("g5-android.db")
    }

    private fun fixedOperationLogName(): String = "operations.log"
}
