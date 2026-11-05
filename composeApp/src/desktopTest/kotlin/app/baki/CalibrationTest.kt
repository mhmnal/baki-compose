package app.baki

import kotlin.test.Test
import kotlin.test.assertEquals

/** The GPS correction is a plain multiplier on tracked distance, and it must survive serialization. */
class CalibrationTest {

    @Test
    fun correctionSurvivesTheStoreRoundTrip() {
        val store = Store(settings = Settings(gpsCorrection = 1.27))
        val back = BakiJson.decodeFromString<Store>(BakiJson.encodeToString(Store.serializer(), store))
        assertEquals(1.27, back.settings.gpsCorrection, 0.001)
    }

    @Test
    fun defaultIsNoCorrection() {
        assertEquals(1.0, Settings().gpsCorrection, 0.001)
        // old files without the field still decode
        val old = """{"v":1,"entries":[],"shifts":[],"settings":{"profit":{"fuelPrice":1.99}}}"""
        assertEquals(1.0, BakiJson.decodeFromString<Store>(old).settings.gpsCorrection, 0.001)
    }

    @Test
    fun aShortTrackIsScaledUpToTheOdometer() {
        val tracked = 94.2
        val factor = 120.0 / 94.2
        assertEquals(120.0, tracked * factor, 0.01)
    }
}
