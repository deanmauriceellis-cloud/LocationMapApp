package com.example.locationmapapp.ui

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.MbtaVehicle
import com.example.locationmapapp.data.repository.MbtaRepository
import com.example.locationmapapp.data.location.LocationManager
import com.example.locationmapapp.data.model.*
import com.example.locationmapapp.data.repository.AircraftRepository
import com.example.locationmapapp.data.repository.PlacesRepository
import com.example.locationmapapp.data.repository.WebcamRepository
import com.example.locationmapapp.data.repository.WeatherRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

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
    private val placesRepository: PlacesRepository,
    private val weatherRepository: WeatherRepository,
    private val mbtaRepository: MbtaRepository,
    private val aircraftRepository: AircraftRepository,
    private val webcamRepository: WebcamRepository
) : ViewModel() {

    private val TAG = "ViewModel"

    private val _currentLocation = MutableLiveData<GeoPoint>()
    val currentLocation: LiveData<GeoPoint> = _currentLocation

    private val _locationMode = MutableLiveData<LocationMode>(LocationMode.GPS)
    val locationMode: LiveData<LocationMode> = _locationMode

    private var locationJob: Job? = null

    private val _places = MutableLiveData<Pair<String, List<PlaceResult>>>()
    val places: LiveData<Pair<String, List<PlaceResult>>> = _places

    private val _weatherAlerts = MutableLiveData<List<WeatherAlert>>()
    val weatherAlerts: LiveData<List<WeatherAlert>> = _weatherAlerts

    private val _metars = MutableLiveData<List<MetarStation>>()
    val metars: LiveData<List<MetarStation>> = _metars

    private val _mbtaTrains = MutableLiveData<List<MbtaVehicle>>()
    val mbtaTrains: LiveData<List<MbtaVehicle>> = _mbtaTrains

    private val _mbtaSubway = MutableLiveData<List<MbtaVehicle>>()
    val mbtaSubway: LiveData<List<MbtaVehicle>> = _mbtaSubway

    private val _mbtaBuses = MutableLiveData<List<MbtaVehicle>>()
    val mbtaBuses: LiveData<List<MbtaVehicle>> = _mbtaBuses

    private val _aircraft = MutableLiveData<List<AircraftState>>()
    val aircraft: LiveData<List<AircraftState>> = _aircraft

    private val _followedAircraft = MutableLiveData<AircraftState?>()
    val followedAircraft: LiveData<AircraftState?> = _followedAircraft

    private val _radarRefreshTick = MutableLiveData<Long>()
    val radarRefreshTick: LiveData<Long> = _radarRefreshTick

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // ── Cap detection & subdivision ───────────────────────────────────────────

    private val _capDetected = MutableLiveData<CapEvent>()
    val capDetected: LiveData<CapEvent> = _capDetected

    private val _subdivisionComplete = MutableLiveData<Boolean>()
    val subdivisionComplete: LiveData<Boolean> = _subdivisionComplete

    private val subdivisionQueue = ConcurrentLinkedQueue<CapEvent>()
    private var subdivisionJob: Job? = null

    init {
        // Pure state init only — NO GPS, NO network calls.
        // GPS starts only after Activity grants permission via onPermissionGranted().
        DebugLogger.i(TAG, "ViewModel created — waiting for permission before starting GPS")

        // Collect cap events from PlacesRepository
        viewModelScope.launch {
            placesRepository.capEvents.collect { event ->
                DebugLogger.i(TAG, "Cap event received — raw=${event.rawCount} parsed=${event.parsedCount} radius=${event.radiusM}m")
                _capDetected.value = event
                enqueueSubdivision(event)
            }
        }
    }

    private fun enqueueSubdivision(event: CapEvent) {
        subdivisionQueue.add(event)
        if (subdivisionJob?.isActive != true) {
            subdivisionJob = viewModelScope.launch { processSubdivisionQueue() }
        }
    }

    private suspend fun processSubdivisionQueue() {
        while (subdivisionQueue.isNotEmpty()) {
            val event = subdivisionQueue.poll() ?: break
            subdivideCell(event.center, event.radiusM, event.categories, depth = 0)
        }
        _subdivisionComplete.postValue(true)
    }

    /** Recursively subdivide a capped cell into 2x2 sub-grid at half radius.
     *  Keeps halving until sub-cells stop capping or we hit MIN_SUBDIVISION_RADIUS. */
    private suspend fun subdivideCell(center: GeoPoint, radiusM: Int, categories: List<String>, depth: Int) {
        val halfRadius = radiusM / 2
        if (halfRadius < MIN_SUBDIVISION_RADIUS) {
            DebugLogger.w(TAG, "Subdivision hit floor (${halfRadius}m < ${MIN_SUBDIVISION_RADIUS}m) at depth=$depth — area too dense, accepting loss")
            return
        }
        val offsetDeg = halfRadius.toDouble() / 111320.0
        val cosLat = Math.cos(Math.toRadians(center.latitude))
        val offsetLon = offsetDeg / cosLat

        val subCenters = listOf(
            GeoPoint(center.latitude - offsetDeg, center.longitude - offsetLon),
            GeoPoint(center.latitude - offsetDeg, center.longitude + offsetLon),
            GeoPoint(center.latitude + offsetDeg, center.longitude - offsetLon),
            GeoPoint(center.latitude + offsetDeg, center.longitude + offsetLon)
        )

        DebugLogger.i(TAG, "Subdivision depth=$depth: 4 sub-cells at ${halfRadius}m radius")
        for ((i, subCenter) in subCenters.withIndex()) {
            try {
                val result = placesRepository.searchSubdivision(subCenter, categories, halfRadius)
                DebugLogger.d(TAG, "Subdivision d=$depth cell ${i+1}/4 → ${result.poiCount} POIs, capped=${result.capped}")
                if (result.capped) {
                    // Still capped — recurse deeper
                    subdivideCell(subCenter, halfRadius, categories, depth + 1)
                }
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Subdivision d=$depth cell ${i+1}/4 failed: ${e.message}")
            }
            if (i < subCenters.lastIndex) {
                delay(5000) // 5s pacing between subdivision queries
            }
        }
    }

    fun cancelSubdivision() {
        subdivisionJob?.cancel()
        subdivisionJob = null
        subdivisionQueue.clear()
        DebugLogger.i(TAG, "Subdivision cancelled")
    }

    companion object {
        const val MIN_SUBDIVISION_RADIUS = 250
    }

    // ── Location ──────────────────────────────────────────────────────────────

    /** Called by MainActivity after location permission is confirmed. Safe to call multiple times. */
    fun onPermissionGranted() {
        if (locationJob?.isActive == true) {
            DebugLogger.w(TAG, "onPermissionGranted() — location already running, skip")
            return
        }
        DebugLogger.i(TAG, "onPermissionGranted() — starting GMS location flow")
        _locationMode.value = LocationMode.GPS
        locationJob = viewModelScope.launch {
            locationManager.getLocationUpdates(intervalMs = 60_000L, minIntervalMs = 30_000L)
                .collect { loc ->
                    DebugLogger.d(TAG, "GPS update: lat=${loc.latitude} lon=${loc.longitude} acc=${loc.accuracy}m")
                    if (_locationMode.value == LocationMode.GPS) {
                        _currentLocation.value = GeoPoint(loc.latitude, loc.longitude)
                    }
                }
            DebugLogger.w(TAG, "GMS location flow ended — 0 updates (permission denied or GMS error)")
        }
    }

    /** Legacy call-site compatibility shim. Delegates to onPermissionGranted(). */
    fun startLocationUpdates() = onPermissionGranted()

    fun onGpsLocationUpdate(location: Location) {
        if (_locationMode.value == LocationMode.GPS) {
            _currentLocation.value = GeoPoint(location.latitude, location.longitude)
        }
    }

    fun setManualLocation(point: GeoPoint) {
        _currentLocation.value = point
        _locationMode.value = LocationMode.MANUAL
        DebugLogger.i(TAG, "Manual location: ${point.latitude}, ${point.longitude}")
    }

    /** Fires a one-shot last-known-location query to centre the map before the first periodic update. */
    fun requestLastKnownLocation() {
        DebugLogger.i(TAG, "requestLastKnownLocation() — asking FusedClient for cached fix")
        locationManager.getLastKnownLocation { loc ->
            if (loc != null && _locationMode.value == LocationMode.GPS) {
                DebugLogger.i(TAG, "lastKnownLocation: lat=${loc.latitude} lon=${loc.longitude} — centering map now")
                _currentLocation.value = GeoPoint(loc.latitude, loc.longitude)
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

    fun fetchWeatherAlerts() {
        DebugLogger.i(TAG, "fetchWeatherAlerts()")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchAlerts() }
                .onSuccess { DebugLogger.i(TAG, "Alerts success — ${it.size}"); _weatherAlerts.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Alerts FAILED: ${e.message}", e as? Exception); _error.value = "Alerts failed: ${e.message}" }
        }
    }

    fun loadMetars(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadMetars() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchMetars(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "METAR success — ${it.size}"); _metars.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "METAR FAILED: ${e.message}", e as? Exception); _error.value = "METAR failed: ${e.message}" }
        }
    }

    fun fetchMbtaTrains() {
        DebugLogger.i(TAG, "fetchMbtaTrains() — fetching commuter rail vehicles")
        viewModelScope.launch {
            runCatching { mbtaRepository.fetchCommuterRailVehicles() }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA trains success — ${it.size} vehicles")
                    _mbtaTrains.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA trains FAILED: ${e.message}", e as? Exception)
                    _error.value = "MBTA trains failed: ${e.message}"
                }
        }
    }

    fun clearMbtaTrains() {
        _mbtaTrains.value = emptyList()
        DebugLogger.i(TAG, "MBTA trains cleared")
    }

    fun fetchMbtaSubway() {
        DebugLogger.i(TAG, "fetchMbtaSubway() — fetching subway vehicles")
        viewModelScope.launch {
            runCatching { mbtaRepository.fetchSubwayVehicles() }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA subway success — ${it.size} vehicles")
                    _mbtaSubway.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA subway FAILED: ${e.message}", e as? Exception)
                    _error.value = "MBTA subway failed: ${e.message}"
                }
        }
    }

    fun clearMbtaSubway() {
        _mbtaSubway.value = emptyList()
        DebugLogger.i(TAG, "MBTA subway cleared")
    }

    fun fetchMbtaBuses() {
        DebugLogger.i(TAG, "fetchMbtaBuses() — fetching bus vehicles")
        viewModelScope.launch {
            runCatching { mbtaRepository.fetchBusVehicles() }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA buses success — ${it.size} vehicles")
                    _mbtaBuses.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA buses FAILED: ${e.message}", e as? Exception)
                    _error.value = "MBTA buses failed: ${e.message}"
                }
        }
    }

    fun clearMbtaBuses() {
        _mbtaBuses.value = emptyList()
        DebugLogger.i(TAG, "MBTA buses cleared")
    }

    fun loadAircraft(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadAircraft() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { aircraftRepository.fetchAircraft(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Aircraft success — ${it.size}"); _aircraft.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Aircraft FAILED: ${e.message}", e as? Exception); _error.value = "Aircraft failed: ${e.message}" }
        }
    }

    fun loadFollowedAircraft(icao24: String) {
        DebugLogger.i(TAG, "loadFollowedAircraft() icao24=$icao24")
        viewModelScope.launch {
            runCatching { aircraftRepository.fetchAircraftByIcao(icao24) }
                .onSuccess { state ->
                    DebugLogger.i(TAG, "Followed aircraft ${if (state != null) "found at ${state.lat},${state.lon}" else "NOT found"}")
                    _followedAircraft.value = state
                }
                .onFailure { e -> DebugLogger.e(TAG, "Followed aircraft FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun clearFollowedAircraft() {
        _followedAircraft.value = null
    }

    fun clearAircraft() {
        _aircraft.value = emptyList()
        DebugLogger.i(TAG, "Aircraft cleared")
    }

    // ── Webcams ────────────────────────────────────────────────────────────────

    private val _webcams = MutableLiveData<List<Webcam>>()
    val webcams: LiveData<List<Webcam>> = _webcams

    fun loadWebcams(south: Double, west: Double, north: Double, east: Double, categories: String) {
        DebugLogger.i(TAG, "loadWebcams() bbox=$south,$west,$north,$east categories=$categories")
        viewModelScope.launch {
            runCatching { webcamRepository.fetchWebcams(south, west, north, east, categories) }
                .onSuccess { DebugLogger.i(TAG, "Webcams success — ${it.size}"); _webcams.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Webcams FAILED: ${e.message}", e as? Exception); _error.value = "Webcams failed: ${e.message}" }
        }
    }

    fun clearWebcams() {
        _webcams.value = emptyList()
        DebugLogger.i(TAG, "Webcams cleared")
    }

    fun refreshRadar() {
        _radarRefreshTick.value = System.currentTimeMillis()
        DebugLogger.i(TAG, "Radar refresh tick")
    }
}

enum class LocationMode { GPS, MANUAL }
