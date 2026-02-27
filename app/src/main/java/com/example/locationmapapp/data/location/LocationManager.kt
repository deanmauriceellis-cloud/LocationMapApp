package com.example.locationmapapp.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.locationmapapp.core.AppException
import com.example.locationmapapp.util.DebugLogger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationManager — wraps the GMS FusedLocationProviderClient and exposes
 * a cold Kotlin Flow of [Location] updates.
 *
 * ## v1.4 BUG FIX — SecurityException crash
 *
 * Root cause (crash log 2026-02-19 13:33:37):
 *   The GMS [addOnFailureListener] callback closed the Channel with the
 *   [AppException.LocationException] as the cause.  The coroutine machinery
 *   propagated that exception through [emitAllImpl] onto [Dispatchers.Main],
 *   producing a FATAL EXCEPTION on the main thread before the permission
 *   dialog could even appear.
 *
 * Fix: [addOnFailureListener] now logs the error and calls [channel.close()]
 * with **no exception payload**.  A clean close causes the flow to complete
 * normally with 0 emissions; the caller ([MainViewModel.startLocationUpdates])
 * handles zero updates gracefully (map stays at last known / default centre).
 */
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val TAG = "LocationManager"

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns a cold [Flow] that emits [Location] updates from the
     * FusedLocationProvider.
     *
     * The flow is active for as long as it is collected; it calls
     * [FusedLocationProviderClient.removeLocationUpdates] when cancelled.
     *
     * **Pre-condition:** Location permission must be granted before collecting
     * this flow.  If it is not, the flow will log the error and complete
     * cleanly with 0 emissions rather than crashing the app.
     *
     * @param intervalMs   Desired update interval in ms (default 5 000).
     * @param minIntervalMs Fastest acceptable update interval (default 2 000).
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(
        intervalMs: Long = 5_000L,
        minIntervalMs: Long = 2_000L
    ): Flow<Location> = callbackFlow {

        DebugLogger.i(TAG, "getLocationUpdates() — building LocationRequest " +
                "interval=${intervalMs}ms minInterval=${minIntervalMs}ms")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .build()

        var updateCount = 0

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    updateCount++
                    DebugLogger.d(TAG, "onLocationResult #$updateCount — " +
                            "lat=${loc.latitude} lon=${loc.longitude} " +
                            "acc=${loc.accuracy}m provider=${loc.provider}")
                    trySend(loc)
                }
            }
        }

        DebugLogger.i(TAG, "Calling requestLocationUpdates on main looper")

        fusedClient
            .requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnSuccessListener {
                DebugLogger.i(TAG, "requestLocationUpdates registered successfully")
            }
            .addOnFailureListener { e ->
                // ─────────────────────────────────────────────────────────────
                // BUG FIX v1.4: DO NOT close the channel with the exception.
                //
                // Previous code:
                //   channel.close(AppException.LocationException(..., e))
                //
                // That closed the channel with a non-null cause, which caused
                // the coroutine machinery to re-surface the SecurityException
                // on Dispatchers.Main as an uncaught fatal exception.
                //
                // Correct behaviour: log the error, close cleanly.
                // The flow completes with 0 emissions; callers handle that.
                // ─────────────────────────────────────────────────────────────
                DebugLogger.e(
                    TAG,
                    "requestLocationUpdates FAILED to register — " +
                    "${e.javaClass.simpleName}: ${e.message}. " +
                    "Is Google Play Services available and up-to-date?\n" +
                    e.stackTraceToString()
                )
                channel.close()   // ← clean close, no exception payload
            }

        awaitClose {
            DebugLogger.i(TAG, "Flow cancelled — removing location updates " +
                    "after $updateCount updates received")
            fusedClient.removeLocationUpdates(callback)
        }
    }

    // =========================================================================
    // Last-known location (instant, no callback latency)
    // =========================================================================

    /**
     * Returns the most recent cached [Location] from any provider, or null
     * if nothing is cached.  Uses the best (most accurate) fix across all
     * providers.
     *
     * Intended to pre-centre the map immediately at cold start before the
     * first GMS periodic update arrives.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(onResult: (Location?) -> Unit) {
        DebugLogger.i(TAG, "getLastKnownLocation() — requesting from FusedClient")
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    DebugLogger.i(TAG, "lastLocation: lat=${loc.latitude} " +
                            "lon=${loc.longitude} acc=${loc.accuracy}m " +
                            "age=${(System.currentTimeMillis() - loc.time) / 1_000}s")
                } else {
                    DebugLogger.w(TAG, "lastLocation: null (no cached fix)")
                }
                onResult(loc)
            }
            .addOnFailureListener { e ->
                DebugLogger.e(TAG, "lastLocation FAILED: ${e.message}")
                onResult(null)
            }
    }
}
