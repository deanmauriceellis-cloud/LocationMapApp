/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.locationmapapp.R
import com.example.locationmapapp.data.model.GeocodeSuggestion
import com.example.locationmapapp.ui.menu.PoiCategories
import com.example.locationmapapp.ui.menu.PoiLayerId
import com.example.locationmapapp.databinding.ActivityMainBinding
import com.example.locationmapapp.ui.menu.AppBarMenuManager
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.ui.menu.MenuEventListener
import com.example.locationmapapp.ui.radar.RadarRefreshScheduler
import com.example.locationmapapp.util.DebugEndpoints
import com.example.locationmapapp.util.DebugHttpServer
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import androidx.preference.PreferenceManager

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivity.kt"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding
    internal val viewModel: MainViewModel by viewModels()
    internal val socialViewModel: SocialViewModel by viewModels()
    internal val transitViewModel: TransitViewModel by viewModels()
    internal val aircraftViewModel: AircraftViewModel by viewModels()
    internal val findViewModel: FindViewModel by viewModels()
    internal val weatherViewModel: WeatherViewModel by viewModels()
    internal val geofenceViewModel: GeofenceViewModel by viewModels()

    internal lateinit var appBarMenuManager: AppBarMenuManager
    internal val radarScheduler = RadarRefreshScheduler()
    internal lateinit var favoritesManager: com.example.locationmapapp.util.FavoritesManager

    internal val metarMarkers      = mutableListOf<Marker>()
    internal val poiMarkers        = mutableMapOf<String, MutableList<Marker>>()
    internal val clusterMarkers    = mutableListOf<Marker>()
    internal val trainMarkers      = mutableListOf<Marker>()
    internal val subwayMarkers     = mutableListOf<Marker>()
    internal val busMarkers        = mutableListOf<Marker>()
    internal var gpsMarker: Marker? = null
    internal var radarTileOverlay: TilesOverlay? = null
    internal var radarAlphaPercent: Int = 35  // 0–100, default 35%
    internal var fabMenuOpen = false

    // Radar animation
    internal val radarAnimFrames = mutableListOf<TilesOverlay>()
    internal var radarAnimIndex: Int = 0
    internal var radarAnimHandler: android.os.Handler? = null
    internal var radarAnimRunnable: Runnable? = null
    internal var radarAnimating: Boolean = false
    internal var radarAnimSpeedMs: Int = 800

    // MBTA auto-refresh
    internal var trainRefreshJob: Job? = null
    internal var trainRefreshIntervalSec: Int = 30
    internal var subwayRefreshJob: Job? = null
    internal var subwayRefreshIntervalSec: Int = 30
    internal var busRefreshJob: Job? = null
    internal var busRefreshIntervalSec: Int = 60

    // Vehicle / aircraft follow mode
    internal var followedVehicleId: String? = null
    internal var followedAircraftIcao: String? = null
    internal var lastFollowedVehicleLat: Double = 0.0
    internal var lastFollowedVehicleLon: Double = 0.0

    // Status line manager (priority-based, replaces followBanner)
    internal lateinit var statusLineManager: StatusLineManager

    // Debounced cache-only POI loader on map scroll
    internal var cachePoiJob: Job? = null
    // Debounced aircraft reload on scroll/zoom
    internal var aircraftReloadJob: Job? = null

    // Aircraft tracking
    internal val aircraftMarkers = mutableListOf<Marker>()
    internal var aircraftRefreshJob: Job? = null
    internal var aircraftRefreshIntervalSec: Int = 60
    internal var followedAircraftRefreshJob: Job? = null
    internal var lastFollowedAircraftState: com.example.locationmapapp.data.model.AircraftState? = null
    // Auto-follow random high-altitude aircraft for POI cache building
    internal var autoFollowAircraftJob: Job? = null
    internal var followedAircraftFailCount: Int = 0
    internal var autoFollowEmptyPoiCount: Int = 0
    // Bounce direction — auto-flip when aircraft nears CONUS boundary
    internal var autoFollowPreferWest: Boolean = true
    internal var autoFollowPreferSouth: Boolean = false

    // Station markers
    internal val stationMarkers = mutableListOf<Marker>()

    // Bus stop markers (viewport-filtered from allBusStops)
    internal val busStopMarkers = mutableListOf<Marker>()
    internal var allBusStops: List<com.example.locationmapapp.data.model.MbtaStop> = emptyList()
    internal var busStopReloadJob: Job? = null

    // Flight trail overlays (aircraft follow mode)
    internal val flightTrailPoints = mutableListOf<com.example.locationmapapp.data.model.FlightPathPoint>()
    internal val flightTrailOverlays = mutableListOf<Polyline>()

    // Emergency squawk dedup — one alert per icao24 per session
    internal val emSquawkAlerted = mutableSetOf<String>()

    // Webcam tracking
    internal val webcamMarkers = mutableListOf<Marker>()
    internal var webcamReloadJob: Job? = null
    internal var pendingWebcamRestore = false

    // Populate POIs scanner
    internal var populateJob: Job? = null
    internal var scanningMarker: Marker? = null

    // Silent background POI fill — single center search on startup / position change
    internal var silentFillJob: Job? = null
    internal var silentFillRunnable: Runnable? = null

    // Idle auto-populate — full scanner triggered after 10 min of GPS stationarity
    internal var idlePopulateJob: Job? = null
    internal var lastSignificantMoveTime: Long = System.currentTimeMillis()

    /** Refresh the layer count badge on the grid toolbar icon. */
    internal fun refreshLayerBadge() {
        appBarMenuManager.updateLayerBadge(layersBadgeView, appBarMenuManager.computeActiveLayerCount())
    }

    /** Reset idle timer on any UI activity — grid, dialogs, etc. */
    internal fun resetIdleTimer() {
        lastSignificantMoveTime = System.currentTimeMillis()
        if (idlePopulateJob?.isActive == true) {
            DebugLogger.i("MainActivity", "UI activity — stopping idle auto-populate")
            stopIdlePopulate()
        }
    }
    internal var idlePopulateState: IdlePopulateState? = null

    // Weather auto-fetch
    internal var weatherMenuItem: android.view.MenuItem? = null
    internal var weatherIconView: ImageView? = null
    internal var alertsIconView: ImageView? = null
    internal var alertsBadgeView: android.widget.TextView? = null
    internal var layersBadgeView: android.widget.TextView? = null
    internal var lastWeatherFetchTime: Long = 0L
    internal var weatherAutoFetchJob: Job? = null
    internal val WEATHER_FETCH_INTERVAL_MS = 30 * 60 * 1000L  // 30 min

    // TFR / Geofence
    internal val tfrOverlays = mutableListOf<org.osmdroid.views.overlay.Polygon>()
    internal val cameraOverlays = mutableListOf<org.osmdroid.views.overlay.Polygon>()
    internal val schoolOverlays = mutableListOf<org.osmdroid.views.overlay.Polygon>()
    internal val floodOverlays = mutableListOf<org.osmdroid.views.overlay.Polygon>()
    internal val crossingOverlays = mutableListOf<org.osmdroid.views.overlay.Polygon>()
    internal val databaseOverlays = mutableListOf<org.osmdroid.views.overlay.Polygon>()
    internal var geofenceReloadJob: Job? = null
    internal var pendingGeofenceRestore = false
    internal var alertPulseAnimation: android.view.animation.AlphaAnimation? = null

    // POI / vehicle label zoom threshold tracking
    internal var poiLabelsShowing = false
    internal var vehicleLabelsShowing = false
    internal var transitMarkersVisible = true
    internal var stationsVisible = false  // stations only at zoom ≥ 12
    internal var metarLabelsShowing = false  // METAR labels only at zoom ≥ 10

    // Deferred restore — wait for first real GPS fix so the map has a valid bounding box
    internal var pendingPoiRestore = false
    internal var pendingMetarRestore = false
    internal var pendingCachedPoiLoad = false

    internal var pendingAircraftRestore = false
    internal var pendingAutoFollowRestore = false
    internal var initialCenterDone = false   // only auto-center map on FIRST GPS fix

    // Smart GPS position tracking
    internal var lastGpsPoint: GeoPoint? = null
    internal var lastPoiFetchPoint: GeoPoint? = null
    internal var lastApiCallTime: Long = 0
    internal var currentGpsIntervalMs: Long = 60_000L
    internal var lastGpsSpeedMph: Double? = null

    /** Load METARs for the current visible map bounding box. */
    internal fun loadMetarsForVisibleArea() {
        val bb = binding.mapView.boundingBox
        weatherViewModel.loadMetars(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }

    /** Debounced: reload aircraft 1s after user stops scrolling/zooming. */
    internal fun scheduleAircraftReload() {
        if (aircraftRefreshJob?.isActive != true) return  // aircraft tracking not enabled
        aircraftReloadJob?.cancel()
        aircraftReloadJob = lifecycleScope.launch {
            delay(1000)
            loadAircraftForVisibleArea()
        }
    }

    /** Load aircraft for the current visible map bounding box. Requires zoom >= 10 to avoid massive queries. */
    internal fun loadAircraftForVisibleArea() {
        val zoom = binding.mapView.zoomLevelDouble
        if (zoom < 10.0) {
            DebugLogger.d("MainActivity", "Aircraft skipped — zoom ${zoom.toInt()} < 10")
            return
        }
        val bb = binding.mapView.boundingBox
        DebugLogger.i("MainActivity", "loadAircraftForVisibleArea zoom=${zoom.toInt()} bbox=${bb.latSouth},${bb.lonWest},${bb.latNorth},${bb.lonEast}")
        aircraftViewModel.loadAircraft(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }

    // v1.4: guard to prevent startGpsUpdatesAndCenter() running twice
    // (fast-path pre-check + launcher callback can both fire on first run).
    internal var locationUpdatesStarted = false

    // v1.4: GPS is managed entirely by the ViewModel via GMS FusedLocationProviderClient.

    internal val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        DebugLogger.i("MainActivity", "Location permission result: granted=$granted")
        if (granted) {
            startGpsUpdatesAndCenter()
        } else {
            DebugLogger.w("MainActivity", "Location permission denied — staying at default center")
        }
    }

    // ── SAF file pickers for geofence database import ─────────────────────
    internal var pendingImportUri: Uri? = null

    internal val dbImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            geofenceViewModel.importGeofenceDatabase(contentResolver, uri)
        }
    }

    internal val csvImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) showCsvImportConfigDialog(uri)
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.i("MainActivity", "onCreate() start — device=${android.os.Build.MODEL} SDK=${android.os.Build.VERSION.SDK_INT}")
        DebugHttpServer.start()

        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = "LocationMapApp/1.2 (Android)"
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Toolbar (slim layout) ─────────────────────────────────────────
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        DebugLogger.i("MainActivity", "setSupportActionBar complete — title hidden for slim toolbar")

        // ── Menu manager — slim toolbar with 3 icons + grid dropdown ──────
        appBarMenuManager = AppBarMenuManager(
            context           = this,
            toolbar           = binding.toolbar,
            menuEventListener = menuEventListenerImpl
        )
        val toolbarRefs = appBarMenuManager.setupSlimToolbar(
            weatherIcon  = binding.root.findViewById(R.id.toolbarWeatherIcon),
            alertsIcon   = binding.root.findViewById(R.id.toolbarAlertsIcon),
            gridButton   = binding.root.findViewById(R.id.toolbarGridButton),
            statusLine   = binding.root.findViewById(R.id.toolbarStatusLine),
            darkModeIcon = binding.root.findViewById(R.id.toolbarDarkModeIcon),
            homeIcon     = binding.root.findViewById(R.id.toolbarHomeIcon),
            aboutIcon    = binding.root.findViewById(R.id.toolbarAboutIcon),
            alertsBadge  = binding.root.findViewById(R.id.alertsBadge),
            layersBadge  = binding.root.findViewById(R.id.layersBadge)
        )
        weatherIconView = toolbarRefs.weatherIcon
        alertsIconView  = toolbarRefs.alertsIcon
        alertsBadgeView = toolbarRefs.alertsBadge
        layersBadgeView = toolbarRefs.layersBadge
        // Set initial layer badge count
        appBarMenuManager.updateLayerBadge(layersBadgeView, appBarMenuManager.computeActiveLayerCount())
        statusLineManager = StatusLineManager(toolbarRefs.statusLine)
        favoritesManager = com.example.locationmapapp.util.FavoritesManager(this)
        DebugLogger.i("MainActivity", "Slim toolbar wired — Weather, Home, Alerts, Grid, About + StatusLine")

        setupMap()
        buildFabSpeedDial()
        observeViewModel()
        requestLocationPermission()
        // Post debug intent to next frame so it runs after onStart() restore
        if (intent?.extras?.let { it.containsKey("lat") || it.containsKey("zoom") ||
                    it.containsKey("enable") || it.containsKey("disable") } == true) {
            binding.mapView.post { handleDebugIntent(intent) }
        }
        DebugLogger.i("MainActivity", "onCreate() complete")
    }

    // Slim toolbar is set up in onCreate() — no menu inflation needed.
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        DebugLogger.i("MainActivity", "onCreateOptionsMenu() — skipped (using slim toolbar)")
        return true
    }

    override fun onStart() {
        super.onStart()
        DebugLogger.i("MainActivity", "onStart() toolbar.childCount=${binding.toolbar.childCount}")
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)

        // Populate is never auto-restored — user must pick a location and start manually
        prefs.edit().putBoolean(MenuPrefs.PREF_POPULATE_POIS, false).apply()

        // Restore radar — add the tile overlay AND restart the refresh scheduler
        val radarOn = prefs.getBoolean(MenuPrefs.PREF_RADAR_ON, true)
        DebugLogger.i("MainActivity", "onStart() radarOn=$radarOn interval=${appBarMenuManager.radarUpdateMinutes}min")
        if (radarOn) {
            if (radarTileOverlay == null) {
                addRadarOverlay()
                DebugLogger.i("MainActivity", "Radar overlay restored from onStart()")
            }
            radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { weatherViewModel.refreshRadar() }
            DebugLogger.i("MainActivity", "Radar scheduler restarted from onStart()")
        }

        // Restore MBTA layers — stagger startup to avoid ANR
        lifecycleScope.launch {
            delay(2000) // let map render first
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_TRAINS, true) && trainRefreshJob?.isActive != true) {
                DebugLogger.i("MainActivity", "onStart() restoring MBTA trains")
                startTrainRefresh()
            }
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_SUBWAY, true) && subwayRefreshJob?.isActive != true) {
                DebugLogger.i("MainActivity", "onStart() restoring MBTA subway")
                startSubwayRefresh()
            }
            delay(500)
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_BUSES, true) && busRefreshJob?.isActive != true) {
                DebugLogger.i("MainActivity", "onStart() restoring MBTA buses")
                startBusRefresh()
            }
            delay(500)
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_STATIONS, true) && stationMarkers.isEmpty()) {
                DebugLogger.i("MainActivity", "onStart() restoring MBTA stations")
                transitViewModel.fetchMbtaStations()
            }
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_BUS_STOPS, false) && allBusStops.isEmpty()) {
                DebugLogger.i("MainActivity", "onStart() restoring MBTA bus stops")
                transitViewModel.fetchMbtaBusStops()
            }
        }

        // Defer METAR restore until GPS fix so the map has a real bounding box
        if (prefs.getBoolean(MenuPrefs.PREF_METAR_DISPLAY, true)) {
            pendingMetarRestore = true
            DebugLogger.i("MainActivity", "onStart() METAR restore deferred — waiting for GPS fix")
        }

        // Defer Aircraft restore
        if (prefs.getBoolean(MenuPrefs.PREF_AIRCRAFT_DISPLAY, false)) {
            pendingAircraftRestore = true
            DebugLogger.i("MainActivity", "onStart() Aircraft restore deferred — waiting for GPS fix")
        }

        // Restore auto-follow aircraft if it was active
        if (prefs.getBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, false)) {
            pendingAutoFollowRestore = true
            DebugLogger.i("MainActivity", "onStart() Auto-follow restore deferred — waiting for GPS fix + aircraft data")
        }

        // Defer POI restore until first GPS fix so we query at the real location
        val anyPoiEnabled = PoiCategories.ALL.any { prefs.getBoolean(it.prefKey, true) }
        if (anyPoiEnabled) {
            pendingPoiRestore = true
            DebugLogger.i("MainActivity", "onStart() POI restore deferred — waiting for GPS fix")
        }
        // Defer webcam restore until GPS fix
        if (prefs.getBoolean(MenuPrefs.PREF_WEBCAMS_ON, true)) {
            pendingWebcamRestore = true
            DebugLogger.i("MainActivity", "onStart() Webcam restore deferred — waiting for GPS fix")
        }

        // Defer geofence overlay restore until GPS fix
        val anyGeofence = prefs.getBoolean(MenuPrefs.PREF_TFR_OVERLAY, true)
            || prefs.getBoolean(MenuPrefs.PREF_CAMERA_OVERLAY, false)
            || prefs.getBoolean(MenuPrefs.PREF_SCHOOL_OVERLAY, false)
            || prefs.getBoolean(MenuPrefs.PREF_FLOOD_OVERLAY, false)
            || prefs.getBoolean(MenuPrefs.PREF_CROSSING_OVERLAY, false)
        if (anyGeofence) {
            pendingGeofenceRestore = true
            DebugLogger.i("MainActivity", "onStart() Geofence overlay restore deferred — waiting for GPS fix")
        }

        // Always load full cached POI coverage after GPS fix
        pendingCachedPoiLoad = true
    }

    override fun onStop()   { super.onStop();   if (radarAnimating) stopRadarAnimation(); radarScheduler.stop(); DebugLogger.i("MainActivity","onStop()") }
    override fun onResume() {
        super.onResume(); binding.mapView.onResume()
        DebugHttpServer.endpoints = DebugEndpoints(this, viewModel, transitViewModel, aircraftViewModel, weatherViewModel, geofenceViewModel)
        DebugLogger.i("MainActivity","onResume()")
    }
    override fun onPause() {
        super.onPause(); binding.mapView.onPause()
        DebugHttpServer.endpoints = null
        DebugLogger.i("MainActivity","onPause()")
    }
    override fun onDestroy() {
        super.onDestroy()
        DebugHttpServer.stop()
        DebugLogger.i("MainActivity","onDestroy()")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    /**
     * Debug intent handler — drive the app from adb for testing.
     *
     * Usage:
     *   adb shell am start -n com.example.locationmapapp/.ui.MainActivity \
     *     --ef lat 42.3601 --ef lon -71.0589 --ei zoom 15 \
     *     --es enable "mbta_stations_on,radar_on" \
     *     --es disable "webcams_on,aircraft_display_on"
     *
     * Extras:
     *   lat/lon  (float)  — center map at these coordinates
     *   zoom     (int)    — set zoom level (1–20)
     *   enable   (string) — comma-separated pref keys to turn ON
     *   disable  (string) — comma-separated pref keys to turn OFF
     *
     * Pref keys: mbta_stations_on, mbta_trains_on, mbta_subway_on, mbta_buses_on,
     *   radar_on, metar_display_on, aircraft_display_on, webcams_on,
     *   weather_alerts_on, national_alerts_on
     */
    internal fun handleDebugIntent(intent: Intent?) {
        if (intent == null) return
        val extras = intent.extras ?: return
        val hasDebug = extras.containsKey("lat") || extras.containsKey("zoom") ||
                extras.containsKey("enable") || extras.containsKey("disable")
        if (!hasDebug) return

        DebugLogger.i("MainActivity", "handleDebugIntent — processing debug extras")

        // ── Map position ──
        if (extras.containsKey("lat") && extras.containsKey("lon")) {
            val lat = extras.getFloat("lat").toDouble()
            val lon = extras.getFloat("lon").toDouble()
            val point = GeoPoint(lat, lon)
            binding.mapView.controller.animateTo(point)
            initialCenterDone = true  // suppress GPS auto-center
            DebugLogger.i("MainActivity", "Debug: center map at $lat, $lon")
        }
        if (extras.containsKey("zoom")) {
            val zoom = extras.getInt("zoom").toDouble().coerceIn(1.0, 20.0)
            binding.mapView.controller.setZoom(zoom)
            updateZoomBubble()
            DebugLogger.i("MainActivity", "Debug: zoom=$zoom")
        }

        // ── Layer toggles ──
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        val toggleMap = mapOf<String, (Boolean) -> Unit>(
            MenuPrefs.PREF_MBTA_STATIONS   to { menuEventListenerImpl.onMbtaStationsToggled(it) },
            MenuPrefs.PREF_MBTA_BUS_STOPS  to { menuEventListenerImpl.onMbtaBusStopsToggled(it) },
            MenuPrefs.PREF_MBTA_TRAINS     to { menuEventListenerImpl.onMbtaTrainsToggled(it) },
            MenuPrefs.PREF_MBTA_SUBWAY     to { menuEventListenerImpl.onMbtaSubwayToggled(it) },
            MenuPrefs.PREF_MBTA_BUSES      to { menuEventListenerImpl.onMbtaBusesToggled(it) },
            MenuPrefs.PREF_RADAR_ON         to { menuEventListenerImpl.onRadarToggled(it) },
            MenuPrefs.PREF_METAR_DISPLAY    to { menuEventListenerImpl.onMetarDisplayToggled(it) },
            MenuPrefs.PREF_AIRCRAFT_DISPLAY to { menuEventListenerImpl.onAircraftDisplayToggled(it) },
            MenuPrefs.PREF_WEBCAMS_ON       to { menuEventListenerImpl.onWebcamToggled(it) },
        )

        extras.getString("enable")?.split(",")?.map { it.trim() }?.forEach { key ->
            val handler = toggleMap[key]
            if (handler != null) {
                prefs.edit().putBoolean(key, true).apply()
                handler(true)
                DebugLogger.i("MainActivity", "Debug: enabled $key")
            } else {
                DebugLogger.w("MainActivity", "Debug: unknown pref key '$key'")
            }
        }
        extras.getString("disable")?.split(",")?.map { it.trim() }?.forEach { key ->
            val handler = toggleMap[key]
            if (handler != null) {
                prefs.edit().putBoolean(key, false).apply()
                handler(false)
                DebugLogger.i("MainActivity", "Debug: disabled $key")
            } else {
                DebugLogger.w("MainActivity", "Debug: unknown pref key '$key'")
            }
        }

        // Clear the extras so they don't re-fire on configuration change
        intent.replaceExtras(Bundle())
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val map = binding.mapView
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_PLUS, android.view.KeyEvent.KEYCODE_NUMPAD_ADD -> {
                map.controller.setZoom((map.zoomLevelDouble + 1.0).coerceAtMost(map.maxZoomLevel))
                updateZoomBubble()
                true
            }
            android.view.KeyEvent.KEYCODE_MINUS, android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                map.controller.setZoom((map.zoomLevelDouble - 1.0).coerceAtLeast(map.minZoomLevel))
                updateZoomBubble()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // =========================================================================
    // MAP SETUP
    // =========================================================================

    internal fun setupMap() {
        DebugLogger.i("MainActivity", "setupMap() — initial center=US_DEFAULT zoom=6 (will update on GPS fix)")
        val darkModePrefs = getSharedPreferences(MenuPrefs.PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = darkModePrefs.getBoolean(MenuPrefs.PREF_DARK_MODE, false)
        binding.mapView.apply {
            setTileSource(if (isDarkMode) buildDarkTileSource() else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Disable built-in zoom buttons — we use the custom slider instead
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setBuiltInZoomControls(false)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(39.8283, -98.5795)) // fallback only
        }
        setupZoomSlider()
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                var consumed = false
                // Stop following any vehicle or aircraft
                if (followedVehicleId != null || followedAircraftIcao != null) {
                    stopFollowing()
                    consumed = true
                }
                // Stop population tasks
                if (populateJob != null) {
                    stopPopulatePois()
                    consumed = true
                }
                if (probe10kmJob != null) {
                    stopProbe10km()
                    consumed = true
                }
                if (idlePopulateJob != null) {
                    stopIdlePopulate(clearState = true)
                    consumed = true
                }
                if (silentFillJob != null) {
                    stopSilentFill()
                    consumed = true
                }
                return consumed
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                DebugLogger.i("MainActivity", "Long-press → manual mode at ${p.latitude},${p.longitude}")
                // Stop populate scanner, idle scanner, and silent fill — user is moving to a new location
                if (populateJob != null) stopPopulatePois()
                stopProbe10km()
                stopIdlePopulate(clearState = true)
                stopSilentFill()
                viewModel.setManualLocation(p)
                // Zoom to 14 if currently zoomed out; leave alone if already 14+
                val targetZoom = if (binding.mapView.zoomLevelDouble < 18.0) 18.0 else binding.mapView.zoomLevelDouble
                binding.mapView.controller.animateTo(p, targetZoom, null)
                triggerFullSearch(p)
                // Programmatic animateTo doesn't fire onScroll — schedule bbox POI refresh
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(2000)
                    loadCachedPoisForVisibleArea()
                }
                // Silent fill at new position — runs after triggerFullSearch settles
                scheduleSilentFill(p, 3000)
                // Fetch weather + alerts at the new location
                weatherViewModel.fetchWeather(p.latitude, p.longitude)
                toast("Manual mode — searching POIs…")
                return true
            }
        }
        binding.mapView.overlays.add(0, MapEventsOverlay(eventsReceiver))

        // Stop idle populate on any user touch — reset idle timer so it takes another 10 min
        binding.mapView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                resetIdleTimer()
            }
            false // don't consume — let map handle normally
        }

        DebugLogger.i("MainActivity", "Map configured — overlays=${binding.mapView.overlays.size}")
    }

    @SuppressLint("ClickableViewAccessibility")
    internal fun setupZoomSlider() {
        val map = binding.mapView

        // + button: zoom in
        binding.btnZoomIn.setOnClickListener {
            val newZoom = (map.zoomLevelDouble + 1.0).coerceAtMost(map.maxZoomLevel)
            map.controller.setZoom(newZoom)
            updateZoomBubble()
        }

        // − button: zoom out
        binding.btnZoomOut.setOnClickListener {
            val newZoom = (map.zoomLevelDouble - 1.0).coerceAtLeast(map.minZoomLevel)
            map.controller.setZoom(newZoom)
            updateZoomBubble()
        }

        // Drag the track area to scrub zoom level
        binding.zoomTrackArea.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> {
                    val fraction = (event.y / v.height.toFloat()).coerceIn(0f, 1f)
                    // Top = max zoom, bottom = min zoom
                    val zoom = map.maxZoomLevel - fraction * (map.maxZoomLevel - map.minZoomLevel)
                    map.controller.setZoom(zoom)
                    updateZoomBubble()
                    true
                }
                else -> false
            }
        }

        // Listen for zoom/scroll changes from pinch-to-zoom, programmatic changes, or panning
        map.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                if (!filterAndMapActive) {
                    scheduleAircraftReload()
                    scheduleBusStopReload()
                    // Suppress webcam reloads while populate scanner is running
                    if (populateJob == null) scheduleWebcamReload()
                    scheduleGeofenceReload()
                }
                // Debounce: load cached POIs for visible bbox 500ms after scrolling stops
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(500)
                    if (filterAndMapActive) return@launch
                    if (findFilterActive) loadFilteredPois() else loadCachedPoisForVisibleArea()
                }
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                updateZoomBubble()
                val zoom = binding.mapView.zoomLevelDouble
                if (!filterAndMapActive) {
                    scheduleAircraftReload()
                    scheduleBusStopReload()
                    if (populateJob == null) scheduleWebcamReload()
                    scheduleGeofenceReload()
                    // Hide transit markers when zoomed out to 10 or less
                    if (zoom <= 10.0) {
                        if (transitMarkersVisible) {
                            transitMarkersVisible = false
                            clearTrainMarkers(); clearSubwayMarkers(); clearBusMarkers()
                            clearStationMarkers(); clearBusStopMarkers()
                            binding.mapView.invalidate()
                        }
                    } else if (!transitMarkersVisible) {
                        transitMarkersVisible = true
                        vehicleLabelsShowing = zoom >= 18.0
                        // Re-add from latest LiveData values
                        transitViewModel.mbtaTrains.value?.let { it.forEach { v -> addTrainMarker(v) } }
                        transitViewModel.mbtaSubway.value?.let { it.forEach { v -> addSubwayMarker(v) } }
                        transitViewModel.mbtaBuses.value?.let { it.forEach { v -> addBusMarker(v) } }
                        if (zoom >= 12.0) {
                            transitViewModel.mbtaStations.value?.let { addStationMarkersBatched(it) }
                        }
                        refreshBusStopMarkersForViewport()
                        binding.mapView.invalidate()
                    }
                }
                // Refresh POI marker icons when crossing the zoom-16 label threshold
                // In filter-and-map mode, always force labels
                val nowLabeled = zoom >= 16.0 || filterAndMapActive
                if (nowLabeled != poiLabelsShowing) {
                    poiLabelsShowing = nowLabeled
                    if (!filterAndMapActive) refreshPoiMarkerIcons()
                }
                // Refresh vehicle marker icons when crossing the zoom-18 label threshold
                if (!filterAndMapActive) {
                    val vehicleLabeled = zoom >= 16.0
                    if (vehicleLabeled != vehicleLabelsShowing) {
                        vehicleLabelsShowing = vehicleLabeled
                        refreshVehicleMarkerIcons()
                    }
                }
                // Toggle METAR labels at zoom 10 threshold
                if (!filterAndMapActive) {
                    val nowMetarLabels = zoom >= 10.0
                    if (nowMetarLabels != metarLabelsShowing) {
                        metarLabelsShowing = nowMetarLabels
                        refreshMetarMarkerIcons()
                    }
                }
                // Show/hide station markers at zoom 12 threshold
                if (transitMarkersVisible && !filterAndMapActive) {
                    val nowStations = zoom >= 12.0
                    if (nowStations != stationsVisible) {
                        stationsVisible = nowStations
                        if (nowStations) {
                            transitViewModel.mbtaStations.value?.let { addStationMarkersBatched(it) }
                        } else {
                            clearStationMarkers()
                        }
                        binding.mapView.invalidate()
                    }
                }
                return false
            }
        })

        // Initial position
        binding.zoomBubble.post { updateZoomBubble() }
    }

    internal fun updateZoomBubble() {
        val map = binding.mapView
        val zoom = map.zoomLevelDouble
        val trackArea = binding.zoomTrackArea
        val bubble = binding.zoomBubble

        // Display zoom level as integer
        bubble.text = "${zoom.toInt()}"

        // Position bubble on track: top = max zoom, bottom = min zoom
        trackArea.post {
            val trackH = trackArea.height.toFloat()
            val bubbleH = bubble.height.toFloat()
            if (trackH <= 0) return@post
            val range = map.maxZoomLevel - map.minZoomLevel
            val fraction = if (range > 0) (map.maxZoomLevel - zoom) / range else 0.5
            val topY = (fraction * (trackH - bubbleH)).toFloat().coerceIn(0f, trackH - bubbleH)
            bubble.translationY = topY
        }
    }

    // =========================================================================
    // LOCATION — startup centering fix
    // =========================================================================

    internal fun requestLocationPermission() {
        DebugLogger.i("MainActivity", "requestLocationPermission() — checking current state")

        // Fast path: permission already granted from a previous session.
        // Skip the launcher round-trip and start GPS immediately so the map
        // centres on the user right away on every cold start after first install.
        val alreadyGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            DebugLogger.i("MainActivity", "requestLocationPermission() — already granted, starting GPS directly")
            startGpsUpdatesAndCenter()
            return
        }

        // Slow path: first install or user previously denied — ask the system.
        DebugLogger.i("MainActivity", "requestLocationPermission() — launching permission launcher")
        locationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    /**
     * Called once permissions are confirmed.
     * BUG FIX: immediately tries lastKnownLocation for instant map centering,
     * then registers ongoing updates. This avoids the cold-start delay where
     * the map sits on the hardcoded US center until the first GPS fix arrives.
     */
    @SuppressLint("MissingPermission")
    internal fun startGpsUpdatesAndCenter() {
        if (locationUpdatesStarted) {
            DebugLogger.w("MainActivity", "startGpsUpdatesAndCenter() called again — SKIPPING")
            return
        }
        locationUpdatesStarted = true
        DebugLogger.i("MainActivity", "startGpsUpdatesAndCenter() — permission confirmed, starting GMS flow")

        // Tell ViewModel permission is granted — this starts the GMS Flow internally.
        viewModel.onPermissionGranted()

        // Immediate centering: get the cached last-known fix from FusedClient.
        // Fires asynchronously and centres the map before the first 5s poll arrives.
        viewModel.requestLastKnownLocation()
    }

    fun toggleLocationMode() {
        viewModel.toggleLocationMode()
        DebugLogger.i("MainActivity", "Location mode toggled → ${viewModel.locationMode.value}")
        toast("Location mode: ${viewModel.locationMode.value}")
    }

    /** FAB quick-toggle for aircraft layer — mirrors the menu toggle logic. */
    internal fun toggleAircraftFromFab() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        val wasOn = prefs.getBoolean(MenuPrefs.PREF_AIRCRAFT_DISPLAY, false)
        val nowOn = !wasOn
        prefs.edit().putBoolean(MenuPrefs.PREF_AIRCRAFT_DISPLAY, nowOn).apply()
        if (nowOn) {
            startAircraftRefresh()
            toast("Aircraft tracking ON")
        } else {
            stopAircraftRefresh()
            aircraftViewModel.clearAircraft()
            clearAircraftMarkers()
            // Cancel auto-follow if aircraft layer toggled off via FAB
            if (autoFollowAircraftJob?.isActive == true) {
                autoFollowAircraftJob?.cancel()
                autoFollowAircraftJob = null
                prefs.edit().putBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, false).apply()
                stopFollowing()
                DebugLogger.i("MainActivity", "Auto-follow cancelled — aircraft layer FAB off")
            }
            toast("Aircraft tracking OFF")
        }
    }

    // =========================================================================
    // FAB SPEED DIAL
    // =========================================================================

    internal data class FabDef(val label: String, val iconRes: Int, val color: String, val action: () -> Unit)

    internal fun buildFabSpeedDial() {
        DebugLogger.i("MainActivity", "buildFabSpeedDial() start")
        val defs = listOf(
            FabDef("Radar",        R.drawable.ic_radar,         "#0277BD") { toggleRadar() },
            FabDef("METAR",        R.drawable.ic_metar,         "#1B5E20") { loadMetarsForVisibleArea(); toast("Loading METARs…") },
            FabDef("Aircraft",     R.drawable.ic_aircraft,      "#1565C0") { toggleAircraftFromFab() },
            FabDef("Search Here",  R.drawable.ic_search,        "#4A148C") { searchFromCurrentLocation() },
            FabDef("Weather",      R.drawable.ic_wx_default,    "#006064") { showWeatherDialog() },
            FabDef("GPS / Manual", R.drawable.ic_gps,           "#37474F") { toggleLocationMode() },
            FabDef("Debug Log",    R.drawable.ic_debug,         "#424242") { startActivity(Intent(this, DebugLogActivity::class.java)) }
        )

        val container = binding.fabMenu
        container.removeAllViews()

        defs.forEach { def ->
            val row = layoutInflater.inflate(R.layout.fab_menu_item, container, false)
            val iv = row.findViewById<ImageView>(R.id.fabItemIcon)
            val d = ContextCompat.getDrawable(this, def.iconRes)!!.mutate()
            DrawableCompat.setTint(d, Color.parseColor(def.color))
            iv.setImageDrawable(d)
            row.findViewById<TextView>(R.id.fabItemLabel).text = def.label
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
            row.layoutParams = lp
            row.setOnClickListener { closeFabMenu(); def.action() }
            container.addView(row)
            DebugLogger.d("MainActivity", "  FAB row: '${def.label}'")
        }

        DebugLogger.i("MainActivity", "FAB container built — ${container.childCount} rows")
        updateFabTriggerIcon(open = false)
        binding.fabMain.setOnClickListener {
            DebugLogger.d("MainActivity", "FAB tapped — fabMenuOpen=$fabMenuOpen")
            if (fabMenuOpen) closeFabMenu() else openFabMenu()
        }
        closeFabMenu()
    }

    internal fun updateFabTriggerIcon(open: Boolean) {
        val res = if (open) android.R.drawable.ic_menu_close_clear_cancel else R.drawable.ic_radar
        val icon = ContextCompat.getDrawable(this, res)!!.mutate()
        DrawableCompat.setTint(icon, Color.WHITE)
        binding.fabMain.setImageDrawable(icon)
    }

    internal fun openFabMenu()  { fabMenuOpen = true;  binding.fabMenu.visibility = View.VISIBLE; updateFabTriggerIcon(true) }
    internal fun closeFabMenu() { fabMenuOpen = false; binding.fabMenu.visibility = View.GONE;    updateFabTriggerIcon(false) }

    // =========================================================================
    // OBSERVERS
    // =========================================================================

    internal fun observeViewModel() {
        DebugLogger.i("MainActivity", "observeViewModel() — attaching all observers")

        viewModel.currentLocation.observe(this) { update ->
            val point = update.point
            val speedMph = update.speedMps?.let { it * 2.23694 }
            lastGpsSpeedMph = speedMph

            // ── 1. Adaptive GPS interval: fast → 10s, slow → 60s ──
            val desiredInterval = if ((speedMph ?: 0.0) > 20.0) 10_000L else 60_000L
            if (desiredInterval != currentGpsIntervalMs) {
                currentGpsIntervalMs = desiredInterval
                val minInterval = if (desiredInterval == 10_000L) 5_000L else 30_000L
                DebugLogger.i("MainActivity", "GPS interval → ${desiredInterval}ms (speed=${speedMph?.let { "%.1f".format(it) } ?: "?"}mph)")
                viewModel.restartLocationUpdates(desiredInterval, minInterval)
            }

            // ── 2. 100m dead zone — skip jitter when stationary ──
            if (!initialCenterDone) {
                // First fix always processes — fall through
            } else if (distanceBetween(lastGpsPoint, point) < 100f) {
                DebugLogger.d("MainActivity", "GPS jitter <100m — skipped")
                // Still handle deferred restores even during jitter
                handleDeferredRestores(point)

                // ── Update status line with GPS position ──
                updateIdleStatusLine(point.latitude, point.longitude, speedMph)

                // ── Idle auto-populate: start full scanner after 5 min stationary ──
                val idleMs = System.currentTimeMillis() - lastSignificantMoveTime
                if (idleMs > 600_000L
                    && idlePopulateJob?.isActive != true
                    && populateJob == null
                    && followedVehicleId == null && followedAircraftIcao == null
                    && (speedMph ?: 0.0) <= 20.0
                ) {
                    lifecycleScope.launch {
                        val nearbyCount = findViewModel.fetchNearbyPoiCount(point.latitude, point.longitude)
                        if (nearbyCount >= 100) {
                            DebugLogger.i("MainActivity", "Idle ${idleMs / 1000}s — skipping idle auto-populate: $nearbyCount POIs within 10km (≥100)")
                        } else {
                            DebugLogger.i("MainActivity", "Idle ${idleMs / 1000}s — starting idle auto-populate at GPS ${point.latitude},${point.longitude} ($nearbyCount POIs within 10km)")
                            startIdlePopulate(point)
                        }
                    }
                }

                return@observe
            }

            // ── 3. Update GPS marker ──
            updateGpsMarker(point)
            lastGpsPoint = point

            // ── 3b. Significant move — reset idle timer and cancel idle populate ──
            lastSignificantMoveTime = System.currentTimeMillis()
            if (idlePopulateJob?.isActive == true) {
                DebugLogger.i("MainActivity", "GPS moved >100m — stopping idle auto-populate")
                stopIdlePopulate(clearState = true)
            }
            idlePopulateState = null  // discard saved state — user has moved

            // ── 4. Initial center — first fix only ──
            if (followedVehicleId == null && !initialCenterDone) {
                initialCenterDone = true
                lastPoiFetchPoint = point
                lastApiCallTime = System.currentTimeMillis()
                DebugLogger.i("MainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — initial center zoom=18")
                binding.mapView.controller.animateTo(point)
                binding.mapView.controller.setZoom(18.0)
            } else {
                DebugLogger.d("MainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} (speed=${speedMph?.let { "%.1f".format(it) } ?: "?"}mph)")
            }

            // ── 5. Weather auto-fetch — on first fix + every 30 min ──
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastWeatherFetchTime > WEATHER_FETCH_INTERVAL_MS) {
                lastWeatherFetchTime = nowMs
                weatherViewModel.fetchWeather(point.latitude, point.longitude)
            }

            // ── 5b. Geofence check — if TFRs loaded, check user position ──
            if (geofenceViewModel.geofenceEngine.getLoadedZoneCount() > 0) {
                val bearing = update.bearing?.toDouble()
                geofenceViewModel.checkGeofences(point.latitude, point.longitude, null, bearing)
            }

            // ── 5c. Update status line with GPS position ──
            updateIdleStatusLine(point.latitude, point.longitude, speedMph)

            // ── 6. Deferred restores — always fire regardless of speed ──
            handleDeferredRestores(point)

            // ── 7. Fast speed guard — no API calls when driving ──
            if ((speedMph ?: 0.0) > 20.0) {
                DebugLogger.d("MainActivity", "Speed >20mph — skipping API calls")
                return@observe
            }

            // ── 8. 3km POI threshold + 1-min cooldown ──
            val now = System.currentTimeMillis()
            val distFromLastFetch = distanceBetween(lastPoiFetchPoint, point)
            if (distFromLastFetch > 3000f && (now - lastApiCallTime) > 60_000L) {
                DebugLogger.i("MainActivity", "GPS moved ${distFromLastFetch.toInt()}m from last POI fetch — refreshing layers")
                lastPoiFetchPoint = point
                lastApiCallTime = now
                if (followedVehicleId == null) {
                    binding.mapView.controller.animateTo(point)
                }
                binding.mapView.postDelayed({ loadCachedPoisForVisibleArea() }, 500)
                scheduleSilentFill(point, 2000)
            }
        }
        viewModel.poiClusters.observe(this) { clusters ->
            if (clusters != null) {
                DebugLogger.i("MainActivity", "poiClusters → ${clusters.size} clusters")
                renderPoiClusters(clusters)
            } else {
                clearClusterMarkers()
            }
        }
        viewModel.places.observe(this) { (layerId, places) ->
            DebugLogger.i("MainActivity", "places → ${places.size} results layerId=$layerId")
            if (layerId == "bbox") {
                if (places.size > 5000) {
                    DebugLogger.i("MainActivity", "POI count ${places.size} > 5000 — suppressing display")
                    replaceAllPoiMarkers(emptyList())
                    return@observe
                }
                // Clear cluster markers when showing individual POIs
                clearClusterMarkers()
                // Viewport bbox fetch — replace ALL POI markers with only visible results
                replaceAllPoiMarkers(places)
            } else {
                // User-initiated search or category restore — data goes to proxy cache.
                // Trigger a bbox refresh so the newly cached POIs appear on screen.
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(2000)
                    loadCachedPoisForVisibleArea()
                }
            }
        }
        weatherViewModel.metars.observe(this) { metars ->
            DebugLogger.i("MainActivity", "metars → ${metars.size} stations")
            clearMetarMarkers(); metars.forEach { addMetarMarker(it) }
            bringStationMarkersToFront()
        }
        weatherViewModel.weatherAlerts.observe(this) { alerts ->
            DebugLogger.i("MainActivity", "weatherAlerts → ${alerts.size} alerts")
            appBarMenuManager.updateAlertBadge(alertsBadgeView, alerts.size)
            if (alerts.isNotEmpty()) toast("${alerts.size} weather alert(s) active")
        }
        weatherViewModel.weatherData.observe(this) { data ->
            DebugLogger.i("MainActivity", "weatherData → ${data?.location?.city ?: "null"}")
            updateWeatherToolbarIcon(data)
            // Refresh idle status line with latest weather info
            lastGpsPoint?.let { pt -> updateIdleStatusLine(pt.latitude, pt.longitude, lastGpsSpeedMph) }
        }
        weatherViewModel.radarRefreshTick.observe(this) {
            DebugLogger.i("MainActivity", "radarRefreshTick → refreshing overlay")
            refreshRadarOverlay()
        }
        weatherViewModel.webcams.observe(this) { webcams ->
            DebugLogger.i("MainActivity", "webcams → ${webcams.size} on map")
            clearWebcamMarkers()
            addWebcamMarkersBatched(webcams)
        }
        viewModel.error.observe(this) { msg ->
            DebugLogger.e("MainActivity", "VM error: $msg")
            toast(msg)
        }
        aircraftViewModel.aircraft.observe(this) { aircraft ->
            DebugLogger.i("MainActivity", "aircraft → ${aircraft.size} on map")
            clearAircraftMarkers()
            // Merge followed aircraft into list if it's outside the bbox results
            val icao = followedAircraftIcao
            val merged = if (icao != null && aircraft.none { it.icao24 == icao }) {
                val followed = lastFollowedAircraftState
                if (followed != null) aircraft + followed else aircraft
            } else aircraft
            merged.forEach { addAircraftMarker(it) }
            // Emergency squawk detection — alert once per icao24 per session
            merged.filter { isEmergencySquawk(it.squawk) && emSquawkAlerted.add(it.icao24) }
                .forEach { s ->
                    val callsign = s.callsign?.ifBlank { null } ?: s.icao24
                    val altFt = s.baroAltitude?.let { "%.0f ft".format(it * 3.28084) } ?: "—"
                    toast("⚠ ${squawkLabel(s.squawk!!)} — $callsign squawk ${s.squawk} at $altFt")
                    DebugLogger.i("MainActivity", "EMERGENCY SQUAWK ${s.squawk} (${squawkLabel(s.squawk!!)}) — $callsign icao=${s.icao24} alt=$altFt")
                }
            updateFollowedAircraft(merged)
        }
        aircraftViewModel.followedAircraft.observe(this) { state ->
            if (state == null) {
                if (followedAircraftIcao != null) {
                    followedAircraftFailCount++
                    DebugLogger.i("MainActivity", "Followed aircraft ${followedAircraftIcao} null response — failCount=$followedAircraftFailCount")
                    if (followedAircraftFailCount < 3) {
                        // Tolerate transient failures (429 rate limits, network blips)
                        DebugLogger.i("MainActivity", "Tolerating failure $followedAircraftFailCount/3 — keeping lock")
                        return@observe
                    }
                    DebugLogger.i("MainActivity", "Followed aircraft ${followedAircraftIcao} lost after $followedAircraftFailCount failures")
                    stopFollowing()
                    if (autoFollowAircraftJob?.isActive == true) {
                        DebugLogger.i("MainActivity", "Auto-follow active — picking replacement aircraft")
                        toast("Aircraft lost — picking another…")
                        pickAndFollowRandomAircraft()
                    } else {
                        toast("Aircraft lost from feed")
                    }
                }
                return@observe
            }
            followedAircraftFailCount = 0  // reset on successful update
            val prevState = lastFollowedAircraftState
            lastFollowedAircraftState = state
            // Always update the banner (altitude/speed/heading change even if position doesn't)
            showAircraftFollowBanner(state)
            // Skip heavy work (animation, POI prefetch, trail, geofence) if position hasn't changed
            val moved = prevState == null || state.lat != prevState.lat || state.lon != prevState.lon
            if (moved) {
                DebugLogger.i("MainActivity", "Followed aircraft update: ${state.callsign ?: state.icao24} at ${state.lat},${state.lon}")
                binding.mapView.controller.animateTo(state.toGeoPoint())
                // Geofence check for followed aircraft (with altitude)
                if (geofenceViewModel.geofenceEngine.getLoadedZoneCount() > 0) {
                    val altFt = state.baroAltitude?.let { it * 3.28084 }
                    geofenceViewModel.checkGeofences(state.lat, state.lon, altFt, state.track)
                }
                // Grow the flight trail with the new position
                appendToFlightTrail(state)
                // Pre-fill POI cache at aircraft's current position
                val point = state.toGeoPoint()
                DebugLogger.i("MainActivity", "Aircraft follow POI prefetch at ${point.latitude},${point.longitude}")
                viewModel.searchPoisAt(point)
            }
            // Refresh full cache display after prefetch; check for empty POI zone (auto-follow)
            binding.mapView.postDelayed({
                if (moved) loadCachedPoisForVisibleArea()
                // If auto-follow is active, check altitude and boundary bounce
                if (autoFollowAircraftJob?.isActive == true) {
                    val lat = state.lat
                    val lon = state.lon
                    // Bounce margins — flip direction before aircraft leaves CONUS
                    val WEST_MARGIN = -120.0
                    val EAST_MARGIN = -70.0
                    val NORTH_MARGIN = 47.0
                    val SOUTH_MARGIN = 27.0
                    var bounced = false
                    // East/West bounce
                    if (lon < WEST_MARGIN && autoFollowPreferWest) {
                        autoFollowPreferWest = false
                        bounced = true
                        DebugLogger.i("MainActivity", "Auto-follow: hit west margin (lon=$lon) — flipping to eastbound")
                        toast("West coast — switching to eastbound")
                    } else if (lon > EAST_MARGIN && !autoFollowPreferWest) {
                        autoFollowPreferWest = true
                        bounced = true
                        DebugLogger.i("MainActivity", "Auto-follow: hit east margin (lon=$lon) — flipping to westbound")
                        toast("East coast — switching to westbound")
                    }
                    // North/South bounce
                    if (lat > NORTH_MARGIN && !autoFollowPreferSouth) {
                        autoFollowPreferSouth = true
                        bounced = true
                        DebugLogger.i("MainActivity", "Auto-follow: hit north margin (lat=$lat) — flipping to southbound")
                        toast("North border — switching to southbound")
                    } else if (lat < SOUTH_MARGIN && autoFollowPreferSouth) {
                        autoFollowPreferSouth = false
                        bounced = true
                        DebugLogger.i("MainActivity", "Auto-follow: hit south margin (lat=$lat) — flipping to northbound")
                        toast("South border — switching to northbound")
                    }
                    if (bounced) {
                        autoFollowEmptyPoiCount = 0
                        pickAndFollowRandomAircraft()
                        return@postDelayed
                    }
                    val altFt = (state.baroAltitude ?: 0.0) * 3.28084
                    if (altFt < 10000 && altFt > 0) {
                        DebugLogger.i("MainActivity", "Auto-follow: aircraft below 10,000 ft (${altFt.toInt()} ft) — switching")
                        toast("Aircraft descending (${altFt.toInt()} ft) — switching")
                        autoFollowEmptyPoiCount = 0
                        pickAndFollowRandomAircraft()
                        return@postDelayed
                    }
                    val poiCount = viewModel.places.value?.second?.size ?: 0
                    if (poiCount == 0) {
                        autoFollowEmptyPoiCount++
                        if (autoFollowEmptyPoiCount >= 2) {
                            DebugLogger.i("MainActivity", "Auto-follow: no POIs $autoFollowEmptyPoiCount times — switching")
                            toast("No POIs — switching aircraft")
                            autoFollowEmptyPoiCount = 0
                            pickAndFollowRandomAircraft()
                        }
                    } else {
                        autoFollowEmptyPoiCount = 0
                    }
                }
            }, 3000)
        }
        transitViewModel.mbtaTrains.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA trains update — ${vehicles.size} vehicles")
            clearTrainMarkers()
            if (transitMarkersVisible) vehicles.forEach { addTrainMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        transitViewModel.mbtaSubway.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA subway update — ${vehicles.size} vehicles")
            clearSubwayMarkers()
            if (transitMarkersVisible) vehicles.forEach { addSubwayMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        transitViewModel.mbtaBuses.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA buses update — ${vehicles.size} vehicles")
            clearBusMarkers()
            if (transitMarkersVisible) vehicles.forEach { addBusMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        transitViewModel.mbtaStations.observe(this) { stations ->
            DebugLogger.i("MainActivity", "MBTA stations update — ${stations.size} stations")
            clearStationMarkers()
            if (transitMarkersVisible && binding.mapView.zoomLevelDouble >= 12.0) {
                addStationMarkersBatched(stations)
            }
        }
        transitViewModel.mbtaBusStops.observe(this) { stops ->
            DebugLogger.i("MainActivity", "MBTA bus stops update — ${stops.size} total stops loaded")
            allBusStops = stops
            refreshBusStopMarkersForViewport()
        }
        geofenceViewModel.tfrZones.observe(this) { zones ->
            DebugLogger.i("MainActivity", "tfrZones → ${zones.size} TFRs")
            renderTfrOverlays(zones)
        }
        geofenceViewModel.cameraZones.observe(this) { zones ->
            DebugLogger.i("MainActivity", "cameraZones → ${zones.size}")
            renderCameraOverlays(zones)
        }
        geofenceViewModel.schoolZones.observe(this) { zones ->
            DebugLogger.i("MainActivity", "schoolZones → ${zones.size}")
            renderSchoolOverlays(zones)
        }
        geofenceViewModel.floodZones.observe(this) { zones ->
            DebugLogger.i("MainActivity", "floodZones → ${zones.size}")
            renderFloodOverlays(zones)
        }
        geofenceViewModel.crossingZones.observe(this) { zones ->
            DebugLogger.i("MainActivity", "crossingZones → ${zones.size}")
            renderCrossingOverlays(zones)
        }
        geofenceViewModel.databaseZones.observe(this) { zones ->
            DebugLogger.i("MainActivity", "databaseZones → ${zones.size}")
            renderDatabaseOverlays(zones)
        }
        geofenceViewModel.geofenceAlerts.observe(this) { alerts ->
            DebugLogger.i("MainActivity", "geofenceAlerts → ${alerts.size}")
            updateAlertsIcon(alerts)
            // Show banner for CRITICAL/EMERGENCY
            val critical = alerts.filter { it.severity.level >= com.example.locationmapapp.data.model.AlertSeverity.CRITICAL.level }
            if (critical.isNotEmpty()) {
                showGeofenceAlertBanner(critical.first())
            }
        }
        DebugLogger.i("MainActivity", "observeViewModel() complete — all observers attached")
    }


    // =========================================================================
    // SEARCH
    // =========================================================================

    internal fun searchFromCurrentLocation() {
        val loc = viewModel.currentLocation.value?.point ?: GeoPoint(42.3601, -71.0589)
        DebugLogger.i("MainActivity", "searchFromCurrentLocation() lat=${loc.latitude} lon=${loc.longitude}")
        viewModel.searchPoisAt(loc)
        toast("Searching POIs…")
    }

    internal fun triggerFullSearch(point: GeoPoint) {
        DebugLogger.i("MainActivity", "triggerFullSearch() lat=${point.latitude} lon=${point.longitude}")
        viewModel.searchPoisAt(point)
        toast("Searching around tapped location…")
    }

    // =========================================================================
    // MARKERS
    // =========================================================================

    internal fun updateGpsMarker(point: GeoPoint) {
        gpsMarker?.let { binding.mapView.overlays.remove(it) }
        gpsMarker = Marker(binding.mapView).apply {
            position = point
            icon     = MarkerIconHelper.forCategory(this@MainActivity, "gps", 32)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title    = "Current Location"
        }
        binding.mapView.overlays.add(gpsMarker!!)
        binding.mapView.invalidate()
    }

    /** Update the GPS idle status line with current position + weather info. */
    internal fun updateIdleStatusLine(lat: Double, lon: Double, speedMph: Double?) {
        val weather = weatherViewModel.weatherData.value
        val tempF = weather?.current?.temperature
        val desc = weather?.current?.description
        statusLineManager.updateIdle(lat, lon, speedMph, tempF, desc)
    }

    internal fun addPoiMarker(layerId: String, place: com.example.locationmapapp.data.model.PlaceResult) {
        val labeled = binding.mapView.zoomLevelDouble >= 16.0
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(place.lat, place.lon)
            icon = if (labeled) {
                MarkerIconHelper.labeledDot(this@MainActivity, place.category, place.name)
            } else {
                MarkerIconHelper.dot(this@MainActivity, place.category)
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = place.name
            snippet  = buildPlaceSnippet(place)
            relatedObject = place  // retained for icon refresh on zoom threshold
            setOnMarkerClickListener { _, _ ->
                openPoiDetailFromPlace(place)
                true
            }
        }
        poiMarkers.getOrPut(layerId) { mutableListOf() }.add(m)
        binding.mapView.overlays.add(m)
    }

    /** Convert a PlaceResult to FindResult and open the POI detail dialog. */
    internal fun openPoiDetailFromPlace(place: com.example.locationmapapp.data.model.PlaceResult) {
        val parts = place.id.split(":", limit = 2)
        val osmType = if (parts.size == 2) parts[0] else "node"
        val osmId = (if (parts.size == 2) parts[1] else parts[0]).toLongOrNull() ?: return
        val center = binding.mapView.mapCenter
        val distResults = FloatArray(1)
        android.location.Location.distanceBetween(center.latitude, center.longitude, place.lat, place.lon, distResults)
        val findResult = com.example.locationmapapp.data.model.FindResult(
            id = osmId,
            type = osmType,
            name = place.name,
            lat = place.lat,
            lon = place.lon,
            category = place.category,
            distanceM = distResults[0].toInt(),
            tags = emptyMap(),
            address = place.address,
            phone = place.phone,
            openingHours = place.openingHours
        )
        showPoiDetailDialog(findResult)
    }

    /** Swap all POI marker icons between dot and labeled-dot when crossing zoom 18. */
    internal fun refreshPoiMarkerIcons() {
        val labeled = binding.mapView.zoomLevelDouble >= 16.0
        DebugLogger.i("MainActivity", "refreshPoiMarkerIcons labeled=$labeled")
        poiMarkers.values.flatten().forEach { marker ->
            val place = marker.relatedObject as? com.example.locationmapapp.data.model.PlaceResult ?: return@forEach
            marker.icon = if (labeled) {
                MarkerIconHelper.labeledDot(this, place.category, place.name)
            } else {
                MarkerIconHelper.dot(this, place.category)
            }
        }
        binding.mapView.invalidate()
    }

    /** Swap all vehicle marker icons between plain arrow and labeled when crossing zoom 18. */
    internal fun refreshVehicleMarkerIcons() {
        val labeled = binding.mapView.zoomLevelDouble >= 16.0
        DebugLogger.i("MainActivity", "refreshVehicleMarkerIcons labeled=$labeled")
        fun refreshList(markers: List<Marker>, resId: Int, sizeDp: Int) {
            markers.forEach { marker ->
                val v = marker.relatedObject as? com.example.locationmapapp.data.model.MbtaVehicle ?: return@forEach
                val tint = vehicleRouteColor(v)
                val isStale = vehicleStalenessTag(v.updatedAt).isNotEmpty()
                if (labeled) {
                    val (icon, anchorY) = MarkerIconHelper.labeledVehicle(
                        this, resId, sizeDp, tint, v.bearing,
                        v.routeName, v.headsign, v.stopName,
                        v.currentStatus.display, v.speedDisplay, isStale
                    )
                    marker.icon = icon
                    marker.setAnchor(Marker.ANCHOR_CENTER, anchorY)
                } else {
                    marker.icon = MarkerIconHelper.withArrow(this, resId, sizeDp, tint, v.bearing)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
            }
        }
        refreshList(trainMarkers, R.drawable.ic_transit_rail, 30)
        refreshList(subwayMarkers, R.drawable.ic_transit_rail, 26)
        refreshList(busMarkers, R.drawable.ic_bus, 22)
        binding.mapView.invalidate()
    }

    /** Process deferred restore flags on GPS update (startup layer restoration). */
    internal fun handleDeferredRestores(point: GeoPoint) {
        if (pendingPoiRestore) {
            pendingPoiRestore = false
            DebugLogger.i("MainActivity", "POI restore — loading cached POIs for visible area")
            binding.mapView.postDelayed({ loadCachedPoisForVisibleArea() }, 1500)
            scheduleSilentFill(point, 3000)
        }
        if (pendingMetarRestore) {
            pendingMetarRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("MainActivity", "METAR restore triggered — loading for visible area")
                loadMetarsForVisibleArea()
            }, 1500)
        }
        if (pendingAircraftRestore) {
            pendingAircraftRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("MainActivity", "Aircraft restore triggered — starting refresh")
                startAircraftRefresh()
            }, 1500)
        }
        if (pendingAutoFollowRestore) {
            pendingAutoFollowRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("MainActivity", "Auto-follow restore triggered")
                startAutoFollowAircraft()
            }, 5000)
        }
        if (pendingCachedPoiLoad) {
            pendingCachedPoiLoad = false
            binding.mapView.postDelayed({
                DebugLogger.i("MainActivity", "Loading cached POIs for visible area")
                loadCachedPoisForVisibleArea()
            }, 2000)
            scheduleSilentFill(point, 4000)
        }
        if (pendingWebcamRestore) {
            pendingWebcamRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("MainActivity", "Webcam restore triggered — loading for visible area")
                loadWebcamsForVisibleArea()
            }, 2000)
        }
        if (pendingGeofenceRestore) {
            pendingGeofenceRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("MainActivity", "Geofence overlay restore triggered — loading for visible area")
                loadGeofenceZonesForVisibleArea()
            }, 2000)
        }
    }

    /** Ask the proxy for cached POIs within the visible map bounding box.
     *  Server returns clusters at wide zoom levels, individual POIs when zoomed in. */
    internal fun loadCachedPoisForVisibleArea() {
        val bb = binding.mapView.boundingBox
        viewModel.loadCachedPoisForBbox(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }



    internal fun clearPoiMarkers(layerId: String) {
        poiMarkers[layerId]?.forEach { binding.mapView.overlays.remove(it) }
        poiMarkers[layerId]?.clear()
        binding.mapView.invalidate()
    }

    /** Clear ALL POI markers from every layer at once. */
    internal fun clearAllPoiMarkers() {
        poiMarkers.values.forEach { list ->
            list.forEach { binding.mapView.overlays.remove(it) }
        }
        poiMarkers.clear()
    }

    /** Replace all POI markers with only the given viewport results. */
    internal fun replaceAllPoiMarkers(places: List<com.example.locationmapapp.data.model.PlaceResult>) {
        clearAllPoiMarkers()
        if (places.isEmpty()) {
            binding.mapView.invalidate()
            return
        }
        // Pre-generate icons on background thread to avoid ANR
        lifecycleScope.launch {
            val labeled = binding.mapView.zoomLevelDouble >= 16.0
            val iconData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                places.map { place ->
                    val icon = if (labeled) {
                        MarkerIconHelper.labeledDot(this@MainActivity, place.category, place.name)
                    } else {
                        MarkerIconHelper.dot(this@MainActivity, place.category)
                    }
                    Pair(place, icon)
                }
            }
            // Now on main thread — fast marker creation
            for ((place, icon) in iconData) {
                val m = Marker(binding.mapView).apply {
                    position = GeoPoint(place.lat, place.lon)
                    this.icon = icon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = place.name
                    snippet = buildPlaceSnippet(place)
                    relatedObject = place
                    setOnMarkerClickListener { _, _ ->
                        openPoiDetailFromPlace(place)
                        true
                    }
                }
                poiMarkers.getOrPut("bbox") { mutableListOf() }.add(m)
                binding.mapView.overlays.add(m)
            }
            bringStationMarkersToFront()
            binding.mapView.invalidate()
        }
    }

    /** Clear cluster markers from the map. */
    internal fun clearClusterMarkers() {
        clusterMarkers.forEach { binding.mapView.overlays.remove(it) }
        clusterMarkers.clear()
    }

    /** Render server-side POI clusters as sized, colored circle markers. */
    internal fun renderPoiClusters(clusters: List<com.example.locationmapapp.data.model.PoiCluster>) {
        clearClusterMarkers()
        clearAllPoiMarkers()
        // Pre-generate icons on background thread to avoid ANR, then add markers on main thread
        lifecycleScope.launch {
            val iconData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                clusters.map { cluster ->
                    val color = MarkerIconHelper.clusterTagColor(cluster.tag)
                    Triple(cluster, color, MarkerIconHelper.clusterIcon(this@MainActivity, cluster.count, color))
                }
            }
            // Now on main thread — fast marker creation + overlay add
            for ((cluster, _, icon) in iconData) {
                val m = org.osmdroid.views.overlay.Marker(binding.mapView).apply {
                    position = cluster.toGeoPoint()
                    this.icon = icon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "${cluster.count} POIs"
                    snippet = cluster.tag
                    setOnMarkerClickListener { _, _ ->
                        binding.mapView.controller.animateTo(cluster.toGeoPoint(), binding.mapView.zoomLevelDouble + 2.0, 500L)
                        true
                    }
                }
                clusterMarkers.add(m)
                binding.mapView.overlays.add(m)
            }
            bringStationMarkersToFront()
            binding.mapView.invalidate()
        }
    }

    /** Move station markers to the end of the overlay list so they draw on top and get taps first. */
    internal fun bringStationMarkersToFront() {
        if (stationMarkers.isEmpty()) return
        val overlays = binding.mapView.overlays
        stationMarkers.forEach { overlays.remove(it) }
        overlays.addAll(stationMarkers)
    }

    // =========================================================================
    // WEBCAMS
    // =========================================================================

    internal fun addWebcamMarker(webcam: com.example.locationmapapp.data.model.Webcam) {
        val m = Marker(binding.mapView).apply {
            position = webcam.toGeoPoint()
            icon     = MarkerIconHelper.forCategory(this@MainActivity, "camera", 20)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = webcam.title
            snippet  = webcam.categories.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
            relatedObject = webcam
            setOnMarkerClickListener { _, _ ->
                showWebcamPreviewDialog(webcam)
                true
            }
        }
        webcamMarkers.add(m)
        binding.mapView.overlays.add(m)
    }

    /** Add webcam markers with icon generation off main thread. */
    internal fun addWebcamMarkersBatched(webcams: List<com.example.locationmapapp.data.model.Webcam>) {
        if (webcams.isEmpty()) return
        lifecycleScope.launch {
            val icon = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                MarkerIconHelper.forCategory(this@MainActivity, "camera", 20)
            }
            for (webcam in webcams) {
                val m = Marker(binding.mapView).apply {
                    position = webcam.toGeoPoint()
                    this.icon = icon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = webcam.title
                    snippet = webcam.categories.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                    relatedObject = webcam
                    setOnMarkerClickListener { _, _ ->
                        showWebcamPreviewDialog(webcam)
                        true
                    }
                }
                webcamMarkers.add(m)
                binding.mapView.overlays.add(m)
            }
            bringStationMarkersToFront()
            binding.mapView.invalidate()
        }
    }

    internal fun clearWebcamMarkers() {
        webcamMarkers.forEach { binding.mapView.overlays.remove(it) }
        webcamMarkers.clear()
        binding.mapView.invalidate()
    }

    internal fun loadWebcamsForVisibleArea() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(MenuPrefs.PREF_WEBCAMS_ON, true)) return
        val bb = binding.mapView.boundingBox
        val cats = prefs.getStringSet(MenuPrefs.PREF_WEBCAM_CATEGORIES, setOf("traffic")) ?: setOf("traffic")
        if (cats.isEmpty()) return
        // Windy API returns 0 results for very small bboxes — enforce minimum ~0.5° span
        val centerLat = (bb.latNorth + bb.latSouth) / 2.0
        val centerLon = (bb.lonEast + bb.lonWest) / 2.0
        val halfLat = maxOf((bb.latNorth - bb.latSouth) / 2.0, 0.25)
        val halfLon = maxOf((bb.lonEast - bb.lonWest) / 2.0, 0.25)
        weatherViewModel.loadWebcams(centerLat - halfLat, centerLon - halfLon, centerLat + halfLat, centerLon + halfLon, cats.joinToString(","))
    }

    /** Debounced: reload webcams 500ms after user stops scrolling/zooming. */
    internal fun scheduleWebcamReload() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(MenuPrefs.PREF_WEBCAMS_ON, true)) return
        webcamReloadJob?.cancel()
        webcamReloadJob = lifecycleScope.launch {
            delay(500)
            loadWebcamsForVisibleArea()
        }
    }







    // =========================================================================
    // MENU EVENT LISTENER
    // =========================================================================

    // =========================================================================
    // MENU EVENT LISTENER — v1.5
    // Implements every MenuEventListener callback.
    // Live features delegate to ViewModel; stubs log + toast pending implementation.
    // =========================================================================

    internal val menuEventListenerImpl = object : MenuEventListener {

        // ── Radar (live) ──────────────────────────────────────────────────────

        override fun onRadarToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onRadarToggled: enabled=$enabled")
            toggleRadar()
            if (enabled) radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { weatherViewModel.refreshRadar() }
            else         radarScheduler.stop()
            refreshLayerBadge()
        }

        override fun onRadarVisibilityChanged(percent: Int) {
            DebugLogger.i("MainActivity", "onRadarVisibilityChanged: $percent%")
            radarAlphaPercent = percent
            if (radarTileOverlay != null) {
                // Rebuild overlay so the new alpha is applied at construction
                binding.mapView.overlays.remove(radarTileOverlay)
                radarTileOverlay = null
                addRadarOverlay()
            }
            binding.mapView.invalidate()
        }

        override fun onRadarFrequencyChanged(minutes: Int) {
            DebugLogger.i("MainActivity", "onRadarFrequencyChanged: ${minutes}min")
            radarScheduler.setInterval(minutes)
            toast("Radar refresh: every $minutes min")
        }

        override fun onRadarAnimateToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onRadarAnimateToggled: $enabled")
            if (enabled) startRadarAnimation() else stopRadarAnimation()
        }

        override fun onRadarAnimSpeedChanged(ms: Int) {
            DebugLogger.i("MainActivity", "onRadarAnimSpeedChanged: ${ms}ms")
            radarAnimSpeedMs = ms
            toast("Radar animation speed: ${ms}ms")
        }

        // ── Dark Mode ────────────────────────────────────────────────────────

        override fun onDarkModeToggled(dark: Boolean) {
            DebugLogger.i("MainActivity", "onDarkModeToggled: $dark")
            if (dark) {
                binding.mapView.setTileSource(buildDarkTileSource())
                toast("Dark mode enabled")
            } else {
                binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
                toast("Light mode enabled")
            }
            binding.mapView.invalidate()
        }

        // ── Weather ───────────────────────────────────────────────────────────

        override fun onWeatherRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onWeatherRequested")
            showWeatherDialog()
        }

        override fun onMetarDisplayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMetarDisplayToggled: $enabled")
            if (enabled) { loadMetarsForVisibleArea(); toast("Loading METARs…") }
            else          clearMetarMarkers()
            refreshLayerBadge()
        }

        override fun onMetarFrequencyChanged(minutes: Int) {
            DebugLogger.i("MainActivity", "onMetarFrequencyChanged: ${minutes}min")
            // STUB: wire to a periodic METAR refresh scheduler
            stub("metar_freq", minutes)
        }

        // ── Aircraft ─────────────────────────────────────────────────────────

        override fun onAircraftDisplayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onAircraftDisplayToggled: $enabled")
            if (enabled) {
                startAircraftRefresh()
                toast("Loading aircraft…")
            } else {
                stopAircraftRefresh()
                aircraftViewModel.clearAircraft()
                clearAircraftMarkers()
                if (followedAircraftIcao != null) stopFollowing()
                // Cancel auto-follow if aircraft layer turned off
                if (autoFollowAircraftJob?.isActive == true) {
                    autoFollowAircraftJob?.cancel()
                    autoFollowAircraftJob = null
                    val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, false).apply()
                    DebugLogger.i("MainActivity", "Auto-follow cancelled — aircraft layer turned off")
                }
            }
            refreshLayerBadge()
        }

        override fun onAircraftFrequencyChanged(seconds: Int) {
            DebugLogger.i("MainActivity", "onAircraftFrequencyChanged: ${seconds}s")
            aircraftRefreshIntervalSec = seconds
            if (aircraftRefreshJob?.isActive == true) {
                startAircraftRefresh()
            }
            toast("Aircraft refresh: every $seconds sec")
        }

        // ── Transit ───────────────────────────────────────────────────────────

        override fun onMbtaStationsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMbtaStationsToggled: $enabled")
            if (enabled) {
                transitViewModel.fetchMbtaStations()
                toast("Loading train stations…")
            } else {
                transitViewModel.clearMbtaStations()
                clearStationMarkers()
            }
        }

        override fun onMbtaBusStopsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMbtaBusStopsToggled: $enabled")
            if (enabled) {
                transitViewModel.fetchMbtaBusStops()
                toast("Loading bus stops…")
            } else {
                transitViewModel.clearMbtaBusStops()
                allBusStops = emptyList()
                clearBusStopMarkers()
            }
        }

        override fun onMbtaTrainsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMbtaTrainsToggled: $enabled")
            if (enabled) {
                startTrainRefresh()
            } else {
                stopTrainRefresh()
                transitViewModel.clearMbtaTrains()
                if (followedVehicleId != null) stopFollowing()
            }
            refreshLayerBadge()
        }

        override fun onMbtaTrainsFrequencyChanged(seconds: Int) {
            DebugLogger.i("MainActivity", "onMbtaTrainsFrequencyChanged: ${seconds}s")
            trainRefreshIntervalSec = seconds
            // Restart refresh loop with new interval if currently active
            if (trainRefreshJob?.isActive == true) {
                startTrainRefresh()
            }
        }

        override fun onMbtaSubwayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMbtaSubwayToggled: $enabled")
            if (enabled) {
                startSubwayRefresh()
            } else {
                stopSubwayRefresh()
                transitViewModel.clearMbtaSubway()
                if (followedVehicleId != null) stopFollowing()
            }
            refreshLayerBadge()
        }

        override fun onMbtaSubwayFrequencyChanged(seconds: Int) {
            DebugLogger.i("MainActivity", "onMbtaSubwayFrequencyChanged: ${seconds}s")
            subwayRefreshIntervalSec = seconds
            if (subwayRefreshJob?.isActive == true) {
                startSubwayRefresh()
            }
        }

        override fun onMbtaBusesToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMbtaBusesToggled: $enabled")
            if (enabled) {
                startBusRefresh()
            } else {
                stopBusRefresh()
                transitViewModel.clearMbtaBuses()
                if (followedVehicleId != null) stopFollowing()
            }
            refreshLayerBadge()
        }

        override fun onMbtaBusesFrequencyChanged(seconds: Int) {
            DebugLogger.i("MainActivity", "onMbtaBusesFrequencyChanged: ${seconds}s")
            busRefreshIntervalSec = seconds
            if (busRefreshJob?.isActive == true) {
                startBusRefresh()
            }
        }

        override fun onNationalAlertsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onNationalAlertsToggled: $enabled")
            // STUB: parse https://alerts.weather.gov/cap/ma.php?x=1 (CAP/XML)
            stub("national_alerts", enabled)
        }

        override fun onNationalAlertsFrequencyChanged(minutes: Int) {
            DebugLogger.i("MainActivity", "onNationalAlertsFrequencyChanged: ${minutes}min")
            stub("national_alerts_freq", minutes)
        }

        // ── Cameras ───────────────────────────────────────────────────────────

        override fun onWebcamToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onWebcamToggled: $enabled")
            if (enabled) {
                loadWebcamsForVisibleArea()
                toast("Loading webcams…")
            } else {
                webcamReloadJob?.cancel()
                weatherViewModel.clearWebcams()
                clearWebcamMarkers()
            }
            refreshLayerBadge()
        }

        override fun onWebcamCategoriesChanged(categories: Set<String>) {
            DebugLogger.i("MainActivity", "onWebcamCategoriesChanged: $categories")
            val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
            if (prefs.getBoolean(MenuPrefs.PREF_WEBCAMS_ON, true)) {
                if (categories.isEmpty()) {
                    weatherViewModel.clearWebcams()
                    clearWebcamMarkers()
                } else {
                    loadWebcamsForVisibleArea()
                }
            }
        }

        // ── POI (partially live) ──────────────────────────────────────────────

        override fun onPoiLayerToggled(layerId: String, enabled: Boolean) {
            DebugLogger.i("MainActivity", "onPoiLayerToggled: layerId=$layerId enabled=$enabled")
            if (!enabled) {
                // Markers are now viewport-driven via bbox — toggle off just stops searching
                return
            }
            val cat = PoiCategories.find(layerId)
            if (cat == null) {
                stub("poi_unknown:$layerId", enabled)
                return
            }
            val tags = appBarMenuManager.getActiveTags(layerId)
            val loc = viewModel.currentLocation.value?.point ?: GeoPoint(42.3601, -71.0589)
            viewModel.searchPoisAt(loc, tags, layerId)
            toast("Loading ${cat.label}…")
        }

        // ── Utility ───────────────────────────────────────────────────────────

        override fun onGpsRecordingToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onGpsRecordingToggled: $enabled")
            // STUB: start/stop writing GeoPoints to local Room DB or GPX file
            stub("record_gps", enabled)
        }

        override fun onBuildStoryRequested() {
            DebugLogger.i("MainActivity", "onBuildStoryRequested")
            // STUB: package stored GPS points and launch URL in browser
            stub("build_story")
        }

        override fun onAnalyzeTodayRequested() {
            DebugLogger.i("MainActivity", "onAnalyzeTodayRequested")
            // STUB: launch URL / WebView with today's travel analysis
            stub("analyze_today")
        }

        override fun onTravelAnomaliesRequested() {
            DebugLogger.i("MainActivity", "onTravelAnomaliesRequested")
            // STUB: launch URL / WebView with anomaly detection report
            stub("travel_anomalies")
        }

        override fun onEmailGpxRequested() {
            DebugLogger.i("MainActivity", "onEmailGpxRequested")
            // STUB: serialize stored track to GPX, fire ACTION_SEND intent
            stub("email_gpx")
        }

        override fun onDebugLogRequested() {
            DebugLogger.i("MainActivity", "onDebugLogRequested")
            startActivity(Intent(this@MainActivity, DebugLogActivity::class.java))
        }

        override fun onAutoFollowAircraftToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onAutoFollowAircraftToggled: $enabled")
            if (enabled) startAutoFollowAircraft() else stopAutoFollowAircraft()
        }

        override fun onPopulatePoisToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onPopulatePoisToggled: $enabled")
            if (enabled) startPopulatePois() else stopPopulatePois()
        }

        override fun onProbe10kmRequested() {
            if (probe10kmJob?.isActive == true) {
                DebugLogger.i("MainActivity", "onProbe10kmRequested — stopping")
                stopProbe10km()
            } else {
                DebugLogger.i("MainActivity", "onProbe10kmRequested — starting")
                startProbe10km()
            }
        }

        override fun onFillProbeRequested() {
            DebugLogger.i("MainActivity", "onFillProbeRequested — stub")
            toast("Fill Probe Populate — coming soon")
        }

        override fun onSilentFillDebugToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onSilentFillDebugToggled: $enabled")
            // Toggle only controls banner visibility — silent fill always runs
            if (!enabled) hideSilentFillBanner()
        }

        override fun onGpsModeToggled(autoGps: Boolean) {
            DebugLogger.i("MainActivity", "onGpsModeToggled: autoGps=$autoGps")
            toggleLocationMode()
        }

        // ── Alerts / Geofence ─────────────────────────────────────────────────

        override fun onAlertsRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onAlertsRequested")
            val anchor = alertsIconView ?: binding.toolbar
            appBarMenuManager.showAlertsMenu(anchor)
        }

        override fun onTfrOverlayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onTfrOverlayToggled: $enabled")
            if (enabled) {
                loadTfrsForVisibleArea()
                toast("Loading TFRs…")
            } else {
                geofenceViewModel.clearZoneType(com.example.locationmapapp.data.model.ZoneType.TFR)
                clearTfrOverlays()
            }
            refreshLayerBadge()
        }

        override fun onCameraOverlayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onCameraOverlayToggled: $enabled")
            if (enabled) {
                val bb = binding.mapView.boundingBox
                geofenceViewModel.loadCameras(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
                toast("Loading speed cameras…")
            } else {
                geofenceViewModel.clearZoneType(com.example.locationmapapp.data.model.ZoneType.SPEED_CAMERA)
                clearCameraOverlays()
            }
            refreshLayerBadge()
        }

        override fun onSchoolOverlayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onSchoolOverlayToggled: $enabled")
            if (enabled) {
                val bb = binding.mapView.boundingBox
                geofenceViewModel.loadSchools(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
                toast("Loading school zones…")
            } else {
                geofenceViewModel.clearZoneType(com.example.locationmapapp.data.model.ZoneType.SCHOOL_ZONE)
                clearSchoolOverlays()
            }
        }

        override fun onFloodOverlayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onFloodOverlayToggled: $enabled")
            if (enabled) {
                val bb = binding.mapView.boundingBox
                geofenceViewModel.loadFloodZones(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
                toast("Loading flood zones…")
            } else {
                geofenceViewModel.clearZoneType(com.example.locationmapapp.data.model.ZoneType.FLOOD_ZONE)
                clearFloodOverlays()
            }
        }

        override fun onCrossingOverlayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onCrossingOverlayToggled: $enabled")
            if (enabled) {
                val bb = binding.mapView.boundingBox
                geofenceViewModel.loadCrossings(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
                toast("Loading railroad crossings…")
            } else {
                geofenceViewModel.clearZoneType(com.example.locationmapapp.data.model.ZoneType.RAILROAD_CROSSING)
                clearCrossingOverlays()
            }
        }

        override fun onAlertSoundToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onAlertSoundToggled: $enabled")
            toast(if (enabled) "Alert sounds enabled" else "Alert sounds disabled")
        }

        override fun onAlertDistanceChanged(nm: Int) {
            DebugLogger.i("MainActivity", "onAlertDistanceChanged: ${nm}nm")
            geofenceViewModel.geofenceEngine.proximityThresholdNm = nm.toDouble()
            toast("Alert distance: $nm NM")
        }

        override fun onDatabaseManagerRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onDatabaseManagerRequested")
            showDatabaseManagerDialog()
        }

        // ── Find / Legend ─────────────────────────────────────────────────────

        override fun onFindRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onFindRequested")
            showFindDialog()
        }

        override fun onLegendRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onLegendRequested")
            showLegendDialog()
        }

        override fun onGoToLocationRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onGoToLocationRequested")
            showGoToLocationDialog()
        }

        // ── Social ────────────────────────────────────────────────────────────

        override fun onSocialRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onSocialRequested")
            if (socialViewModel.isLoggedIn()) showProfileDialog() else showAuthDialog()
        }

        override fun onChatRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onChatRequested")
            if (!socialViewModel.isLoggedIn()) {
                toast("Register first to use Chat")
                showAuthDialog()
            } else {
                showChatDialog()
            }
        }

        override fun onProfileRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onProfileRequested")
            if (socialViewModel.isLoggedIn()) showProfileDialog() else showAuthDialog()
        }

        // ── Toolbar actions ───────────────────────────────────────────────────

        override fun onHomeRequested() {
            resetIdleTimer()
            val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, MODE_PRIVATE)
            if (prefs.getBoolean(MenuPrefs.PREF_HOME_SET, false)) {
                val lat = prefs.getFloat(MenuPrefs.PREF_HOME_LAT, 0f).toDouble()
                val lon = prefs.getFloat(MenuPrefs.PREF_HOME_LON, 0f).toDouble()
                DebugLogger.i("MainActivity", "onHomeRequested — centering on saved home ($lat,$lon)")
                binding.mapView.controller.animateTo(org.osmdroid.util.GeoPoint(lat, lon), 18.0, 800L)
            } else {
                DebugLogger.i("MainActivity", "onHomeRequested — centering on GPS (no home set)")
                viewModel.currentLocation.value?.point?.let { gps ->
                    binding.mapView.controller.animateTo(gps, 18.0, 800L)
                } ?: toast("No GPS fix yet — long-press Home to set")
            }
        }

        override fun onHomeLongPressed(setting: Boolean) {
            resetIdleTimer()
            if (setting) {
                val center = binding.mapView.mapCenter
                val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, MODE_PRIVATE)
                prefs.edit()
                    .putFloat(MenuPrefs.PREF_HOME_LAT, center.latitude.toFloat())
                    .putFloat(MenuPrefs.PREF_HOME_LON, center.longitude.toFloat())
                    .putBoolean(MenuPrefs.PREF_HOME_SET, true)
                    .apply()
                // Tint home icon teal
                binding.root.findViewById<android.widget.ImageView>(R.id.toolbarHomeIcon)
                    ?.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4DB6AC"))
                DebugLogger.i("MainActivity", "Home set to ${center.latitude},${center.longitude}")
                toast("Home location saved")
            } else {
                // Already cleared in AppBarMenuManager
                DebugLogger.i("MainActivity", "Home location cleared")
                toast("Home location cleared")
            }
        }

        override fun onAboutRequested() {
            resetIdleTimer()
            DebugLogger.i("MainActivity", "onAboutRequested")
            showAboutDialog()
        }

        // ── Safety net ────────────────────────────────────────────────────────

        override fun onStubAction(featureId: String) {
            DebugLogger.w("MenuStub", "onStubAction — UNRECOGNISED id: $featureId (this is a bug)")
            toast("[$featureId] — unrecognised action")
        }

        // ── Stub helper (keeps the above tidy) ───────────────────────────────

        private fun stub(feature: String, value: Any? = null) {
            val msg = if (value != null) "$feature=$value" else feature
            DebugLogger.i("MenuStub", "Stub: $msg — coming soon")
            toast("[$msg] — coming soon")
        }
    }



}
