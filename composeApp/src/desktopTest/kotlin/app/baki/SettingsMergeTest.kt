package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsMergeTest {
    @Test fun pendingLocalSettingsBeatAStalePull() {
        val local = Store(settings = Settings(dailyFoodCapCents = 2_500, nextServiceKm = 90_000))
        val remote = Store(settings = Settings(dailyFoodCapCents = null, nextServiceKm = 80_000))
        val merged = mergeStores(local, remote, keepLocalSettings = true)
        assertEquals(2_500L, merged.settings.dailyFoodCapCents)
        assertEquals(90_000, merged.settings.nextServiceKm)
    }

    @Test fun cleanMergeStillTakesRemoteSettings() {
        val local = Store(settings = Settings(dailyFoodCapCents = 2_500))
        val remote = Store(settings = Settings(dailyFoodCapCents = 3_000))
        val merged = mergeStores(local, remote)
        assertEquals(3_000L, merged.settings.dailyFoodCapCents)
    }
}
