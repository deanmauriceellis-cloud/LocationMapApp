/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

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
import com.example.wickedsalemwitchcitytour.R
import com.example.locationmapapp.data.model.GeocodeSuggestion
import com.example.wickedsalemwitchcitytour.ui.menu.PoiCategories
import com.example.locationmapapp.ui.menu.PoiLayerId
import com.example.wickedsalemwitchcitytour.databinding.ActivityMainBinding
import com.example.wickedsalemwitchcitytour.ui.menu.AppBarMenuManager
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.ui.menu.MenuEventListener
import com.example.wickedsalemwitchcitytour.ui.radar.RadarRefreshScheduler
import com.example.wickedsalemwitchcitytour.util.DebugEndpoints
import com.example.wickedsalemwitchcitytour.util.DebugHttpServer
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.userdata.GpsTrackRecorder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import androidx.preference.PreferenceManager

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SalemMainActivity.kt"

@AndroidEntryPoint
class SalemMainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding
    internal val viewModel: MainViewModel by viewModels()
    internal val socialViewModel: SocialViewModel by viewModels()
    internal val transitViewModel: TransitViewModel by viewModels()
    internal val aircraftViewModel: AircraftViewModel by viewModels()
    internal val findViewModel: FindViewModel by viewModels()
    internal val weatherViewModel: WeatherViewModel by viewModels()
    internal val geofenceViewModel: GeofenceViewModel by viewModels()
    internal val tourViewModel: TourViewModel by viewModels()
    internal val eventsViewModel: EventsViewModel by viewModels()

    internal lateinit var appBarMenuManager: AppBarMenuManager
    internal val radarScheduler = RadarRefreshScheduler()
    internal lateinit var favoritesManager: com.example.locationmapapp.util.FavoritesManager

    /**
     * GPS Journey recorder (Phase 9T+, S109). Field-injected by Hilt.
     * Records every GPS fix the activity sees into [com.example.wickedsalemwitchcitytour.userdata.db.UserDataDatabase]
     * with a 24h rolling retention. Recording is always-on while the activity is alive (Option B);
     * the bottom-left "GPS" FAB controls only whether the path polyline is displayed on the map.
     */
    @Inject
    internal lateinit var gpsTrackRecorder: GpsTrackRecorder

    /**
     * POI proximity encounter tracker (S110). Field-injected by Hilt.
     * Tracks every time the user comes within ~100m of a narration POI: opens
     * a row, updates min/max distance + duration + fix count on each subsequent
     * fix, and finalizes when the POI leaves the proximity radius. The data is
     * persisted in [com.example.wickedsalemwitchcitytour.userdata.db.UserDataDatabase]
     * for after-the-fact review.
     */
    @Inject
    internal lateinit var poiEncounterTracker: com.example.wickedsalemwitchcitytour.userdata.PoiEncounterTracker

    /**
     * Magenta polyline that paints the user's recent GPS journey on the map.
     * Lazy-created in [initGpsTrackOverlay] during onCreate. Lives in the activity
     * scope; not shared with any other module.
     */
    internal var gpsTrackOverlay: Polyline? = null

    // Phase 9T: Narration system
    /**
     * Singleton (Hilt) — survives Activity recreation so the dedup state
     * (`narratedAt`, `cooldowns`) doesn't reset on orientation change.
     * S110 lift; see NarrationGeofenceManager class docs.
     */
    @Inject
    internal lateinit var narrationGeofenceManager: com.example.wickedsalemwitchcitytour.tour.NarrationGeofenceManager
    internal var corridorManager: com.example.wickedsalemwitchcitytour.tour.CorridorGeofenceManager? = null
    internal var proximityDock: ProximityDock? = null

    /**
     * Wall-clock timestamp (System.currentTimeMillis) of the most recent
     * `viewModel.currentLocation` emission. 0 means we have never seen a fix.
     *
     * Used by the narration silence reach-out (SalemMainActivityNarration.kt) and
     * by the GPS-OBS heartbeat coroutine (started in observeViewModel) to detect
     * stale-fix conditions. The reach-out is suppressed when this is older than
     * GPS_STALE_THRESHOLD_MS, which is the S110 fix for the runaway loop where
     * the app cycled through nearby Salem POIs at a 15-minute-old position.
     */
    internal var lastFixAtMs: Long = 0L

    /** Treat the location stream as stale after this much silence (ms). */
    internal val GPS_STALE_THRESHOLD_MS = 30_000L

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
            DebugLogger.i("SalemMainActivity", "UI activity — stopping idle auto-populate")
            stopIdlePopulate()
        }
    }
    internal var idlePopulateState: IdlePopulateState? = null

    // Zoom toggle (x1/x2/x3 quick zoom)
    internal var zoomToggleLevel: Int = 0  // 0=x1, 1=x2, 2=x3

    // Walk simulator
    internal var walkSimJob: kotlinx.coroutines.Job? = null
    internal var walkSimRunning: Boolean = false

    // Show-all-POIs debug toggle
    internal var showAllPoisActive: Boolean = false

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
            DebugLogger.d("SalemMainActivity", "Aircraft skipped — zoom ${zoom.toInt()} < 10")
            return
        }
        val bb = binding.mapView.boundingBox
        DebugLogger.i("SalemMainActivity", "loadAircraftForVisibleArea zoom=${zoom.toInt()} bbox=${bb.latSouth},${bb.lonWest},${bb.latNorth},${bb.lonEast}")
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
        DebugLogger.i("SalemMainActivity", "Location permission result: granted=$granted")
        if (granted) {
            startGpsUpdatesAndCenter()
        } else {
            DebugLogger.w("SalemMainActivity", "Location permission denied — staying at default center")
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
        DebugLogger.i("SalemMainActivity", "onCreate() start — device=${android.os.Build.MODEL} SDK=${android.os.Build.VERSION.SDK_INT} savedInstanceState=${savedInstanceState != null}")
        // S110 — lifecycle observer for diagnostic logging. Helps post-mortems
        // distinguish "process death" (no graceful onPause / onDestroy logs)
        // from "configuration change" (changingConfigurations=true) from
        // "user backgrounded the app" (changingConfigurations=false).
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                DebugLogger.i("LIFECYCLE", "onResume")
            }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                DebugLogger.i("LIFECYCLE", "onPause changingConfig=${this@SalemMainActivity.isChangingConfigurations} finishing=${this@SalemMainActivity.isFinishing}")
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                DebugLogger.i("LIFECYCLE", "onStop")
            }
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                DebugLogger.i("LIFECYCLE", "onDestroy changingConfig=${this@SalemMainActivity.isChangingConfigurations} finishing=${this@SalemMainActivity.isFinishing}")
            }
        })
        DebugHttpServer.start()

        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = "WickedSalemWitchCityTour/1.0 (Android)"
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Toolbar (slim layout) ─────────────────────────────────────────
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        DebugLogger.i("SalemMainActivity", "setSupportActionBar complete — title hidden for slim toolbar")

        // ── Menu manager — slim toolbar with 3 icons + grid dropdown ──────
        appBarMenuManager = AppBarMenuManager(
            context           = this,
            toolbar           = binding.toolbar,
            menuEventListener = menuEventListenerImpl
        )
        val toolbarRefs = appBarMenuManager.setupSlimToolbar(
            weatherIcon    = binding.root.findViewById(R.id.toolbarWeatherIcon),
            alertsIcon     = binding.root.findViewById(R.id.toolbarAlertsIcon),
            gridButton     = binding.root.findViewById(R.id.toolbarGridButton),
            statusLine     = binding.root.findViewById(R.id.toolbarStatusLine),
            tileSourceIcon = binding.root.findViewById(R.id.toolbarTileSourceIcon),
            homeIcon       = binding.root.findViewById(R.id.toolbarHomeIcon),
            aboutIcon      = binding.root.findViewById(R.id.toolbarAboutIcon),
            alertsBadge    = binding.root.findViewById(R.id.alertsBadge),
            layersBadge    = binding.root.findViewById(R.id.layersBadge)
        )
        weatherIconView = toolbarRefs.weatherIcon
        alertsIconView  = toolbarRefs.alertsIcon
        alertsBadgeView = toolbarRefs.alertsBadge
        layersBadgeView = toolbarRefs.layersBadge
        // Set initial layer badge count
        appBarMenuManager.updateLayerBadge(layersBadgeView, appBarMenuManager.computeActiveLayerCount())
        statusLineManager = StatusLineManager(toolbarRefs.statusLine)
        favoritesManager = com.example.locationmapapp.util.FavoritesManager(this)
        DebugLogger.i("SalemMainActivity", "Slim toolbar wired — Weather, Home, Alerts, Grid, About + StatusLine")

        setupMap()
        buildFabSpeedDial()
        initNarrationSystem()
        // ── GPS Journey: prune stale data, init the polyline overlay, wire toggle ──
        gpsTrackRecorder.pruneStaleAtStartup()
        initGpsTrackOverlay()
        setupGpsToggleButton()
        // ── POI proximity tracker (S110): prune old encounters at startup ──
        poiEncounterTracker.pruneStaleAtStartup()
        observeViewModel()
        requestLocationPermission()
        // Post debug intent to next frame so it runs after onStart() restore
        if (intent?.extras?.let { it.containsKey("lat") || it.containsKey("zoom") ||
                    it.containsKey("enable") || it.containsKey("disable") } == true) {
            binding.mapView.post { handleDebugIntent(intent) }
        }
        DebugLogger.i("SalemMainActivity", "onCreate() complete")
    }

    // Slim toolbar is set up in onCreate() — no menu inflation needed.
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        DebugLogger.i("SalemMainActivity", "onCreateOptionsMenu() — skipped (using slim toolbar)")
        return true
    }

    override fun onStart() {
        super.onStart()
        DebugLogger.i("SalemMainActivity", "onStart() toolbar.childCount=${binding.toolbar.childCount}")
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)

        // Populate is never auto-restored — user must pick a location and start manually
        prefs.edit().putBoolean(MenuPrefs.PREF_POPULATE_POIS, false).apply()

        // Restore radar — add the tile overlay AND restart the refresh scheduler
        val radarOn = prefs.getBoolean(MenuPrefs.PREF_RADAR_ON, true)
        DebugLogger.i("SalemMainActivity", "onStart() radarOn=$radarOn interval=${appBarMenuManager.radarUpdateMinutes}min")
        if (radarOn) {
            if (radarTileOverlay == null) {
                addRadarOverlay()
                DebugLogger.i("SalemMainActivity", "Radar overlay restored from onStart()")
            }
            radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { weatherViewModel.refreshRadar() }
            DebugLogger.i("SalemMainActivity", "Radar scheduler restarted from onStart()")
        }

        // Restore MBTA layers — stagger startup to avoid ANR
        lifecycleScope.launch {
            delay(2000) // let map render first
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_TRAINS, true) && trainRefreshJob?.isActive != true) {
                DebugLogger.i("SalemMainActivity", "onStart() restoring MBTA trains")
                startTrainRefresh()
            }
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_SUBWAY, true) && subwayRefreshJob?.isActive != true) {
                DebugLogger.i("SalemMainActivity", "onStart() restoring MBTA subway")
                startSubwayRefresh()
            }
            delay(500)
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_BUSES, true) && busRefreshJob?.isActive != true) {
                DebugLogger.i("SalemMainActivity", "onStart() restoring MBTA buses")
                startBusRefresh()
            }
            delay(500)
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_STATIONS, true) && stationMarkers.isEmpty()) {
                DebugLogger.i("SalemMainActivity", "onStart() restoring MBTA stations")
                transitViewModel.fetchMbtaStations()
            }
            if (prefs.getBoolean(MenuPrefs.PREF_MBTA_BUS_STOPS, false) && allBusStops.isEmpty()) {
                DebugLogger.i("SalemMainActivity", "onStart() restoring MBTA bus stops")
                transitViewModel.fetchMbtaBusStops()
            }
        }

        // Defer METAR restore until GPS fix so the map has a real bounding box
        if (prefs.getBoolean(MenuPrefs.PREF_METAR_DISPLAY, true)) {
            pendingMetarRestore = true
            DebugLogger.i("SalemMainActivity", "onStart() METAR restore deferred — waiting for GPS fix")
        }

        // Defer Aircraft restore
        if (prefs.getBoolean(MenuPrefs.PREF_AIRCRAFT_DISPLAY, false)) {
            pendingAircraftRestore = true
            DebugLogger.i("SalemMainActivity", "onStart() Aircraft restore deferred — waiting for GPS fix")
        }

        // Restore auto-follow aircraft if it was active
        if (prefs.getBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, false)) {
            pendingAutoFollowRestore = true
            DebugLogger.i("SalemMainActivity", "onStart() Auto-follow restore deferred — waiting for GPS fix + aircraft data")
        }

        // Defer POI restore until first GPS fix so we query at the real location
        val anyPoiEnabled = PoiCategories.ALL.any { prefs.getBoolean(it.prefKey, it.defaultEnabled) }
        if (anyPoiEnabled) {
            pendingPoiRestore = true
            DebugLogger.i("SalemMainActivity", "onStart() POI restore deferred — waiting for GPS fix")
        }
        // Defer webcam restore until GPS fix
        if (prefs.getBoolean(MenuPrefs.PREF_WEBCAMS_ON, true)) {
            pendingWebcamRestore = true
            DebugLogger.i("SalemMainActivity", "onStart() Webcam restore deferred — waiting for GPS fix")
        }

        // Defer geofence overlay restore until GPS fix
        val anyGeofence = prefs.getBoolean(MenuPrefs.PREF_TFR_OVERLAY, true)
            || prefs.getBoolean(MenuPrefs.PREF_CAMERA_OVERLAY, false)
            || prefs.getBoolean(MenuPrefs.PREF_SCHOOL_OVERLAY, false)
            || prefs.getBoolean(MenuPrefs.PREF_FLOOD_OVERLAY, false)
            || prefs.getBoolean(MenuPrefs.PREF_CROSSING_OVERLAY, false)
        if (anyGeofence) {
            pendingGeofenceRestore = true
            DebugLogger.i("SalemMainActivity", "onStart() Geofence overlay restore deferred — waiting for GPS fix")
        }

        // Always load full cached POI coverage after GPS fix
        pendingCachedPoiLoad = true
    }

    override fun onStop()   { super.onStop();   if (radarAnimating) stopRadarAnimation(); radarScheduler.stop(); DebugLogger.i("SalemMainActivity","onStop()") }
    override fun onResume() {
        super.onResume(); binding.mapView.onResume()
        DebugHttpServer.endpoints = DebugEndpoints(this, viewModel, transitViewModel, aircraftViewModel, weatherViewModel, geofenceViewModel)
        DebugLogger.i("SalemMainActivity","onResume()")
    }
    override fun onPause() {
        super.onPause(); binding.mapView.onPause()
        DebugHttpServer.endpoints = null
        DebugLogger.i("SalemMainActivity","onPause()")
    }
    override fun onDestroy() {
        super.onDestroy()
        DebugHttpServer.stop()
        // S110: log any active POI proximity encounters that were in flight
        // when the activity was destroyed. The DB rows already hold their
        // last-known values; this is just for the diagnostic log so a
        // post-mortem reader can see what was open at destroy time.
        poiEncounterTracker.flushAll()
        DebugLogger.i("SalemMainActivity","onDestroy()")
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
     *   adb shell am start -n com.example.wickedsalemwitchcitytour/.ui.SalemMainActivity \
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

        DebugLogger.i("SalemMainActivity", "handleDebugIntent — processing debug extras")

        // ── Map position ──
        if (extras.containsKey("lat") && extras.containsKey("lon")) {
            val lat = extras.getFloat("lat").toDouble()
            val lon = extras.getFloat("lon").toDouble()
            val point = GeoPoint(lat, lon)
            binding.mapView.controller.animateTo(point)
            initialCenterDone = true  // suppress GPS auto-center
            DebugLogger.i("SalemMainActivity", "Debug: center map at $lat, $lon")
        }
        if (extras.containsKey("zoom")) {
            val zoom = extras.getInt("zoom").toDouble().coerceIn(1.0, 20.0)
            binding.mapView.controller.setZoom(zoom)
            updateZoomBubble()
            DebugLogger.i("SalemMainActivity", "Debug: zoom=$zoom")
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
                DebugLogger.i("SalemMainActivity", "Debug: enabled $key")
            } else {
                DebugLogger.w("SalemMainActivity", "Debug: unknown pref key '$key'")
            }
        }
        extras.getString("disable")?.split(",")?.map { it.trim() }?.forEach { key ->
            val handler = toggleMap[key]
            if (handler != null) {
                prefs.edit().putBoolean(key, false).apply()
                handler(false)
                DebugLogger.i("SalemMainActivity", "Debug: disabled $key")
            } else {
                DebugLogger.w("SalemMainActivity", "Debug: unknown pref key '$key'")
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

    /** True when arriving from SplashActivity — triggers cinematic zoom animation. */
    internal var fromSplash = false

    internal fun setupMap() {
        fromSplash = intent?.getBooleanExtra(SplashActivity.EXTRA_FROM_SPLASH, false) == true
        val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, MODE_PRIVATE)
        val tileSourceId = prefs.getString(MenuPrefs.PREF_TILE_SOURCE, TileSourceManager.DEFAULT_SOURCE)
            ?: TileSourceManager.DEFAULT_SOURCE
        DebugLogger.i("SalemMainActivity", "setupMap() — tileSource=$tileSourceId fromSplash=$fromSplash")
        binding.mapView.apply {
            setTileSource(TileSourceManager.buildSource(tileSourceId))
            setMultiTouchControls(true)
            // Disable built-in zoom buttons — we use the custom slider instead
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setBuiltInZoomControls(false)
            minZoomLevel = 3.0
            maxZoomLevel = if (tileSourceId == TileSourceManager.Id.SATELLITE)
                TileSourceManager.USGS_MAX_ZOOM.toDouble() else 19.0
            if (fromSplash) {
                // Start on Salem at street level — no wasted tile loading
                controller.setZoom(18.0)
                controller.setCenter(GeoPoint(42.5225, -70.8897)) // Downtown Salem
            } else {
                controller.setZoom(18.0)
                controller.setCenter(GeoPoint(42.5225, -70.8897)) // Downtown Salem
            }
        }
        // Splash fallback: if no GPS fix within 3s, zoom to Salem center
        if (fromSplash) {
            binding.mapView.postDelayed({
                if (!initialCenterDone && fromSplash) {
                    DebugLogger.i("SalemMainActivity", "Splash fallback — no GPS fix, zooming to Salem center")
                    performCinematicZoom(GeoPoint(42.521, -70.887))
                    fromSplash = false
                    initialCenterDone = true
                }
            }, 3000L)
        }
        setupZoomSlider()
        setupZoomToggle()
        setupWalkSimButton()
        setupShowAllPoisButton()
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                var consumed = false
                // Stop walk simulator if running — user tapped the map
                if (walkSimRunning) {
                    stopWalkSim()
                    consumed = true
                }
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
                DebugLogger.i("SalemMainActivity", "Long-press → manual mode at ${p.latitude},${p.longitude}")
                // Stop walk simulator if running — user wants to teleport somewhere else
                if (walkSimRunning) stopWalkSim()
                // Stop populate scanner, idle scanner, and silent fill — user is moving to a new location
                if (populateJob != null) stopPopulatePois()
                stopProbe10km()
                stopIdlePopulate(clearState = true)
                stopSilentFill()
                viewModel.setManualLocation(p)
                // Feed tour engine so geofences trigger at the teleported location
                updateTourLocation(p)
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

        DebugLogger.i("SalemMainActivity", "Map configured — overlays=${binding.mapView.overlays.size}")
    }

    /** Set map max zoom based on tile source — USGS satellite caps at 16, others at 19. */
    internal fun applyTileSourceZoomLimits(tileSourceId: String) {
        val maxZoom = if (tileSourceId == TileSourceManager.Id.SATELLITE)
            TileSourceManager.USGS_MAX_ZOOM.toDouble() else 19.0
        binding.mapView.maxZoomLevel = maxZoom
        // If currently zoomed past new max, snap back
        if (binding.mapView.zoomLevelDouble > maxZoom) {
            binding.mapView.controller.setZoom(maxZoom)
        }
        DebugLogger.i("SalemMainActivity", "Max zoom set to $maxZoom for $tileSourceId")
    }

    /**
     * Cinematic zoom-in animation: US from space → region → city → street level.
     * Called when arriving from SplashActivity on first GPS fix (or fallback to Salem center).
     *
     * Sequence: zoom 4 → 10 (800ms) → GPS at 14 (1200ms) → ease to 16 (800ms)
     * Final zoom capped at satellite max (16) when using USGS tiles.
     */
    internal fun performCinematicZoom(target: GeoPoint) {
        DebugLogger.i("SalemMainActivity", "performCinematicZoom → ${target.latitude},${target.longitude}")
        val map = binding.mapView
        val maxZoom = map.maxZoomLevel.coerceAtMost(19.0)

        // Phase 1: zoom 4→10, center on target region (800ms)
        map.controller.animateTo(target, 10.0, 800L)

        // Phase 2: zoom 10→14, center on exact target (1200ms)
        map.postDelayed({
            map.controller.animateTo(target, 14.0, 1200L)
        }, 900L)

        // Phase 3: ease to max zoom — street-level detail (800ms)
        map.postDelayed({
            map.controller.animateTo(target, maxZoom, 800L)
        }, 2200L)

        // Phase 4: show welcome dialog after zoom settles
        map.postDelayed({
            showWelcomeDialog()
        }, 3400L)
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
                refreshNarrationIcons()
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

    /** Set up the bottom-left magnification toggle (x1 → x2 → x3 → x1).
     *  Scales the map view visually without changing the actual zoom level. */
    internal fun setupZoomToggle() {
        val scaleFactors = floatArrayOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f) // x1–x5
        val labels = arrayOf("x1", "x2", "x3", "x4", "x5")

        binding.zoomToggleBtn.setOnClickListener {
            zoomToggleLevel = (zoomToggleLevel + 1) % 5
            val scale = scaleFactors[zoomToggleLevel]
            binding.mapView.animate()
                .scaleX(scale).scaleY(scale)
                .setDuration(200L)
                .start()
            binding.zoomToggleLabel.text = labels[zoomToggleLevel]
            DebugLogger.i("SalemMainActivity", "Map magnify → ${labels[zoomToggleLevel]} (scale $scale)")
        }
    }

    /** Walk simulator button — starts/stops emulated GPS walk along the witch trials tour. */
    internal fun setupWalkSimButton() {
        binding.btnWalkSim.setOnClickListener {
            if (walkSimRunning) {
                stopWalkSim()
            } else {
                startWalkSim()
            }
        }
    }

    private fun startWalkSim() {
        // Walk sim follows downtown Salem street route (PEM → Essex → Common → Derby → back)
        val routePoints = com.example.wickedsalemwitchcitytour.tour.TourRouteLoader
            .loadDowntownRoute(this)
        if (routePoints.isEmpty()) {
            toast("No downtown Salem route data")
            return
        }
        // Reset narration session so all POIs can trigger fresh (clears narratedToday + cooldowns)
        narrationGeofenceManager.resetSession()
        // Enable walk sim mode: expanded geofence radius + tighter reach-out
        narrationGeofenceManager.walkSimMode = true
        walkSimRunning = true
        binding.btnWalkSim.text = "Stop"
        binding.btnWalkSim.setBackgroundResource(R.drawable.zoom_button_active_bg)
        DebugLogger.i("SalemMainActivity", "Walk sim started: ${routePoints.size} route points (narration session reset)")

        walkSimJob = lifecycleScope.launch {
            val interpolated = interpolateWalkRoute(routePoints, 1.4f)
            DebugLogger.i("SalemMainActivity", "Walk sim: ${interpolated.size} steps at 1.4m/s")
            for (point in interpolated) {
                if (walkSimJob?.isCancelled == true) break
                viewModel.setManualLocation(point)
                kotlinx.coroutines.delay(1000L)
            }
            // Walk complete
            if (walkSimJob?.isCancelled != true) {
                walkSimRunning = false
                binding.btnWalkSim.text = "Walk"
                binding.btnWalkSim.setBackgroundResource(R.drawable.zoom_toggle_bg)
                toast("Walk simulation complete")
            }
        }
    }

    private fun stopWalkSim() {
        walkSimJob?.cancel()
        walkSimJob = null
        walkSimRunning = false
        narrationGeofenceManager.walkSimMode = false
        binding.btnWalkSim.text = "Walk"
        binding.btnWalkSim.setBackgroundResource(R.drawable.zoom_toggle_bg)
        DebugLogger.i("SalemMainActivity", "Walk sim stopped")
        toast("Walk stopped")
    }

    private fun interpolateWalkRoute(points: List<org.osmdroid.util.GeoPoint>, speedMps: Float): List<org.osmdroid.util.GeoPoint> {
        if (points.size < 2) return points
        val result = mutableListOf(points[0])
        var residualM = 0.0
        for (i in 0 until points.size - 1) {
            val from = points[i]; val to = points[i + 1]
            val segDist = com.example.wickedsalemwitchcitytour.tour.TourEngine.haversineM(
                from.latitude, from.longitude, to.latitude, to.longitude)
            if (segDist < 0.1) continue
            // If carry-over from previous segment exceeds this segment, consume and skip
            if (residualM >= segDist) {
                residualM -= segDist
                continue
            }
            var covered = residualM
            while (covered < segDist) {
                covered += speedMps
                if (covered >= segDist) { residualM = covered - segDist; break }
                val frac = covered / segDist
                result.add(org.osmdroid.util.GeoPoint(
                    from.latitude + (to.latitude - from.latitude) * frac,
                    from.longitude + (to.longitude - from.longitude) * frac))
            }
        }
        result.add(points.last())
        return result
    }

    /**
     * S112: POI button toggle — ON shows all POIs, OFF shows only the
     * historic + attraction + paid customer tiers (the curated set the
     * operator wants to see by default during a tour). The OFF state used
     * to fall back to the per-category SharedPreferences toggles, but the
     * operator wants OFF to mean a fixed tier filter.
     *
     * Both branches re-render OSM POIs and narration markers; the actual
     * filtering happens in `loadCachedPoisForVisibleArea` (OSM places) and
     * `loadNarrationPointMarkers` (Salem narration data).
     */
    internal fun setupShowAllPoisButton() {
        binding.btnShowAllPois.setOnClickListener {
            showAllPoisActive = !showAllPoisActive
            if (showAllPoisActive) {
                binding.btnShowAllPois.setBackgroundResource(R.drawable.zoom_button_active_bg)
                toast("Showing ALL POIs")
            } else {
                binding.btnShowAllPois.setBackgroundResource(R.drawable.zoom_toggle_bg)
                toast("Historic + attractions only")
            }
            DebugLogger.i("SalemMainActivity", "Show all POIs → $showAllPoisActive")
            loadCachedPoisForVisibleArea()
            // S112: Always reload the narration markers — both branches need them,
            // but the OFF branch filters them down to historic + attraction + paid.
            loadNarrationPointMarkers()
        }
    }

    /** Narration point markers — stored for zoom-based icon refresh */
    internal val narrationMarkers = mutableListOf<Pair<Marker, com.example.wickedsalemwitchcitytour.content.model.NarrationPoint>>()
    private var lastNarrationIconZoom = -1

    /**
     * Load narration points as markers on the map.
     *
     * S112: Honors the POI button (`showAllPoisActive`):
     *   - ON  → all narration points are loaded (full set)
     *   - OFF → only points classified as PAID, HISTORIC, or ATTRACTION
     *
     * Always removes any previously-loaded narration markers from the map
     * before loading the new set, so toggling the button is fully reversible.
     */
    internal fun loadNarrationPointMarkers() {
        lifecycleScope.launch {
            try {
                // Remove any previously loaded narration markers (they may be a
                // different filtered subset from a prior toggle state)
                for ((marker, _) in narrationMarkers) {
                    binding.mapView.overlays.remove(marker)
                }
                narrationMarkers.clear()

                val allPoints = tourViewModel.loadNarrationPoints()
                val points = if (showAllPoisActive) {
                    allPoints
                } else {
                    // OFF: only show paid + historic + attraction tiers
                    allPoints.filter { p ->
                        val tier = com.example.wickedsalemwitchcitytour.tour.NarrationTierClassifier.classify(p)
                        tier == com.example.wickedsalemwitchcitytour.tour.NarrationTier.PAID ||
                        tier == com.example.wickedsalemwitchcitytour.tour.NarrationTier.HISTORIC ||
                        tier == com.example.wickedsalemwitchcitytour.tour.NarrationTier.ATTRACTION
                    }
                }
                DebugLogger.i("SalemMainActivity",
                    "Loading ${points.size} narration points as markers (showAll=$showAllPoisActive, total=${allPoints.size})")
                val zoom = binding.mapView.zoomLevelDouble
                for (p in points) {
                    val marker = Marker(binding.mapView)
                    marker.position = org.osmdroid.util.GeoPoint(p.lat, p.lng)
                    marker.title = p.name
                    marker.snippet = p.type.replace('_', ' ')
                    marker.icon = narrationIconForZoom(p.type, p.name, zoom)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.setOnMarkerClickListener { _, _ ->
                        enqueueNarration(p, jumpToFront = true)
                        true
                    }
                    binding.mapView.overlays.add(marker)
                    narrationMarkers.add(marker to p)
                }
                lastNarrationIconZoom = zoomBucket(zoom)
                binding.mapView.invalidate()
            } catch (e: Exception) {
                DebugLogger.e("SalemMainActivity", "Failed to load narration markers", e)
            }
        }
    }

    /** Zoom bucket: 0=far, 1=medium, 2=close, 3=street — triggers icon refresh on change */
    private fun zoomBucket(zoom: Double): Int = when {
        zoom < 15 -> 0
        zoom < 17 -> 1
        zoom < 18 -> 2
        else -> 3
    }

    /** Get the right icon for a narration point based on current zoom */
    private fun narrationIconForZoom(type: String, name: String, zoom: Double): android.graphics.drawable.BitmapDrawable {
        return when {
            zoom >= 18 -> MarkerIconHelper.labeledDot(this, type, name)
            zoom >= 17 -> MarkerIconHelper.dot(this, type, 20)
            zoom >= 15 -> MarkerIconHelper.dot(this, type, 12)
            else -> MarkerIconHelper.dot(this, type, 8)
        }
    }

    /** Called from scroll/zoom listener — refresh narration marker icons when zoom bucket changes */
    internal fun refreshNarrationIcons() {
        if (narrationMarkers.isEmpty()) return
        val zoom = binding.mapView.zoomLevelDouble
        val bucket = zoomBucket(zoom)
        if (bucket == lastNarrationIconZoom) return
        lastNarrationIconZoom = bucket
        // Only refresh markers in the current viewport — avoids ANR from iterating
        // 800+ markers at every zoom-bucket transition
        val visible = binding.mapView.boundingBox
        var refreshed = 0
        for ((marker, point) in narrationMarkers) {
            if (!visible.contains(marker.position)) continue
            marker.icon = narrationIconForZoom(point.type, point.name, zoom)
            refreshed++
        }
        DebugLogger.i("SalemMainActivity", "refreshNarrationIcons: bucket=$bucket refreshed=$refreshed/${narrationMarkers.size}")
        binding.mapView.invalidate()
    }

    // =========================================================================
    // LOCATION — startup centering fix
    // =========================================================================

    internal fun requestLocationPermission() {
        DebugLogger.i("SalemMainActivity", "requestLocationPermission() — checking current state")

        // Fast path: permission already granted from a previous session.
        // Skip the launcher round-trip and start GPS immediately so the map
        // centres on the user right away on every cold start after first install.
        val alreadyGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            DebugLogger.i("SalemMainActivity", "requestLocationPermission() — already granted, starting GPS directly")
            startGpsUpdatesAndCenter()
            return
        }

        // Slow path: first install or user previously denied — ask the system.
        DebugLogger.i("SalemMainActivity", "requestLocationPermission() — launching permission launcher")
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
            DebugLogger.w("SalemMainActivity", "startGpsUpdatesAndCenter() called again — SKIPPING")
            return
        }
        locationUpdatesStarted = true
        DebugLogger.i("SalemMainActivity", "startGpsUpdatesAndCenter() — permission confirmed, starting GMS flow")

        // Tell ViewModel permission is granted — this starts the GMS Flow internally.
        viewModel.onPermissionGranted()

        // Immediate centering: get the cached last-known fix from FusedClient.
        // Fires asynchronously and centres the map before the first 5s poll arrives.
        viewModel.requestLastKnownLocation()
    }

    fun toggleLocationMode() {
        viewModel.toggleLocationMode()
        DebugLogger.i("SalemMainActivity", "Location mode toggled → ${viewModel.locationMode.value}")
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
                DebugLogger.i("SalemMainActivity", "Auto-follow cancelled — aircraft layer FAB off")
            }
            toast("Aircraft tracking OFF")
        }
    }

    // =========================================================================
    // FAB SPEED DIAL
    // =========================================================================

    internal data class FabDef(val label: String, val iconRes: Int, val color: String, val action: () -> Unit)

    internal fun buildFabSpeedDial() {
        DebugLogger.i("SalemMainActivity", "buildFabSpeedDial() start")
        val defs = listOf(
            FabDef("Radar",        R.drawable.ic_radar,         "#0277BD") { toggleRadar() },
            FabDef("METAR",        R.drawable.ic_metar,         "#1B5E20") { loadMetarsForVisibleArea(); toast("Loading METARs…") },
            FabDef("Aircraft",     R.drawable.ic_aircraft,      "#1565C0") { toggleAircraftFromFab() },
            FabDef("Search Here",  R.drawable.ic_search,        "#4A148C") { searchFromCurrentLocation() },
            FabDef("Weather",      R.drawable.ic_wx_default,    "#006064") { showWeatherDialog() },
            FabDef("GPS / Manual", R.drawable.ic_gps,           "#37474F") { toggleLocationMode() },
            FabDef("Debug Log",    R.drawable.ic_debug,         "#424242") { startActivity(Intent(this, DebugLogActivity::class.java)) },
            FabDef("Voices",       R.drawable.ic_debug,         "#6A1B9A") { startActivity(Intent(this, com.example.wickedsalemwitchcitytour.debug.VoiceTestActivity::class.java)) }
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
            DebugLogger.d("SalemMainActivity", "  FAB row: '${def.label}'")
        }

        DebugLogger.i("SalemMainActivity", "FAB container built — ${container.childCount} rows")
        updateFabTriggerIcon(open = false)
        binding.fabMain.setOnClickListener {
            DebugLogger.d("SalemMainActivity", "FAB tapped — fabMenuOpen=$fabMenuOpen")
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
    // GPS JOURNEY (Phase 9T+, S109) — Option B: always record, toggle controls visibility
    // =========================================================================

    private val GPS_TRACK_PREFS = "app_bar_menu_prefs"
    private val GPS_TRACK_PREF_KEY = "gps_track_visible"
    private val GPS_TRACK_TINT_ON  = Color.parseColor("#80FF80")  // soft green when visible
    private val GPS_TRACK_TINT_OFF = Color.parseColor("#888888")  // gray when hidden

    /**
     * Creates the magenta journey polyline immediately (empty), adds it to the map
     * if the user preference says visible, then asynchronously seeds it with the
     * last 24h of recorded points from [GpsTrackRecorder].
     *
     * The empty-first-then-seed pattern means [observeViewModel]'s GPS handler can
     * unconditionally call `gpsTrackOverlay?.actualPoints.add(...)` from frame 1
     * without a null check race. The async seed runs on the Main dispatcher (per
     * lifecycleScope default) so it serializes cleanly with the GPS observer's
     * point appends.
     */
    private fun initGpsTrackOverlay() {
        val polyline = Polyline().apply {
            outlinePaint.apply {
                color = Color.parseColor("#B3FF00FF")  // semi-transparent magenta (~70% alpha)
                strokeWidth = 8f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            title = "GPS journey"
        }
        gpsTrackOverlay = polyline
        if (isGpsTrackVisible()) {
            // Insert at index 0 so it paints below POI markers and the GPS marker
            binding.mapView.overlays.add(0, polyline)
        }

        lifecycleScope.launch {
            val recent = gpsTrackRecorder.getRecent24h()
            // Any fixes that arrived during the suspend are already in actualPoints
            // (added by the GPS observer on Main). Prepend the historical to keep
            // chronological order and avoid losing those in-flight appends.
            val pendingNew = polyline.actualPoints.toList()
            val merged = recent.map { GeoPoint(it.lat, it.lng) } + pendingNew
            polyline.setPoints(merged)
            binding.mapView.invalidate()
            DebugLogger.i(
                "SalemMainActivity",
                "GPS journey overlay seeded — ${recent.size} historical + ${pendingNew.size} in-flight points (visible=${isGpsTrackVisible()})"
            )
        }
    }

    /**
     * Wires the bottom-left "GPS" FAB to add/remove the journey polyline from the
     * map. Persists the visibility preference; recording is unaffected (Option B).
     */
    private fun setupGpsToggleButton() {
        val btn = binding.btnGpsToggle
        btn.setTextColor(if (isGpsTrackVisible()) GPS_TRACK_TINT_ON else GPS_TRACK_TINT_OFF)
        btn.setOnClickListener {
            val newVisible = !isGpsTrackVisible()
            setGpsTrackVisible(newVisible)
            btn.setTextColor(if (newVisible) GPS_TRACK_TINT_ON else GPS_TRACK_TINT_OFF)
            gpsTrackOverlay?.let { polyline ->
                if (newVisible) {
                    if (!binding.mapView.overlays.contains(polyline)) {
                        binding.mapView.overlays.add(0, polyline)
                    }
                } else {
                    binding.mapView.overlays.remove(polyline)
                }
                binding.mapView.invalidate()
            }
            Toast.makeText(
                this,
                if (newVisible) "GPS journey path: ON" else "GPS journey path: OFF",
                Toast.LENGTH_SHORT
            ).show()
            DebugLogger.i(
                "SalemMainActivity",
                "GPS journey toggled → ${if (newVisible) "VISIBLE" else "HIDDEN"} (recording always on)"
            )
        }
    }

    private fun isGpsTrackVisible(): Boolean =
        getSharedPreferences(GPS_TRACK_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(GPS_TRACK_PREF_KEY, true)

    private fun setGpsTrackVisible(visible: Boolean) {
        getSharedPreferences(GPS_TRACK_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GPS_TRACK_PREF_KEY, visible)
            .apply()
    }

    // =========================================================================
    // OBSERVERS
    // =========================================================================

    internal fun observeViewModel() {
        DebugLogger.i("SalemMainActivity", "observeViewModel() — attaching all observers")

        // ── GPS-OBS heartbeat (S110) ──
        //   Tick every 10 seconds. Logs in three cases:
        //     1. State TRANSITION (healthy → stale, stale → recovered, no-fix → first-fix) — logged immediately
        //     2. Periodic OK heartbeat — once per 60 seconds (every 6 ticks) while healthy, so the
        //        post-mortem reader can confirm the coroutine is alive even when nothing's wrong
        //     3. STALE-still — once per 30 seconds while held in stale state, so we don't spam
        //   Without case (2), the heartbeat looks dead during normal operation and you can't tell
        //   the difference between "GPS is fine, heartbeat ticking silently" and "heartbeat
        //   coroutine crashed".
        lifecycleScope.launch {
            DebugLogger.i("GPS-OBS", "HEARTBEAT START — tick=10s, stale-threshold=${GPS_STALE_THRESHOLD_MS / 1000}s, ok-log=60s")
            var lastOkLogAt = 0L
            var lastStaleLogAt = 0L
            var wasStale = false
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                val now = System.currentTimeMillis()
                if (lastFixAtMs == 0L) {
                    // Pre-first-fix — log every 30s while we wait
                    if (now - lastStaleLogAt >= 30_000L) {
                        DebugLogger.w("GPS-OBS", "HEARTBEAT no-fix-yet (waiting for first emission)")
                        lastStaleLogAt = now
                    }
                    continue
                }
                val ageMs = now - lastFixAtMs
                val isStale = ageMs > GPS_STALE_THRESHOLD_MS
                when {
                    // Transition: healthy → stale
                    isStale && !wasStale -> {
                        DebugLogger.w("GPS-OBS",
                            "HEARTBEAT STALE (transition) — last fix ${ageMs / 1000}s ago, narration reach-out suppressed")
                        wasStale = true
                        lastStaleLogAt = now
                    }
                    // Transition: stale → healthy
                    !isStale && wasStale -> {
                        DebugLogger.i("GPS-OBS",
                            "HEARTBEAT recovered — fresh fix after ${ageMs}ms")
                        wasStale = false
                        lastOkLogAt = now
                    }
                    // Steady-state stale: throttled to once per 30s
                    isStale -> {
                        if (now - lastStaleLogAt >= 30_000L) {
                            DebugLogger.w("GPS-OBS",
                                "HEARTBEAT still-stale — last fix ${ageMs / 1000}s ago")
                            lastStaleLogAt = now
                        }
                    }
                    // Steady-state healthy: throttled to once per 60s
                    else -> {
                        if (now - lastOkLogAt >= 60_000L) {
                            DebugLogger.i("GPS-OBS",
                                "HEARTBEAT ok — last fix age=${ageMs / 1000}s")
                            lastOkLogAt = now
                        }
                    }
                }
            }
        }

        viewModel.currentLocation.observe(this) { update ->
            val point = update.point
            val speedMph = update.speedMps?.let { it * 2.23694 }
            lastGpsSpeedMph = speedMph

            // ── 0a. GPS staleness tracking (S110) ──
            //    Stamp wall-clock timestamp on EVERY fix BEFORE any other logic.
            //    The narration reach-out gate uses this to suppress reach-out
            //    when the location stream goes silent (driving out of GPS coverage,
            //    process-suspended, etc.). The GPS-OBS log line below is the
            //    "every fix the activity sees" record — combined with the
            //    GPS-OBS heartbeat coroutine, it gives a complete timeline of
            //    GPS health for post-mortem debugging.
            val previousFixAt = lastFixAtMs
            lastFixAtMs = System.currentTimeMillis()
            val gapMs = if (previousFixAt == 0L) 0L else (lastFixAtMs - previousFixAt)
            DebugLogger.i(
                "GPS-OBS",
                "fix lat=${"%.5f".format(point.latitude)} lng=${"%.5f".format(point.longitude)} " +
                "acc=${"%.1f".format(update.accuracy)}m " +
                "speed=${update.speedMps?.let { "${"%.2f".format(it)}mps" } ?: "?"} " +
                "bearing=${update.bearing?.let { "${"%.0f".format(it)}°" } ?: "?"} " +
                "gap=${gapMs}ms"
            )

            // ── 0b. GPS Journey: record every fix to user_data.db (Option B, S109) ──
            //    Recording is independent of the dead-zone gate AND of the visibility
            //    toggle — we capture every fix the activity sees so the journey log
            //    has continuous data even when the visual marker is parked.
            gpsTrackRecorder.recordFix(
                lat = point.latitude,
                lng = point.longitude,
                accuracyM = update.accuracy,
                speedMps = update.speedMps,
                bearingDeg = update.bearing
            )
            gpsTrackOverlay?.let { polyline ->
                polyline.actualPoints.add(GeoPoint(point.latitude, point.longitude))
                if (binding.mapView.overlays.contains(polyline)) {
                    binding.mapView.invalidate()
                }
            }

            // ── 1. Adaptive GPS interval ──
            //   Driving (>20 mph): 10s — coarse is fine
            //   Walking with narration active: 5s — need frequent checks for 20-50m geofences
            //   Stationary/slow without narration: 60s — battery saver
            //
            //   S110: narration manager is now a Hilt singleton, so it's always present
            //   while the activity is alive. The old null-check was the proxy for
            //   "narration system initialized?"; that's now structurally guaranteed.
            val narrationActive = true
            val desiredInterval = when {
                (speedMph ?: 0.0) > 20.0 -> 10_000L
                narrationActive -> 5_000L
                else -> 60_000L
            }
            if (desiredInterval != currentGpsIntervalMs) {
                currentGpsIntervalMs = desiredInterval
                val minInterval = if (desiredInterval <= 5_000L) 2_000L else if (desiredInterval == 10_000L) 5_000L else 30_000L
                DebugLogger.i("SalemMainActivity", "GPS interval → ${desiredInterval}ms (speed=${speedMph?.let { "%.1f".format(it) } ?: "?"}mph)")
                viewModel.restartLocationUpdates(desiredInterval, minInterval)
            }

            // ── 2. Proportional dead zone — drop fixes within 2× reported accuracy ──
            //    S109 fix: replaces the old fixed 100m threshold. At slow downtown driving
            //    speeds (13-15 mph ≈ 6 m/s) the 100m threshold made the cursor refresh only
            //    every 100m/6m/s ≈ 15 seconds — exactly the user-reported "10-15 sec GPS
            //    updates" symptom. The chip and the GMS request are both fine; the entire
            //    bug was here.
            //
            //    New rule: dead zone = 2× reported accuracy, clamped to [5m, 100m].
            //      • 5m floor   — always allow real movement >5m, never freeze the cursor
            //                     when GPS is reporting good accuracy.
            //      • 100m ceil  — worst-case multipath safety net, matches the old behavior.
            //      • 2× acc     — when accuracy widens (urban canyon, tree cover, indoors)
            //                     the dead zone widens with it, so jitter is still absorbed
            //                     under degraded conditions.
            //
            //    Self-adapts across walking, slow driving, and L1-only multipath without
            //    needing mode-specific logic. Bypassed in MANUAL mode (walk simulator) so
            //    every position update renders.
            val isManual = viewModel.locationMode.value == com.example.wickedsalemwitchcitytour.ui.LocationMode.MANUAL
            val deadZoneMeters = (update.accuracy * 2f).coerceIn(5f, 100f)
            if (!initialCenterDone) {
                // First fix always processes — fall through
            } else if (!isManual && distanceBetween(lastGpsPoint, point) < deadZoneMeters) {
                DebugLogger.d("SalemMainActivity",
                    "GPS dead zone ${"%.1f".format(deadZoneMeters)}m " +
                    "(acc=${"%.1f".format(update.accuracy)}m) — skipped (map only)")
                // Still handle deferred restores even during jitter
                handleDeferredRestores(point)

                // ── Update status line with GPS position ──
                updateIdleStatusLine(point.latitude, point.longitude, speedMph)

                // ── NARRATION: feed every GPS update regardless of dead zone ──
                // Geofence radii are 20-50m; the dead zone is for map/marker only
                updateTourLocation(point)

                // ── Idle auto-populate: start full scanner after 10 min stationary ──
                //
                // S109: gated to TOUR MODE ONLY. In Explore mode the spiral pulls in POIs
                // far outside the visible viewport, which is wasted bandwidth and irrelevant
                // to the user (they're browsing, not following a route). On a tour, the
                // outward spiral usefully prefetches the next stop's neighbourhood while
                // the user lingers at the current stop, so the data is ready as they walk
                // point-to-point. Active OR Paused both count as "on a tour" — if the user
                // paused at a stop to take a photo, prefetching is still useful. Idle /
                // Loading / Completed / Error all skip the trigger.
                val tourState = tourViewModel.tourState.value
                val tourActive = tourState is com.example.wickedsalemwitchcitytour.tour.TourState.Active
                    || tourState is com.example.wickedsalemwitchcitytour.tour.TourState.Paused
                val idleMs = System.currentTimeMillis() - lastSignificantMoveTime
                if (idleMs > 600_000L
                    && tourActive
                    && idlePopulateJob?.isActive != true
                    && populateJob == null
                    && followedVehicleId == null && followedAircraftIcao == null
                    && (speedMph ?: 0.0) <= 20.0
                ) {
                    lifecycleScope.launch {
                        val nearbyCount = findViewModel.fetchNearbyPoiCount(point.latitude, point.longitude)
                        if (nearbyCount >= 100) {
                            DebugLogger.i("SalemMainActivity", "Idle ${idleMs / 1000}s — skipping idle auto-populate: $nearbyCount POIs within 10km (≥100)")
                        } else {
                            DebugLogger.i("SalemMainActivity", "Idle ${idleMs / 1000}s — starting idle auto-populate at GPS ${point.latitude},${point.longitude} ($nearbyCount POIs within 10km)")
                            startIdlePopulate(point)
                        }
                    }
                }

                return@observe
            }

            // ── 3. Update GPS marker ──
            updateGpsMarker(point)
            lastGpsPoint = point

            // ── 3a. Feed location to tour engine ──
            updateTourLocation(point)

            // ── 3b. Significant move — reset idle timer and cancel idle populate ──
            lastSignificantMoveTime = System.currentTimeMillis()
            if (idlePopulateJob?.isActive == true) {
                DebugLogger.i("SalemMainActivity", "GPS moved beyond dead zone — stopping idle auto-populate")
                stopIdlePopulate(clearState = true)
            }
            idlePopulateState = null  // discard saved state — user has moved

            // ── 4. Center map on GPS — always follow unless tracking a vehicle/aircraft ──
            if (followedVehicleId == null && followedAircraftIcao == null) {
                if (!initialCenterDone) {
                    initialCenterDone = true
                    lastPoiFetchPoint = point
                    lastApiCallTime = System.currentTimeMillis()
                    if (fromSplash) {
                        DebugLogger.i("SalemMainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — cinematic zoom-in")
                        performCinematicZoom(point)
                        fromSplash = false
                    } else {
                        DebugLogger.i("SalemMainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — initial center zoom=18")
                        binding.mapView.controller.animateTo(point)
                        binding.mapView.controller.setZoom(18.0)
                    }
                } else {
                    // Continuous GPS follow — keep map centered on user
                    binding.mapView.controller.animateTo(point)
                    DebugLogger.d("SalemMainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — following (speed=${speedMph?.let { "%.1f".format(it) } ?: "?"}mph)")
                }
            } else {
                DebugLogger.d("SalemMainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — skipped center (following vehicle/aircraft)")
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
                DebugLogger.d("SalemMainActivity", "Speed >20mph — skipping API calls")
                return@observe
            }

            // ── 8. 3km POI threshold + 1-min cooldown ──
            val now = System.currentTimeMillis()
            val distFromLastFetch = distanceBetween(lastPoiFetchPoint, point)
            if (distFromLastFetch > 3000f && (now - lastApiCallTime) > 60_000L) {
                DebugLogger.i("SalemMainActivity", "GPS moved ${distFromLastFetch.toInt()}m from last POI fetch — refreshing layers")
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
                DebugLogger.i("SalemMainActivity", "poiClusters → ${clusters.size} clusters")
                renderPoiClusters(clusters)
            } else {
                clearClusterMarkers()
            }
        }
        viewModel.places.observe(this) { (layerId, places) ->
            DebugLogger.i("SalemMainActivity", "places → ${places.size} results layerId=$layerId")
            if (layerId == "bbox") {
                if (places.size > 5000) {
                    DebugLogger.i("SalemMainActivity", "POI count ${places.size} > 5000 — suppressing display")
                    replaceAllPoiMarkers(emptyList())
                    return@observe
                }
                // Clear cluster markers when showing individual POIs
                clearClusterMarkers()
                // S112: POI button filter — ON shows all OSM POIs, OFF shows only the
                // historic + attraction tier set (paid customers don't apply to OSM
                // places — adPriority is a NarrationPoint-only field).
                val filtered = if (showAllPoisActive) {
                    places
                } else {
                    places.filter {
                        com.example.wickedsalemwitchcitytour.tour.NarrationTierClassifier
                            .isHistoricOrAttractionTag(it.category)
                    }
                }
                DebugLogger.i("SalemMainActivity", "POI layer filter: ${places.size} → ${filtered.size} (showAll=$showAllPoisActive)")
                replaceAllPoiMarkers(filtered)
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
            DebugLogger.i("SalemMainActivity", "metars → ${metars.size} stations")
            clearMetarMarkers(); metars.forEach { addMetarMarker(it) }
            bringStationMarkersToFront()
        }
        weatherViewModel.weatherAlerts.observe(this) { alerts ->
            DebugLogger.i("SalemMainActivity", "weatherAlerts → ${alerts.size} alerts")
            appBarMenuManager.updateAlertBadge(alertsBadgeView, alerts.size)
            if (alerts.isNotEmpty()) toast("${alerts.size} weather alert(s) active")
        }
        weatherViewModel.weatherData.observe(this) { data ->
            DebugLogger.i("SalemMainActivity", "weatherData → ${data?.location?.city ?: "null"}")
            updateWeatherToolbarIcon(data)
            // Refresh idle status line with latest weather info
            lastGpsPoint?.let { pt -> updateIdleStatusLine(pt.latitude, pt.longitude, lastGpsSpeedMph) }
        }
        weatherViewModel.radarRefreshTick.observe(this) {
            DebugLogger.i("SalemMainActivity", "radarRefreshTick → refreshing overlay")
            refreshRadarOverlay()
        }
        weatherViewModel.webcams.observe(this) { webcams ->
            DebugLogger.i("SalemMainActivity", "webcams → ${webcams.size} on map")
            clearWebcamMarkers()
            addWebcamMarkersBatched(webcams)
        }
        viewModel.error.observe(this) { msg ->
            DebugLogger.e("SalemMainActivity", "VM error: $msg")
            toast(msg)
        }
        aircraftViewModel.aircraft.observe(this) { aircraft ->
            DebugLogger.i("SalemMainActivity", "aircraft → ${aircraft.size} on map")
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
                    DebugLogger.i("SalemMainActivity", "EMERGENCY SQUAWK ${s.squawk} (${squawkLabel(s.squawk!!)}) — $callsign icao=${s.icao24} alt=$altFt")
                }
            updateFollowedAircraft(merged)
        }
        aircraftViewModel.followedAircraft.observe(this) { state ->
            if (state == null) {
                if (followedAircraftIcao != null) {
                    followedAircraftFailCount++
                    DebugLogger.i("SalemMainActivity", "Followed aircraft ${followedAircraftIcao} null response — failCount=$followedAircraftFailCount")
                    if (followedAircraftFailCount < 3) {
                        // Tolerate transient failures (429 rate limits, network blips)
                        DebugLogger.i("SalemMainActivity", "Tolerating failure $followedAircraftFailCount/3 — keeping lock")
                        return@observe
                    }
                    DebugLogger.i("SalemMainActivity", "Followed aircraft ${followedAircraftIcao} lost after $followedAircraftFailCount failures")
                    stopFollowing()
                    if (autoFollowAircraftJob?.isActive == true) {
                        DebugLogger.i("SalemMainActivity", "Auto-follow active — picking replacement aircraft")
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
                DebugLogger.i("SalemMainActivity", "Followed aircraft update: ${state.callsign ?: state.icao24} at ${state.lat},${state.lon}")
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
                DebugLogger.i("SalemMainActivity", "Aircraft follow POI prefetch at ${point.latitude},${point.longitude}")
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
                        DebugLogger.i("SalemMainActivity", "Auto-follow: hit west margin (lon=$lon) — flipping to eastbound")
                        toast("West coast — switching to eastbound")
                    } else if (lon > EAST_MARGIN && !autoFollowPreferWest) {
                        autoFollowPreferWest = true
                        bounced = true
                        DebugLogger.i("SalemMainActivity", "Auto-follow: hit east margin (lon=$lon) — flipping to westbound")
                        toast("East coast — switching to westbound")
                    }
                    // North/South bounce
                    if (lat > NORTH_MARGIN && !autoFollowPreferSouth) {
                        autoFollowPreferSouth = true
                        bounced = true
                        DebugLogger.i("SalemMainActivity", "Auto-follow: hit north margin (lat=$lat) — flipping to southbound")
                        toast("North border — switching to southbound")
                    } else if (lat < SOUTH_MARGIN && autoFollowPreferSouth) {
                        autoFollowPreferSouth = false
                        bounced = true
                        DebugLogger.i("SalemMainActivity", "Auto-follow: hit south margin (lat=$lat) — flipping to northbound")
                        toast("South border — switching to northbound")
                    }
                    if (bounced) {
                        autoFollowEmptyPoiCount = 0
                        pickAndFollowRandomAircraft()
                        return@postDelayed
                    }
                    val altFt = (state.baroAltitude ?: 0.0) * 3.28084
                    if (altFt < 10000 && altFt > 0) {
                        DebugLogger.i("SalemMainActivity", "Auto-follow: aircraft below 10,000 ft (${altFt.toInt()} ft) — switching")
                        toast("Aircraft descending (${altFt.toInt()} ft) — switching")
                        autoFollowEmptyPoiCount = 0
                        pickAndFollowRandomAircraft()
                        return@postDelayed
                    }
                    val poiCount = viewModel.places.value?.second?.size ?: 0
                    if (poiCount == 0) {
                        autoFollowEmptyPoiCount++
                        if (autoFollowEmptyPoiCount >= 2) {
                            DebugLogger.i("SalemMainActivity", "Auto-follow: no POIs $autoFollowEmptyPoiCount times — switching")
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
            DebugLogger.i("SalemMainActivity", "MBTA trains update — ${vehicles.size} vehicles")
            clearTrainMarkers()
            if (transitMarkersVisible) vehicles.forEach { addTrainMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        transitViewModel.mbtaSubway.observe(this) { vehicles ->
            DebugLogger.i("SalemMainActivity", "MBTA subway update — ${vehicles.size} vehicles")
            clearSubwayMarkers()
            if (transitMarkersVisible) vehicles.forEach { addSubwayMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        transitViewModel.mbtaBuses.observe(this) { vehicles ->
            DebugLogger.i("SalemMainActivity", "MBTA buses update — ${vehicles.size} vehicles")
            clearBusMarkers()
            if (transitMarkersVisible) vehicles.forEach { addBusMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        transitViewModel.mbtaStations.observe(this) { stations ->
            DebugLogger.i("SalemMainActivity", "MBTA stations update — ${stations.size} stations")
            clearStationMarkers()
            if (transitMarkersVisible && binding.mapView.zoomLevelDouble >= 12.0) {
                addStationMarkersBatched(stations)
            }
        }
        transitViewModel.mbtaBusStops.observe(this) { stops ->
            DebugLogger.i("SalemMainActivity", "MBTA bus stops update — ${stops.size} total stops loaded")
            allBusStops = stops
            refreshBusStopMarkersForViewport()
        }
        geofenceViewModel.tfrZones.observe(this) { zones ->
            DebugLogger.i("SalemMainActivity", "tfrZones → ${zones.size} TFRs")
            renderTfrOverlays(zones)
        }
        geofenceViewModel.cameraZones.observe(this) { zones ->
            DebugLogger.i("SalemMainActivity", "cameraZones → ${zones.size}")
            renderCameraOverlays(zones)
        }
        geofenceViewModel.schoolZones.observe(this) { zones ->
            DebugLogger.i("SalemMainActivity", "schoolZones → ${zones.size}")
            renderSchoolOverlays(zones)
        }
        geofenceViewModel.floodZones.observe(this) { zones ->
            DebugLogger.i("SalemMainActivity", "floodZones → ${zones.size}")
            renderFloodOverlays(zones)
        }
        geofenceViewModel.crossingZones.observe(this) { zones ->
            DebugLogger.i("SalemMainActivity", "crossingZones → ${zones.size}")
            renderCrossingOverlays(zones)
        }
        geofenceViewModel.databaseZones.observe(this) { zones ->
            DebugLogger.i("SalemMainActivity", "databaseZones → ${zones.size}")
            renderDatabaseOverlays(zones)
        }
        geofenceViewModel.geofenceAlerts.observe(this) { alerts ->
            DebugLogger.i("SalemMainActivity", "geofenceAlerts → ${alerts.size}")
            updateAlertsIcon(alerts)
            // Show banner for CRITICAL/EMERGENCY
            val critical = alerts.filter { it.severity.level >= com.example.locationmapapp.data.model.AlertSeverity.CRITICAL.level }
            if (critical.isNotEmpty()) {
                showGeofenceAlertBanner(critical.first())
            }
        }
        // ── Tour state + geofence + narration + directions observers ──
        observeTourState()
        observeGeofenceEvents()
        observeNarrationState()
        observeWalkingRoute()

        DebugLogger.i("SalemMainActivity", "observeViewModel() complete — all observers attached")
    }


    // =========================================================================
    // SEARCH
    // =========================================================================

    internal fun searchFromCurrentLocation() {
        val loc = viewModel.currentLocation.value?.point ?: GeoPoint(42.3601, -71.0589)
        DebugLogger.i("SalemMainActivity", "searchFromCurrentLocation() lat=${loc.latitude} lon=${loc.longitude}")
        viewModel.searchPoisAt(loc)
        toast("Searching POIs…")
    }

    internal fun triggerFullSearch(point: GeoPoint) {
        DebugLogger.i("SalemMainActivity", "triggerFullSearch() lat=${point.latitude} lon=${point.longitude}")
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
            icon     = MarkerIconHelper.forCategory(this@SalemMainActivity, "gps", 32)
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
                MarkerIconHelper.labeledDot(this@SalemMainActivity, place.category, place.name)
            } else {
                MarkerIconHelper.dot(this@SalemMainActivity, place.category)
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
        DebugLogger.i("SalemMainActivity", "refreshPoiMarkerIcons labeled=$labeled")
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
        DebugLogger.i("SalemMainActivity", "refreshVehicleMarkerIcons labeled=$labeled")
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
            DebugLogger.i("SalemMainActivity", "POI restore — loading cached POIs for visible area")
            binding.mapView.postDelayed({ loadCachedPoisForVisibleArea() }, 1500)
            scheduleSilentFill(point, 3000)
        }
        if (pendingMetarRestore) {
            pendingMetarRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("SalemMainActivity", "METAR restore triggered — loading for visible area")
                loadMetarsForVisibleArea()
            }, 1500)
        }
        if (pendingAircraftRestore) {
            pendingAircraftRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("SalemMainActivity", "Aircraft restore triggered — starting refresh")
                startAircraftRefresh()
            }, 1500)
        }
        if (pendingAutoFollowRestore) {
            pendingAutoFollowRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("SalemMainActivity", "Auto-follow restore triggered")
                startAutoFollowAircraft()
            }, 5000)
        }
        if (pendingCachedPoiLoad) {
            pendingCachedPoiLoad = false
            binding.mapView.postDelayed({
                DebugLogger.i("SalemMainActivity", "Loading cached POIs for visible area")
                loadCachedPoisForVisibleArea()
            }, 2000)
            scheduleSilentFill(point, 4000)
        }
        if (pendingWebcamRestore) {
            pendingWebcamRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("SalemMainActivity", "Webcam restore triggered — loading for visible area")
                loadWebcamsForVisibleArea()
            }, 2000)
        }
        if (pendingGeofenceRestore) {
            pendingGeofenceRestore = false
            binding.mapView.postDelayed({
                DebugLogger.i("SalemMainActivity", "Geofence overlay restore triggered — loading for visible area")
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
                        MarkerIconHelper.labeledDot(this@SalemMainActivity, place.category, place.name)
                    } else {
                        MarkerIconHelper.dot(this@SalemMainActivity, place.category)
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
                    Triple(cluster, color, MarkerIconHelper.clusterIcon(this@SalemMainActivity, cluster.count, color))
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
            icon     = MarkerIconHelper.forCategory(this@SalemMainActivity, "camera", 20)
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
                MarkerIconHelper.forCategory(this@SalemMainActivity, "camera", 20)
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
            DebugLogger.i("SalemMainActivity", "onRadarToggled: enabled=$enabled")
            toggleRadar()
            if (enabled) radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { weatherViewModel.refreshRadar() }
            else         radarScheduler.stop()
            refreshLayerBadge()
        }

        override fun onRadarVisibilityChanged(percent: Int) {
            DebugLogger.i("SalemMainActivity", "onRadarVisibilityChanged: $percent%")
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
            DebugLogger.i("SalemMainActivity", "onRadarFrequencyChanged: ${minutes}min")
            radarScheduler.setInterval(minutes)
            toast("Radar refresh: every $minutes min")
        }

        override fun onRadarAnimateToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onRadarAnimateToggled: $enabled")
            if (enabled) startRadarAnimation() else stopRadarAnimation()
        }

        override fun onRadarAnimSpeedChanged(ms: Int) {
            DebugLogger.i("SalemMainActivity", "onRadarAnimSpeedChanged: ${ms}ms")
            radarAnimSpeedMs = ms
            toast("Radar animation speed: ${ms}ms")
        }

        // ── Dark Mode ────────────────────────────────────────────────────────

        override fun onDarkModeToggled(dark: Boolean) {
            // Legacy toggle — delegate to tile source change
            val id = if (dark) TileSourceManager.Id.DARK else TileSourceManager.Id.SATELLITE
            onTileSourceChanged(id)
        }

        override fun onTileSourceChanged(tileSourceId: String) {
            DebugLogger.i("SalemMainActivity", "onTileSourceChanged: $tileSourceId")
            binding.mapView.setTileSource(TileSourceManager.buildSource(tileSourceId))
            // Enforce max zoom for satellite tiles (USGS caps at 16)
            applyTileSourceZoomLimits(tileSourceId)
            binding.mapView.invalidate()
            // Persist the choice
            getSharedPreferences(MenuPrefs.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(MenuPrefs.PREF_TILE_SOURCE, tileSourceId)
                .apply()
            toast("${TileSourceManager.label(tileSourceId)} tiles")
        }

        // ── Weather ───────────────────────────────────────────────────────────

        override fun onWeatherRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onWeatherRequested")
            showWeatherDialog()
        }

        override fun onMetarDisplayToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onMetarDisplayToggled: $enabled")
            if (enabled) { loadMetarsForVisibleArea(); toast("Loading METARs…") }
            else          clearMetarMarkers()
            refreshLayerBadge()
        }

        override fun onMetarFrequencyChanged(minutes: Int) {
            DebugLogger.i("SalemMainActivity", "onMetarFrequencyChanged: ${minutes}min")
            // STUB: wire to a periodic METAR refresh scheduler
            stub("metar_freq", minutes)
        }

        // ── Aircraft ─────────────────────────────────────────────────────────

        override fun onAircraftDisplayToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onAircraftDisplayToggled: $enabled")
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
                    DebugLogger.i("SalemMainActivity", "Auto-follow cancelled — aircraft layer turned off")
                }
            }
            refreshLayerBadge()
        }

        override fun onAircraftFrequencyChanged(seconds: Int) {
            DebugLogger.i("SalemMainActivity", "onAircraftFrequencyChanged: ${seconds}s")
            aircraftRefreshIntervalSec = seconds
            if (aircraftRefreshJob?.isActive == true) {
                startAircraftRefresh()
            }
            toast("Aircraft refresh: every $seconds sec")
        }

        // ── Transit ───────────────────────────────────────────────────────────

        override fun onMbtaStationsToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onMbtaStationsToggled: $enabled")
            if (enabled) {
                transitViewModel.fetchMbtaStations()
                toast("Loading train stations…")
            } else {
                transitViewModel.clearMbtaStations()
                clearStationMarkers()
            }
        }

        override fun onMbtaBusStopsToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onMbtaBusStopsToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onMbtaTrainsToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onMbtaTrainsFrequencyChanged: ${seconds}s")
            trainRefreshIntervalSec = seconds
            // Restart refresh loop with new interval if currently active
            if (trainRefreshJob?.isActive == true) {
                startTrainRefresh()
            }
        }

        override fun onMbtaSubwayToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onMbtaSubwayToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onMbtaSubwayFrequencyChanged: ${seconds}s")
            subwayRefreshIntervalSec = seconds
            if (subwayRefreshJob?.isActive == true) {
                startSubwayRefresh()
            }
        }

        override fun onMbtaBusesToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onMbtaBusesToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onMbtaBusesFrequencyChanged: ${seconds}s")
            busRefreshIntervalSec = seconds
            if (busRefreshJob?.isActive == true) {
                startBusRefresh()
            }
        }

        override fun onNationalAlertsToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onNationalAlertsToggled: $enabled")
            // STUB: parse https://alerts.weather.gov/cap/ma.php?x=1 (CAP/XML)
            stub("national_alerts", enabled)
        }

        override fun onNationalAlertsFrequencyChanged(minutes: Int) {
            DebugLogger.i("SalemMainActivity", "onNationalAlertsFrequencyChanged: ${minutes}min")
            stub("national_alerts_freq", minutes)
        }

        // ── Cameras ───────────────────────────────────────────────────────────

        override fun onWebcamToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onWebcamToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onWebcamCategoriesChanged: $categories")
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
            DebugLogger.i("SalemMainActivity", "onPoiLayerToggled: layerId=$layerId enabled=$enabled")
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
            DebugLogger.i("SalemMainActivity", "onGpsRecordingToggled: $enabled")
            // STUB: start/stop writing GeoPoints to local Room DB or GPX file
            stub("record_gps", enabled)
        }

        override fun onBuildStoryRequested() {
            DebugLogger.i("SalemMainActivity", "onBuildStoryRequested")
            // STUB: package stored GPS points and launch URL in browser
            stub("build_story")
        }

        override fun onAnalyzeTodayRequested() {
            DebugLogger.i("SalemMainActivity", "onAnalyzeTodayRequested")
            // STUB: launch URL / WebView with today's travel analysis
            stub("analyze_today")
        }

        override fun onTravelAnomaliesRequested() {
            DebugLogger.i("SalemMainActivity", "onTravelAnomaliesRequested")
            // STUB: launch URL / WebView with anomaly detection report
            stub("travel_anomalies")
        }

        override fun onEmailGpxRequested() {
            DebugLogger.i("SalemMainActivity", "onEmailGpxRequested")
            // STUB: serialize stored track to GPX, fire ACTION_SEND intent
            stub("email_gpx")
        }

        override fun onDebugLogRequested() {
            DebugLogger.i("SalemMainActivity", "onDebugLogRequested")
            startActivity(Intent(this@SalemMainActivity, DebugLogActivity::class.java))
        }

        override fun onAutoFollowAircraftToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onAutoFollowAircraftToggled: $enabled")
            if (enabled) startAutoFollowAircraft() else stopAutoFollowAircraft()
        }

        override fun onPopulatePoisToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onPopulatePoisToggled: $enabled")
            if (enabled) startPopulatePois() else stopPopulatePois()
        }

        override fun onProbe10kmRequested() {
            if (probe10kmJob?.isActive == true) {
                DebugLogger.i("SalemMainActivity", "onProbe10kmRequested — stopping")
                stopProbe10km()
            } else {
                DebugLogger.i("SalemMainActivity", "onProbe10kmRequested — starting")
                startProbe10km()
            }
        }

        override fun onFillProbeRequested() {
            DebugLogger.i("SalemMainActivity", "onFillProbeRequested — stub")
            toast("Fill Probe Populate — coming soon")
        }

        override fun onSilentFillDebugToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onSilentFillDebugToggled: $enabled")
            // Toggle only controls banner visibility — silent fill always runs
            if (!enabled) hideSilentFillBanner()
        }

        override fun onGpsModeToggled(autoGps: Boolean) {
            DebugLogger.i("SalemMainActivity", "onGpsModeToggled: autoGps=$autoGps")
            toggleLocationMode()
        }

        // ── Alerts / Geofence ─────────────────────────────────────────────────

        override fun onAlertsRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onAlertsRequested")
            val anchor = alertsIconView ?: binding.toolbar
            appBarMenuManager.showAlertsMenu(anchor)
        }

        override fun onTfrOverlayToggled(enabled: Boolean) {
            DebugLogger.i("SalemMainActivity", "onTfrOverlayToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onCameraOverlayToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onSchoolOverlayToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onFloodOverlayToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onCrossingOverlayToggled: $enabled")
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
            DebugLogger.i("SalemMainActivity", "onAlertSoundToggled: $enabled")
            toast(if (enabled) "Alert sounds enabled" else "Alert sounds disabled")
        }

        override fun onAlertDistanceChanged(nm: Int) {
            DebugLogger.i("SalemMainActivity", "onAlertDistanceChanged: ${nm}nm")
            geofenceViewModel.geofenceEngine.proximityThresholdNm = nm.toDouble()
            toast("Alert distance: $nm NM")
        }

        override fun onDatabaseManagerRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onDatabaseManagerRequested")
            showDatabaseManagerDialog()
        }

        // ── Find / Legend ─────────────────────────────────────────────────────

        override fun onFindRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onFindRequested")
            showFindDialog()
        }

        override fun onLegendRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onLegendRequested")
            showLegendDialog()
        }

        override fun onGoToLocationRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onGoToLocationRequested")
            showGoToLocationDialog()
        }

        // ── Social ────────────────────────────────────────────────────────────

        override fun onSocialRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onSocialRequested")
            if (socialViewModel.isLoggedIn()) showProfileDialog() else showAuthDialog()
        }

        override fun onChatRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onChatRequested")
            if (!socialViewModel.isLoggedIn()) {
                toast("Register first to use Chat")
                showAuthDialog()
            } else {
                showChatDialog()
            }
        }

        override fun onProfileRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onProfileRequested")
            if (socialViewModel.isLoggedIn()) showProfileDialog() else showAuthDialog()
        }

        // ── Toolbar actions ───────────────────────────────────────────────────

        override fun onHomeRequested() {
            resetIdleTimer()
            // S112: Tapping home stops the walk simulator and reverts to real GPS.
            // The walk sim was pushing synthetic positions via setManualLocation;
            // stopping it lets the real LocationManager fixes flow through again.
            if (walkSimRunning) {
                DebugLogger.i("SalemMainActivity", "onHomeRequested — stopping walk simulator, reverting to real GPS")
                stopWalkSim()
            }
            val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, MODE_PRIVATE)
            if (prefs.getBoolean(MenuPrefs.PREF_HOME_SET, false)) {
                val lat = prefs.getFloat(MenuPrefs.PREF_HOME_LAT, 0f).toDouble()
                val lon = prefs.getFloat(MenuPrefs.PREF_HOME_LON, 0f).toDouble()
                DebugLogger.i("SalemMainActivity", "onHomeRequested — centering on saved home ($lat,$lon)")
                binding.mapView.controller.animateTo(org.osmdroid.util.GeoPoint(lat, lon), 18.0, 800L)
            } else {
                DebugLogger.i("SalemMainActivity", "onHomeRequested — centering on GPS (no home set)")
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
                DebugLogger.i("SalemMainActivity", "Home set to ${center.latitude},${center.longitude}")
                toast("Home location saved")
            } else {
                // Already cleared in AppBarMenuManager
                DebugLogger.i("SalemMainActivity", "Home location cleared")
                toast("Home location cleared")
            }
        }

        override fun onAboutRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onAboutRequested")
            showAboutDialog()
        }

        // ── Tours ─────────────────────────────────────────────────────────────

        override fun onTourRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onTourRequested")
            showTourSelectionDialog()
        }

        override fun onEventsRequested() {
            resetIdleTimer()
            DebugLogger.i("SalemMainActivity", "onEventsRequested")
            showEventsDialog()
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
