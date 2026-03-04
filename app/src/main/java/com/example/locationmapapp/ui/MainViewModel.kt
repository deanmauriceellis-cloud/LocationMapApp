/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.location.LocationManager
import com.example.locationmapapp.data.model.*
import com.example.locationmapapp.data.repository.PlacesRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainViewModel.kt"

/** Rich GPS update — carries speed/bearing/accuracy alongside the map point. */
data class LocationUpdate(
    val point: GeoPoint,
    val speedMps: Float?,
    val bearing: Float?,
    val accuracy: Float
)

/**
 * MainViewModel — owns all UI state for MainActivity.
 *
 * v1.4 Location contract:
 *   GPS updates start ONLY after Activity confirms permission via onPermissionGranted().
 *   NO GPS call in init{} — that caused FATAL SecurityException on fresh installs.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val locationManager: LocationManager,
    private val placesRepository: PlacesRepository
) : ViewModel() {

    private val TAG = "ViewModel"

    private val _currentLocation = MutableLiveData<LocationUpdate>()
    val currentLocation: LiveData<LocationUpdate> = _currentLocation

    private val _locationMode = MutableLiveData<LocationMode>(LocationMode.GPS)
    val locationMode: LiveData<LocationMode> = _locationMode

    private var locationJob: Job? = null

    private val _places = MutableLiveData<Pair<String, List<PlaceResult>>>()
    val places: LiveData<Pair<String, List<PlaceResult>>> = _places


    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    init {
        // Pure state init only — NO GPS, NO network calls.
        // GPS starts only after Activity grants permission via onPermissionGranted().
        DebugLogger.i(TAG, "ViewModel created — waiting for permission before starting GPS")
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private var currentIntervalMs: Long = 60_000L

    /** Called by MainActivity after location permission is confirmed. Safe to call multiple times. */
    fun onPermissionGranted() {
        if (locationJob?.isActive == true) {
            DebugLogger.w(TAG, "onPermissionGranted() — location already running, skip")
            return
        }
        DebugLogger.i(TAG, "onPermissionGranted() — starting GMS location flow")
        _locationMode.value = LocationMode.GPS
        currentIntervalMs = 60_000L
        locationJob = viewModelScope.launch {
            locationManager.getLocationUpdates(intervalMs = 60_000L, minIntervalMs = 30_000L)
                .collect { loc ->
                    DebugLogger.d(TAG, "GPS update: lat=${loc.latitude} lon=${loc.longitude} acc=${loc.accuracy}m spd=${loc.speed}m/s")
                    if (_locationMode.value == LocationMode.GPS) {
                        _currentLocation.value = LocationUpdate(
                            point = GeoPoint(loc.latitude, loc.longitude),
                            speedMps = if (loc.hasSpeed()) loc.speed else null,
                            bearing = if (loc.hasBearing()) loc.bearing else null,
                            accuracy = loc.accuracy
                        )
                    }
                }
            DebugLogger.w(TAG, "GMS location flow ended — 0 updates (permission denied or GMS error)")
        }
    }

    /** Restart GPS flow with a new interval. No-op if interval is unchanged. */
    fun restartLocationUpdates(intervalMs: Long, minIntervalMs: Long) {
        if (intervalMs == currentIntervalMs) return
        DebugLogger.i(TAG, "restartLocationUpdates() — interval ${currentIntervalMs}ms → ${intervalMs}ms")
        currentIntervalMs = intervalMs
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationManager.getLocationUpdates(intervalMs = intervalMs, minIntervalMs = minIntervalMs)
                .collect { loc ->
                    DebugLogger.d(TAG, "GPS update: lat=${loc.latitude} lon=${loc.longitude} acc=${loc.accuracy}m spd=${loc.speed}m/s")
                    if (_locationMode.value == LocationMode.GPS) {
                        _currentLocation.value = LocationUpdate(
                            point = GeoPoint(loc.latitude, loc.longitude),
                            speedMps = if (loc.hasSpeed()) loc.speed else null,
                            bearing = if (loc.hasBearing()) loc.bearing else null,
                            accuracy = loc.accuracy
                        )
                    }
                }
            DebugLogger.w(TAG, "GMS location flow ended — 0 updates (permission denied or GMS error)")
        }
    }

    /** Legacy call-site compatibility shim. Delegates to onPermissionGranted(). */
    fun startLocationUpdates() = onPermissionGranted()

    fun onGpsLocationUpdate(location: Location) {
        if (_locationMode.value == LocationMode.GPS) {
            _currentLocation.value = LocationUpdate(
                point = GeoPoint(location.latitude, location.longitude),
                speedMps = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                accuracy = location.accuracy
            )
        }
    }

    fun setManualLocation(point: GeoPoint) {
        _currentLocation.value = LocationUpdate(point, null, null, 0f)
        _locationMode.value = LocationMode.MANUAL
        DebugLogger.i(TAG, "Manual location: ${point.latitude}, ${point.longitude}")
    }

    /** Fires a one-shot last-known-location query to centre the map before the first periodic update. */
    fun requestLastKnownLocation() {
        DebugLogger.i(TAG, "requestLastKnownLocation() — asking FusedClient for cached fix")
        locationManager.getLastKnownLocation { loc ->
            if (loc != null && _locationMode.value == LocationMode.GPS) {
                DebugLogger.i(TAG, "lastKnownLocation: lat=${loc.latitude} lon=${loc.longitude} — centering map now")
                _currentLocation.value = LocationUpdate(
                    point = GeoPoint(loc.latitude, loc.longitude),
                    speedMps = null,
                    bearing = null,
                    accuracy = loc.accuracy
                )
            } else {
                DebugLogger.w(TAG, "lastKnownLocation: null — map stays at default until first GPS fix")
            }
        }
    }

    fun toggleLocationMode() {
        _locationMode.value = if (_locationMode.value == LocationMode.GPS)
            LocationMode.MANUAL else LocationMode.GPS
        DebugLogger.i(TAG, "Location mode → ${_locationMode.value}")
    }

    // ── Data loads — user-initiated only, never called from init ──────────────

    fun searchPoisAt(point: GeoPoint, categories: List<String> = emptyList(), layerId: String = "default") {
        DebugLogger.i(TAG, "searchPoisAt() lat=${point.latitude} lon=${point.longitude} layerId=$layerId categories=$categories")
        viewModelScope.launch {
            runCatching { placesRepository.searchPois(point, categories) }
                .onSuccess { DebugLogger.i(TAG, "POI success — ${it.size} layerId=$layerId"); _places.value = layerId to it }
                .onFailure { e -> DebugLogger.e(TAG, "POI FAILED: ${e.message}", e as? Exception); _error.value = "POI failed: ${e.message}" }
        }
    }

    fun searchPoisFromCache(point: GeoPoint) {
        viewModelScope.launch {
            runCatching { placesRepository.searchPoisCacheOnly(point) }
                .onSuccess { if (it.isNotEmpty()) { DebugLogger.i(TAG, "Cache POI hit — ${it.size}"); _places.value = "cache" to it } }
                .onFailure { /* silent — cache miss is expected */ }
        }
    }

    /** Load cached POIs within a bounding box from the proxy's poi-cache. */
    fun loadCachedPoisForBbox(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadCachedPoisForBbox() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { placesRepository.fetchCachedPoisInBbox(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Cached POIs in bbox — ${it.size}"); _places.value = "bbox" to it }
                .onFailure { e -> DebugLogger.e(TAG, "Cached POIs bbox FAILED: ${e.message}", e as? Exception) }
        }
    }

    /** Direct suspend call for populate scanner — returns result without LiveData.
     *  @param radiusOverride if non-null, overrides the radius hint (used for cap-retry) */
    suspend fun populateSearchAt(point: GeoPoint, categories: List<String> = emptyList(), radiusOverride: Int? = null): com.example.locationmapapp.data.model.PopulateSearchResult? {
        return try {
            placesRepository.searchPoisForPopulate(point, categories, radiusOverride)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "populateSearchAt FAILED: ${e.message}", e)
            null
        }
    }

    /** Cancel all queued Overpass requests at the proxy. Call on follow/move/stop. */
    fun cancelPendingOverpass() {
        viewModelScope.launch(Dispatchers.IO) {
            placesRepository.cancelPendingOverpass()
        }
    }

}

enum class LocationMode { GPS, MANUAL }
