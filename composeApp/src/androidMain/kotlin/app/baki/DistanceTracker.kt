package app.baki

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance from GPS, tuned to cost almost nothing:
 *  - the radio wakes on movement only: minTime 4s, minDistance 25m
 *  - a fix is accepted only if it is accurate (<= 40m) and plausible (<= 150 km/h)
 *  - micro-jitter under 8m is ignored, so standing still does not accumulate metres
 * The CPU work is a handful of haversines per minute — the cost is the GPS radio, not the math.
 */
object DistanceTracker {

    private const val MIN_ACCURACY_M = 40f
    private const val MIN_MOVE_M = 8f
    private const val MAX_SPEED_MPS = 42.0     // 150 km/h: anything faster is a jump
    private const val MIN_INTERVAL_MS = 4_000L
    private const val MIN_DISTANCE_M = 25f

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    private var last: Location? = null
    private var metres: Double = 0.0
    private var onUpdate: ((Double) -> Unit)? = null

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context, track: DistanceCallback) {
        if (!hasPermission(ctx)) return
        stop()

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        manager = lm
        last = null
        metres = (loadLive()?.km ?: 0.0) * 1000.0   // resume after a service restart

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
            else -> null
        }
        if (provider == null) return

        runCatching {
            lm.requestLocationUpdates(provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, l, ctx.mainLooper)
        }
    }

    fun stop() {
        val lm = manager
        val l = listener
        if (lm != null && l != null) runCatching { lm.removeUpdates(l) }
        manager = null
        listener = null
    }

    private fun accept(loc: Location) {
        if (loc.accuracy > MIN_ACCURACY_M) return
        val prev = last
        if (prev == null) { last = loc; return }

        val step = distanceMetres(prev, loc)
        val seconds = (loc.time - prev.time) / 1000.0
        val tooFast = seconds > 0 && (step / seconds) > MAX_SPEED_MPS
        if (tooFast) return                     // a jump, not travel

        if (step >= MIN_MOVE_M) {
            metres += step
            last = loc
        }
        // persist straight into the running shift
        val live = loadLive()
        if (live != null) {
            val km = metres / 1000.0
            if (abs(km - live.km) >= 0.01) {
                saveLive(live.copy(km = km))
                onUpdate?.invoke(km)
            }
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
