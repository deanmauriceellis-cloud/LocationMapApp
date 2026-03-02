package com.example.locationmapapp.ui

import android.content.ContentResolver
import android.location.Location
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.MbtaPrediction
import com.example.locationmapapp.data.model.MbtaStop
import com.example.locationmapapp.data.model.MbtaTripScheduleEntry
import com.example.locationmapapp.data.model.MbtaVehicle
import com.example.locationmapapp.data.repository.MbtaRepository
import com.example.locationmapapp.data.location.LocationManager
import com.example.locationmapapp.data.model.*
import com.example.locationmapapp.data.model.ZoneType
import com.example.locationmapapp.data.repository.AircraftRepository
import com.example.locationmapapp.data.repository.FindRepository
import com.example.locationmapapp.data.repository.GeofenceDatabaseRepository
import com.example.locationmapapp.data.repository.GeofenceRepository
import com.example.locationmapapp.data.repository.PlacesRepository
import com.example.locationmapapp.data.repository.TfrRepository
import com.example.locationmapapp.data.repository.WebcamRepository
import com.example.locationmapapp.data.repository.WeatherRepository
import com.example.locationmapapp.util.GeofenceEngine
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

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
    private val placesRepository: PlacesRepository,
    private val weatherRepository: WeatherRepository,
    private val mbtaRepository: MbtaRepository,
    private val aircraftRepository: AircraftRepository,
    private val webcamRepository: WebcamRepository,
    private val findRepository: FindRepository,
    private val tfrRepository: TfrRepository,
    private val geofenceRepository: GeofenceRepository,
    private val geofenceDatabaseRepository: GeofenceDatabaseRepository
) : ViewModel() {

    private val TAG = "ViewModel"

    private val _currentLocation = MutableLiveData<LocationUpdate>()
    val currentLocation: LiveData<LocationUpdate> = _currentLocation

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

    private val _mbtaStations = MutableLiveData<List<MbtaStop>>()
    val mbtaStations: LiveData<List<MbtaStop>> = _mbtaStations

    private val _mbtaBusStops = MutableLiveData<List<MbtaStop>>()
    val mbtaBusStops: LiveData<List<MbtaStop>> = _mbtaBusStops

    private val _aircraft = MutableLiveData<List<AircraftState>>()
    val aircraft: LiveData<List<AircraftState>> = _aircraft

    private val _followedAircraft = MutableLiveData<AircraftState?>()
    val followedAircraft: LiveData<AircraftState?> = _followedAircraft

    private val _radarRefreshTick = MutableLiveData<Long>()
    val radarRefreshTick: LiveData<Long> = _radarRefreshTick

    private val _weatherData = MutableLiveData<WeatherData?>()
    val weatherData: LiveData<WeatherData?> = _weatherData

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

    fun fetchWeatherAlerts() {
        DebugLogger.i(TAG, "fetchWeatherAlerts()")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchAlerts() }
                .onSuccess { DebugLogger.i(TAG, "Alerts success — ${it.size}"); _weatherAlerts.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Alerts FAILED: ${e.message}", e as? Exception); _error.value = "Alerts failed: ${e.message}" }
        }
    }

    fun fetchWeather(lat: Double, lon: Double) {
        DebugLogger.i(TAG, "fetchWeather() lat=$lat lon=$lon")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchWeather(lat, lon) }
                .onSuccess { DebugLogger.i(TAG, "Weather success — ${it.location.city},${it.location.state}"); _weatherData.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Weather FAILED: ${e.message}", e as? Exception); _error.value = "Weather failed: ${e.message}" }
        }
    }

    /** Suspend call — returns weather data directly for dialog. */
    suspend fun fetchWeatherDirectly(lat: Double, lon: Double): WeatherData? {
        return try {
            weatherRepository.fetchWeather(lat, lon)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchWeatherDirectly FAILED: ${e.message}", e)
            null
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

    fun fetchMbtaStations() {
        DebugLogger.i(TAG, "fetchMbtaStations() — fetching subway + CR stations")
        viewModelScope.launch {
            runCatching { mbtaRepository.fetchStations() }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA stations success — ${it.size} stations")
                    _mbtaStations.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA stations FAILED: ${e.message}", e as? Exception)
                    _error.value = "MBTA stations failed: ${e.message}"
                }
        }
    }

    fun clearMbtaStations() {
        _mbtaStations.value = emptyList()
        DebugLogger.i(TAG, "MBTA stations cleared")
    }

    fun fetchMbtaBusStops() {
        DebugLogger.i(TAG, "fetchMbtaBusStops() — fetching all bus stops")
        viewModelScope.launch {
            runCatching { mbtaRepository.fetchBusStops() }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA bus stops success — ${it.size} stops")
                    _mbtaBusStops.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA bus stops FAILED: ${e.message}", e as? Exception)
                    _error.value = "MBTA bus stops failed: ${e.message}"
                }
        }
    }

    fun clearMbtaBusStops() {
        _mbtaBusStops.value = emptyList()
        DebugLogger.i(TAG, "MBTA bus stops cleared")
    }

    /** Suspend call — returns predictions directly for dialogs. */
    suspend fun fetchPredictionsDirectly(stopId: String): List<MbtaPrediction> {
        return try {
            mbtaRepository.fetchPredictions(stopId)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchPredictionsDirectly FAILED: ${e.message}", e)
            emptyList()
        }
    }

    /** Suspend call — returns trip schedule directly for dialogs. */
    suspend fun fetchTripScheduleDirectly(tripId: String): List<MbtaTripScheduleEntry> {
        return try {
            mbtaRepository.fetchTripSchedule(tripId)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchTripScheduleDirectly FAILED: ${e.message}", e)
            emptyList()
        }
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

    /** Suspend call — returns flight history path points directly for trail drawing. */
    suspend fun fetchFlightHistoryDirectly(icao24: String): List<FlightPathPoint> {
        return try {
            aircraftRepository.fetchFlightHistory(icao24)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchFlightHistoryDirectly FAILED: ${e.message}", e)
            emptyList()
        }
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

    // ── Find (POI Discovery) ──────────────────────────────────────────────────

    private val _findCounts = MutableLiveData<FindCounts?>()
    val findCounts: LiveData<FindCounts?> = _findCounts

    fun loadFindCounts() {
        DebugLogger.i(TAG, "loadFindCounts()")
        viewModelScope.launch {
            runCatching { findRepository.fetchCounts() }
                .onSuccess { _findCounts.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "FindCounts FAILED: ${e.message}", e as? Exception) }
        }
    }

    /** Direct suspend call for Find dialog — returns results without LiveData. */
    suspend fun findNearbyDirectly(lat: Double, lon: Double, categories: List<String>, limit: Int = 50, offset: Int = 0): FindResponse? {
        return try {
            findRepository.findNearby(lat, lon, categories, limit, offset)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "findNearbyDirectly FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun fetchPoiWebsiteDirectly(osmType: String, osmId: Long, name: String?, lat: Double, lon: Double): PoiWebsite? {
        return try {
            findRepository.fetchWebsite(osmType, osmId, name, lat, lon)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchPoiWebsiteDirectly FAILED: ${e.message}", e)
            null
        }
    }

    // ── TFR / Geofences ───────────────────────────────────────────────────────

    private val _tfrZones = MutableLiveData<List<TfrZone>>()
    val tfrZones: LiveData<List<TfrZone>> = _tfrZones

    private val _cameraZones = MutableLiveData<List<TfrZone>>()
    val cameraZones: LiveData<List<TfrZone>> = _cameraZones

    private val _schoolZones = MutableLiveData<List<TfrZone>>()
    val schoolZones: LiveData<List<TfrZone>> = _schoolZones

    private val _floodZones = MutableLiveData<List<TfrZone>>()
    val floodZones: LiveData<List<TfrZone>> = _floodZones

    private val _crossingZones = MutableLiveData<List<TfrZone>>()
    val crossingZones: LiveData<List<TfrZone>> = _crossingZones

    private val _databaseZones = MutableLiveData<List<TfrZone>>()
    val databaseZones: LiveData<List<TfrZone>> = _databaseZones

    private val _geofenceCatalog = MutableLiveData<List<GeofenceDatabaseInfo>>()
    val geofenceCatalog: LiveData<List<GeofenceDatabaseInfo>> = _geofenceCatalog

    private val _databaseDownloadProgress = MutableLiveData<Pair<String, Int>?>()
    val databaseDownloadProgress: LiveData<Pair<String, Int>?> = _databaseDownloadProgress

    private val _geofenceAlerts = MutableLiveData<List<GeofenceAlert>>()
    val geofenceAlerts: LiveData<List<GeofenceAlert>> = _geofenceAlerts

    private val _importResult = MutableLiveData<Pair<Boolean, String>?>()
    val importResult: LiveData<Pair<Boolean, String>?> = _importResult

    val geofenceEngine = GeofenceEngine()

    fun loadTfrs(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadTfrs() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { tfrRepository.fetchTfrs(south, west, north, east) }
                .onSuccess {
                    DebugLogger.i(TAG, "TFRs success — ${it.size}")
                    _tfrZones.value = it
                    rebuildGeofenceIndex()
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "TFRs FAILED: ${e.message}", e as? Exception)
                    _error.value = "TFRs failed: ${e.message}"
                }
        }
    }

    fun loadCameras(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadCameras() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchCameras(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Cameras success — ${it.size}"); _cameraZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "Cameras FAILED: ${e.message}", e as? Exception); _error.value = "Cameras failed: ${e.message}" }
        }
    }

    fun loadSchools(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadSchools() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchSchools(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Schools success — ${it.size}"); _schoolZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "Schools FAILED: ${e.message}", e as? Exception); _error.value = "Schools failed: ${e.message}" }
        }
    }

    fun loadFloodZones(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadFloodZones() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchFloodZones(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "FloodZones success — ${it.size}"); _floodZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "FloodZones FAILED: ${e.message}", e as? Exception); _error.value = "Flood zones failed: ${e.message}" }
        }
    }

    fun loadCrossings(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadCrossings() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchCrossings(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Crossings success — ${it.size}"); _crossingZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "Crossings FAILED: ${e.message}", e as? Exception); _error.value = "Crossings failed: ${e.message}" }
        }
    }

    /** Rebuild the spatial index from all zone type lists. */
    private fun rebuildGeofenceIndex() {
        val all = mutableListOf<TfrZone>()
        _tfrZones.value?.let { all.addAll(it) }
        _cameraZones.value?.let { all.addAll(it) }
        _schoolZones.value?.let { all.addAll(it) }
        _floodZones.value?.let { all.addAll(it) }
        _crossingZones.value?.let { all.addAll(it) }
        _databaseZones.value?.let { all.addAll(it) }
        geofenceEngine.loadZones(all)
    }

    /** Clear a specific zone type. */
    fun clearZoneType(zoneType: ZoneType) {
        when (zoneType) {
            ZoneType.TFR -> _tfrZones.value = emptyList()
            ZoneType.SPEED_CAMERA -> _cameraZones.value = emptyList()
            ZoneType.SCHOOL_ZONE -> _schoolZones.value = emptyList()
            ZoneType.FLOOD_ZONE -> _floodZones.value = emptyList()
            ZoneType.RAILROAD_CROSSING -> _crossingZones.value = emptyList()
            ZoneType.MILITARY_BASE, ZoneType.NO_FLY_ZONE, ZoneType.CUSTOM -> { /* database zones not per-type cleared */ }
        }
        rebuildGeofenceIndex()
        DebugLogger.i(TAG, "Cleared zone type $zoneType")
    }

    suspend fun fetchTfrsDirectly(south: Double, west: Double, north: Double, east: Double): List<TfrZone> {
        return try {
            tfrRepository.fetchTfrs(south, west, north, east)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchTfrsDirectly FAILED: ${e.message}", e)
            emptyList()
        }
    }

    fun checkGeofences(lat: Double, lon: Double, altFt: Double?, bearing: Double?) {
        val alerts = geofenceEngine.checkPosition(lat, lon, altFt, bearing)
        if (alerts.isNotEmpty()) {
            DebugLogger.i(TAG, "Geofence alerts: ${alerts.size} — ${alerts.map { "${it.alertType}:${it.zoneName}" }}")
            _geofenceAlerts.value = alerts
        }
    }

    fun clearTfrs() {
        _tfrZones.value = emptyList()
        _geofenceAlerts.value = emptyList()
        geofenceEngine.clear()
        DebugLogger.i(TAG, "TFRs cleared")
    }

    fun clearAllGeofences() {
        _tfrZones.value = emptyList()
        _cameraZones.value = emptyList()
        _schoolZones.value = emptyList()
        _floodZones.value = emptyList()
        _crossingZones.value = emptyList()
        _databaseZones.value = emptyList()
        _geofenceAlerts.value = emptyList()
        geofenceEngine.clear()
        DebugLogger.i(TAG, "All geofences cleared")
    }

    // ── Geofence Databases ───────────────────────────────────────────────────

    fun fetchGeofenceCatalog() {
        viewModelScope.launch {
            runCatching { geofenceDatabaseRepository.fetchCatalog() }
                .onSuccess { catalog ->
                    // Merge local-only databases not in remote catalog
                    val remoteIds = catalog.map { it.id }.toSet()
                    val localOnly = geofenceDatabaseRepository.getLocalOnlyDatabaseInfos()
                        .filter { it.id !in remoteIds }
                    val merged = catalog + localOnly
                    _geofenceCatalog.value = merged
                    DebugLogger.i(TAG, "Catalog: ${catalog.size} remote + ${localOnly.size} local-only = ${merged.size}")
                }
                .onFailure { e ->
                    // Even if remote fails, show local-only databases
                    val localOnly = geofenceDatabaseRepository.getLocalOnlyDatabaseInfos()
                    if (localOnly.isNotEmpty()) {
                        _geofenceCatalog.value = localOnly
                        DebugLogger.i(TAG, "Catalog remote FAILED, showing ${localOnly.size} local-only databases")
                    }
                    DebugLogger.e(TAG, "Catalog FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun importGeofenceDatabase(contentResolver: ContentResolver, uri: Uri, overwriteId: String? = null) {
        viewModelScope.launch {
            val result = geofenceDatabaseRepository.importSqliteDatabase(contentResolver, uri, overwriteId)
            _importResult.value = result
            if (result.first) fetchGeofenceCatalog()
        }
    }

    fun importCsvAsGeofenceDatabase(
        contentResolver: ContentResolver, uri: Uri,
        databaseId: String, databaseName: String,
        zoneType: ZoneType, defaultRadius: Double
    ) {
        viewModelScope.launch {
            val result = geofenceDatabaseRepository.importCsvAsDatabase(
                contentResolver, uri, databaseId, databaseName, zoneType, defaultRadius
            )
            _importResult.value = result
            if (result.first) fetchGeofenceCatalog()
        }
    }

    fun clearImportResult() { _importResult.value = null }

    fun getGeofenceDatabaseFile(id: String): java.io.File? =
        geofenceDatabaseRepository.getDatabaseFile(id)

    fun downloadGeofenceDatabase(id: String) {
        viewModelScope.launch {
            _databaseDownloadProgress.value = Pair(id, 0)
            val success = geofenceDatabaseRepository.downloadDatabase(id) { pct ->
                _databaseDownloadProgress.postValue(Pair(id, pct))
            }
            _databaseDownloadProgress.value = null
            if (success) {
                DebugLogger.i(TAG, "Database $id downloaded, refreshing catalog")
                fetchGeofenceCatalog()
            } else {
                _error.value = "Failed to download database: $id"
            }
        }
    }

    fun deleteGeofenceDatabase(id: String) {
        geofenceDatabaseRepository.deleteDatabase(id)
        _databaseZones.value = _databaseZones.value?.filter {
            val meta = it.metadata
            meta["database_id"] != id
        } ?: emptyList()
        rebuildGeofenceIndex()
        fetchGeofenceCatalog()
        DebugLogger.i(TAG, "Database $id deleted")
    }

    fun loadDatabaseZonesForVisibleArea(s: Double, w: Double, n: Double, e: Double) {
        viewModelScope.launch {
            val installed = geofenceDatabaseRepository.getInstalledDatabases()
            if (installed.isEmpty()) return@launch
            val allZones = mutableListOf<TfrZone>()
            for (dbId in installed) {
                val zones = geofenceDatabaseRepository.loadZonesFromDatabaseInBbox(dbId, s, w, n, e)
                // Tag each zone with its source database
                allZones.addAll(zones.map { zone ->
                    zone.copy(metadata = zone.metadata + ("database_id" to dbId))
                })
            }
            _databaseZones.value = allZones
            rebuildGeofenceIndex()
            DebugLogger.i(TAG, "Database zones: ${allZones.size} from ${installed.size} databases")
        }
    }

    fun hasInstalledDatabases(): Boolean =
        geofenceDatabaseRepository.getInstalledDatabases().isNotEmpty()
}

enum class LocationMode { GPS, MANUAL }
