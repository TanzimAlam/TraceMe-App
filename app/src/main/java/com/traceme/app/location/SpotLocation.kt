package com.traceme.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.traceme.app.util.Perms
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-off location lookup for screens that are not running the night watch
 * (Nearby Help, the route map). Asks for a fresh high-accuracy fix and falls
 * back to the last known one, so the app never blocks on "waiting for GPS".
 */
object SpotLocation {

    data class Result(val location: Location?, val locationOff: Boolean = false)

    fun servicesEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, maxWaitMillis: Long = 15_000L): Result {
        if (!Perms.hasLocation(context)) return Result(null, false)
        if (!servicesEnabled(context)) return Result(null, true)

        val client = try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            return Result(null, false)
        }

        val fresh: Location? = withTimeoutOrNull(maxWaitMillis) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val token = CancellationTokenSource()
                continuation.invokeOnCancellation { runCatching { token.cancel() } }
                try {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                        .addOnSuccessListener { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume(null)
                        }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        if (fresh != null) return Result(fresh)

        val last: Location? = withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                try {
                    client.lastLocation
                        .addOnSuccessListener { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume(null)
                        }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        return Result(last)
    }
}
