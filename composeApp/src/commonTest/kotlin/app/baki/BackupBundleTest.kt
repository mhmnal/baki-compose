package app.baki

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupBundleTest {
    @Test
    fun bundleCarriesThePartsAStoreOnlyBackupMissed() {
        val bundle = BakiBackupBundle(
            exportedAt = "2026-11-15T00:00:00Z",
            store = Store(),
            config = ClientConfig(serverUrl = "http://192.168.1.2:8787", reducedMotion = true),
            calibration = CalibrationFile(rawByShift = mapOf("s1" to 97.5)),
            routes = listOf(
                ShiftRoute(
                    shiftId = "s1",
                    date = "2026-11-15",
                    points = listOf(RoutePoint(1.0, 2.0, 3L), RoutePoint(1.1, 2.1, 4L)),
                ),
            ),
        )
        val encoded = Json { encodeDefaults = true }.encodeToString(BakiBackupBundle.serializer(), bundle)
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(BakiBackupBundle.serializer(), encoded)
        assertEquals("baki-backup-v2", decoded.format)
        assertEquals("http://192.168.1.2:8787", decoded.config?.serverUrl)
        assertEquals(97.5, decoded.calibration?.rawByShift?.get("s1"))
        assertEquals(1, decoded.routes.size)
        assertTrue(decoded.store.entries.isEmpty())
    }
}
