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
import com.example.locationmapapp.data.repository.PlacesRepository
import com.example.locationmapapp.data.repository.WeatherRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
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
    private val mbtaRepository: MbtaRepository
) : ViewModel() {

    private val TAG = "ViewModel"

    private val _currentLocation = MutableLiveData<GeoPoint>()
    val currentLocation: LiveData<GeoPoint> = _currentLocation

    private val _locationMode = MutableLiveData<LocationMode>(LocationMode.GPS)
    val locationMode: LiveData<LocationMode> = _locationMode

    private var locationJob: Job? = null

    private val _places = MutableLiveData<List<PlaceResult>>()
    val places: LiveData<List<PlaceResult>> = _places

    private val _gasStations = MutableLiveData<List<PlaceResult>>()
    val gasStations: LiveData<List<PlaceResult>> = _gasStations

    private val _weatherAlerts = MutableLiveData<List<WeatherAlert>>()
    val weatherAlerts: LiveData<List<WeatherAlert>> = _weatherAlerts

    private val _metars = MutableLiveData<List<MetarStation>>()
    val metars: LiveData<List<MetarStation>> = _metars

    private val _earthquakes = MutableLiveData<List<EarthquakeEvent>>()
    val earthquakes: LiveData<List<EarthquakeEvent>> = _earthquakes

    private val _mbtaTrains = MutableLiveData<List<MbtaVehicle>>()
    val mbtaTrains: LiveData<List<MbtaVehicle>> = _mbtaTrains

    private val _mbtaSubway = MutableLiveData<List<MbtaVehicle>>()
    val mbtaSubway: LiveData<List<MbtaVehicle>> = _mbtaSubway

    private val _mbtaBuses = MutableLiveData<List<MbtaVehicle>>()
    val mbtaBuses: LiveData<List<MbtaVehicle>> = _mbtaBuses

    private val _radarRefreshTick = MutableLiveData<Long>()
    val radarRefreshTick: LiveData<Long> = _radarRefreshTick

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    init {
        // Pure state init only — NO GPS, NO network calls.
        // GPS starts only after Activity grants permission via onPermissionGranted().
        DebugLogger.i(TAG, "ViewModel created — waiting for permission before starting GPS")
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

    fun searchPoisAt(point: GeoPoint, categories: List<String> = emptyList()) {
        DebugLogger.i(TAG, "searchPoisAt() lat=${point.latitude} lon=${point.longitude} categories=$categories")
        viewModelScope.launch {
            runCatching { placesRepository.searchPois(point, categories) }
                .onSuccess { DebugLogger.i(TAG, "POI success — ${it.size}"); _places.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "POI FAILED: ${e.message}", e as? Exception); _error.value = "POI failed: ${e.message}" }
        }
    }

    fun searchPoisFromCache(point: GeoPoint) {
        viewModelScope.launch {
            runCatching { placesRepository.searchPoisCacheOnly(point) }
                .onSuccess { if (it.isNotEmpty()) { DebugLogger.i(TAG, "Cache POI hit — ${it.size}"); _places.value = it } }
                .onFailure { /* silent — cache miss is expected */ }
        }
    }

    fun loadGasStations(point: GeoPoint) {
        DebugLogger.i(TAG, "loadGasStations() lat=${point.latitude} lon=${point.longitude}")
        viewModelScope.launch {
            runCatching { placesRepository.searchGasStations(point) }
                .onSuccess { DebugLogger.i(TAG, "Gas stations success — ${it.size}"); _gasStations.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Gas FAILED: ${e.message}", e as? Exception); _error.value = "Gas failed: ${e.message}" }
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

    fun loadAllUsMetars() {
        DebugLogger.i(TAG, "loadAllUsMetars()")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchMetars() }
                .onSuccess { DebugLogger.i(TAG, "METAR success — ${it.size}"); _metars.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "METAR FAILED: ${e.message}", e as? Exception); _error.value = "METAR failed: ${e.message}" }
        }
    }

    fun loadEarthquakesForMap() {
        DebugLogger.i(TAG, "loadEarthquakesForMap()")
        viewModelScope.launch {
            runCatching { placesRepository.fetchEarthquakes() }
                .onSuccess { DebugLogger.i(TAG, "Earthquakes success — ${it.size}"); _earthquakes.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Earthquakes FAILED: ${e.message}", e as? Exception); _error.value = "Earthquakes failed: ${e.message}" }
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

    fun refreshRadar() {
        _radarRefreshTick.value = System.currentTimeMillis()
        DebugLogger.i(TAG, "Radar refresh tick")
    }
}

enum class LocationMode { GPS, MANUAL }
