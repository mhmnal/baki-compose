package app.baki

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One shift where the driver's own number disagreed with the track. */
@Serializable
data class CalibrationSample(val rawKm: Double, val realKm: Double, val at: String)

@Serializable
data class CalibrationFile(
    /** raw GPS km recorded when a shift ended, so a later correction can teach the ratio */
    val rawByShift: Map<String, Double> = emptyMap(),
    val samples: List<CalibrationSample> = emptyList(),
)

/**
 * Learns how far the track drifts from the car's own measurement, so the driver never has to key in
 * an odometer before and after every shift. Any time a finished shift's km is corrected to the
 * number the car reports, that pair becomes evidence; the median ratio of recent evidence becomes
 * the correction applied to future shifts.
 *
 * Local only: it is a property of this phone and this car, not of the ledger.
 */
object Calibration {
    private const val FILE = "calibration.json"
    private const val MIN_RAW_KM = 3.0      // shorter than this teaches nothing
    private const val MIN_RATIO = 0.7       // outside these the pair is a typo, not drift
    private const val MAX_RATIO = 2.0
    private const val MIN_FACTOR = 0.8      // what we will apply to future tracking
    private const val MAX_FACTOR = 1.8
    private const val WINDOW = 5            // recent samples win; a car changes over time

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var cache: CalibrationFile? = null

    private fun load(): CalibrationFile {
        cache?.let { return it }
        val raw = try {
            LocalStore.readText(FILE)
        } catch (e: Exception) {
            runCatching { LocalStore.quarantine(FILE) }
            StoreHealth.loadError = "Odometer calibration could not be opened (${e.message ?: "decrypt failed"})."
            null
        }
        val file = raw?.let {
            runCatching { json.decodeFromString<CalibrationFile>(it) }.getOrNull()
        } ?: CalibrationFile()
        cache = file
        return file
    }

    private fun write(file: CalibrationFile) {
        cache = file
        LocalStore.writeText(FILE, json.encodeToString(CalibrationFile.serializer(), file))
    }

    /** Called when a shift ends: remember what the tracker measured before any correction. */
    suspend fun rememberRaw(shiftId: String, rawKm: Double) = mutex.withLock {
        if (shiftId.isBlank() || rawKm < MIN_RAW_KM) return@withLock
        val file = load()
        // keep the map from growing forever
        val trimmed = (file.rawByShift + (shiftId to rawKm)).entries.toList().takeLast(40)
            .associate { it.key to it.value }
        write(file.copy(rawByShift = trimmed))
    }

    /**
     * Called when the driver edits a shift's km to the car's figure. Records the pair and returns
     * the correction to apply from now on, or null when there is not enough evidence yet.
     */
    suspend fun learn(shiftId: String, realKm: Double): Double? = mutex.withLock {
        val file = load()
        val raw = file.rawByShift[shiftId] ?: return@withLock null
        if (raw < MIN_RAW_KM || realKm <= 0.0) return@withLock null
        val ratio = realKm / raw
        if (ratio < MIN_RATIO || ratio > MAX_RATIO) return@withLock null

        val samples = (file.samples + CalibrationSample(raw, realKm, nowIso())).takeLast(20)
        // once learned, the raw entry has done its job
        write(file.copy(samples = samples, rawByShift = file.rawByShift - shiftId))
        suggestedFactor(samples)
    }

    /** Median of the most recent ratios, clamped: one odd shift cannot swing the whole model. */
    fun suggestedFactor(samples: List<CalibrationSample>): Double? {
        if (samples.size < 2) return null
        val ratios = samples.takeLast(WINDOW).map { it.realKm / it.rawKm }.sorted()
        val median = if (ratios.size % 2 == 1) {
            ratios[ratios.size / 2]
        } else {
            (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2.0
        }
        return median.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    suspend fun sampleCount(): Int = mutex.withLock { load().samples.size }
}
