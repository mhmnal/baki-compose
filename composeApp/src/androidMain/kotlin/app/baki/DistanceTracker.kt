package app.baki

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance from location fixes.
 *
 * Prefers the **fused** provider (GPS + Wi-Fi + cell + sensors, smoothed by Play Services) because
 * raw GPS alone loses fixes in a city: every rejected fix becomes one long straight line across the
 * streets you actually drove. Falls back to LocationManager where Play Services is absent.
 *
 * Sampling is deliberately dense (≈1–2 s, no distance filter) since the phone is charging in the
 * car; the filters below do the noise rejection instead.
 */
object DistanceTracker {

    private const val MAX_ACCURACY_M = 65f      // was 40: rejecting city fixes cost more than noise
    private const val MIN_MOVE_M = 3f           // below this it is jitter, not travel
    private const val MAX_SPEED_MPS = 55.0      // ~198 km/h: anything faster is a teleport

    private var fused: Any? = null
    private var callback: Any? = null
    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    private var last: Location? = null
    private var metres: Double = 0.0
    private var lastPersistMs: Long = 0
    private var lastPersistedKm: Double = -1.0

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context, unused: DistanceCallback? = null) {
        if (!hasPermission(ctx)) return
        stop()

        // resume after a service restart instead of starting from zero
        metres = (loadLive()?.km ?: 0.0) * 1000.0
        last = null

        val started = runCatching {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { accept(it) }
                }
            }
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
            fused = client
            callback = cb
            true
        }.getOrDefault(false)

        if (!started) startLegacy(ctx)
    }

    private fun startLegacy(ctx: Context) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        manager = lm
        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) = accept(loc)
            @Deprecated("required by the interface")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = l
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        runCatching { lm.requestLocationUpdates(provider, 1_000L, 0f, l, ctx.mainLooper) }
    }

    fun stop() {
        (fused as? com.google.android.gms.location.FusedLocationProviderClient)?.let {
            (callback as? LocationCallback)?.let { cb -> runCatching { it.removeLocationUpdates(cb) } }
        }
        fused = null; callback = null
        val lm = manager
        val l = listener
        if (lm != null && l != null) runCatching { lm.removeUpdates(l) }
        manager = null; listener = null
    }

    private fun accept(loc: Location) {
        if (loc.accuracy > MAX_ACCURACY_M) return
        val prev = last
        if (prev == null) { last = loc; return }

        val step = distanceMetres(prev, loc)
        val seconds = (loc.time - prev.time) / 1000.0
        if (seconds > 0 && step / seconds > MAX_SPEED_MPS) return   // a jump, not travel

        if (step >= MIN_MOVE_M) {
            metres += step
            last = loc
        }

        // Coalesce disk writes: each one is a JSON encode + AES-GCM encrypt + file write.
        // 25 m of travel or 3 s, whichever comes first, is plenty for a live display.
        val live = loadLive() ?: return
        val km = metres / 1000.0
        val now = System.currentTimeMillis()
        val farEnough = abs(km - lastPersistedKm) >= 0.025
        if (farEnough && now - lastPersistMs >= 3_000) {
            lastPersistMs = now
            lastPersistedKm = km
            saveLive(live.copy(km = km))
        }
    }

    private fun distanceMetres(a: Location, b: Location): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}

fun interface DistanceCallback { fun onDistance(km: Double) }
