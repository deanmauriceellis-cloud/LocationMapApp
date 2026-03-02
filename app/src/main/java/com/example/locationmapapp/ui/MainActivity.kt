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
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.locationmapapp.R
import com.example.locationmapapp.ui.menu.PoiCategories
import com.example.locationmapapp.ui.menu.PoiLayerId
import com.example.locationmapapp.databinding.ActivityMainBinding
import com.example.locationmapapp.ui.menu.AppBarMenuManager
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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var appBarMenuManager: AppBarMenuManager
    private val radarScheduler = RadarRefreshScheduler()

    private val metarMarkers      = mutableListOf<Marker>()
    private val poiMarkers        = mutableMapOf<String, MutableList<Marker>>()
    private val trainMarkers      = mutableListOf<Marker>()
    private val subwayMarkers     = mutableListOf<Marker>()
    private val busMarkers        = mutableListOf<Marker>()
    private var gpsMarker: Marker? = null
    private var radarTileOverlay: TilesOverlay? = null
    private var radarAlphaPercent: Int = 70  // 0–100, default 70%
    private var fabMenuOpen = false

    // MBTA auto-refresh
    private var trainRefreshJob: Job? = null
    private var trainRefreshIntervalSec: Int = 30
    private var subwayRefreshJob: Job? = null
    private var subwayRefreshIntervalSec: Int = 30
    private var busRefreshJob: Job? = null
    private var busRefreshIntervalSec: Int = 60

    // Vehicle / aircraft follow mode
    private var followedVehicleId: String? = null
    private var followedAircraftIcao: String? = null
    private var followBanner: TextView? = null

    // Debounced cache-only POI loader on map scroll
    private var cachePoiJob: Job? = null
    // Debounced aircraft reload on scroll/zoom
    private var aircraftReloadJob: Job? = null

    // Aircraft tracking
    private val aircraftMarkers = mutableListOf<Marker>()
    private var aircraftRefreshJob: Job? = null
    private var aircraftRefreshIntervalSec: Int = 60
    private var followedAircraftRefreshJob: Job? = null
    private var lastFollowedAircraftState: com.example.locationmapapp.data.model.AircraftState? = null
    // Auto-follow random high-altitude aircraft for POI cache building
    private var autoFollowAircraftJob: Job? = null
    private var followedAircraftFailCount: Int = 0
    private var autoFollowEmptyPoiCount: Int = 0
    // Bounce direction — auto-flip when aircraft nears CONUS boundary
    private var autoFollowPreferWest: Boolean = true
    private var autoFollowPreferSouth: Boolean = false

    // Station markers
    private val stationMarkers = mutableListOf<Marker>()

    // Bus stop markers (viewport-filtered from allBusStops)
    private val busStopMarkers = mutableListOf<Marker>()
    private var allBusStops: List<com.example.locationmapapp.data.model.MbtaStop> = emptyList()
    private var busStopReloadJob: Job? = null

    // Flight trail overlays (aircraft follow mode)
    private val flightTrailPoints = mutableListOf<com.example.locationmapapp.data.model.FlightPathPoint>()
    private val flightTrailOverlays = mutableListOf<Polyline>()

    // Webcam tracking
    private val webcamMarkers = mutableListOf<Marker>()
    private var webcamReloadJob: Job? = null
    private var pendingWebcamRestore = false

    // Populate POIs scanner
    private var populateJob: Job? = null
    private var scanningMarker: Marker? = null

    // Silent background POI fill — single center search on startup / position change
    private var silentFillJob: Job? = null
    private var silentFillRunnable: Runnable? = null

    // POI label zoom threshold tracking
    private var poiLabelsShowing = false
    private var transitMarkersVisible = true

    // Deferred restore — wait for first real GPS fix so the map has a valid bounding box
    private var pendingPoiRestore = false
    private var pendingMetarRestore = false
    private var pendingCachedPoiLoad = false

    private var pendingAircraftRestore = false
    private var pendingAutoFollowRestore = false
    private var initialCenterDone = false   // only auto-center map on FIRST GPS fix

    /** Load METARs for the current visible map bounding box. */
    private fun loadMetarsForVisibleArea() {
        val bb = binding.mapView.boundingBox
        viewModel.loadMetars(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }

    /** Debounced: reload aircraft 1s after user stops scrolling/zooming. */
    private fun scheduleAircraftReload() {
        if (aircraftRefreshJob?.isActive != true) return  // aircraft tracking not enabled
        aircraftReloadJob?.cancel()
        aircraftReloadJob = lifecycleScope.launch {
            delay(1000)
            loadAircraftForVisibleArea()
        }
    }

    /** Load aircraft for the current visible map bounding box. Requires zoom >= 10 to avoid massive queries. */
    private fun loadAircraftForVisibleArea() {
        val zoom = binding.mapView.zoomLevelDouble
        if (zoom < 10.0) {
            DebugLogger.d("MainActivity", "Aircraft skipped — zoom ${zoom.toInt()} < 10")
            return
        }
        val bb = binding.mapView.boundingBox
        DebugLogger.i("MainActivity", "loadAircraftForVisibleArea zoom=${zoom.toInt()} bbox=${bb.latSouth},${bb.lonWest},${bb.latNorth},${bb.lonEast}")
        viewModel.loadAircraft(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }

    // v1.4: guard to prevent startGpsUpdatesAndCenter() running twice
    // (fast-path pre-check + launcher callback can both fire on first run).
    private var locationUpdatesStarted = false

    // v1.4: GPS is managed entirely by the ViewModel via GMS FusedLocationProviderClient.

    private val locationPermissionLauncher = registerForActivityResult(
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

        // ── Toolbar ───────────────────────────────────────────────────────────
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "LocationMap"   // short title to leave room for menu items
        }
        DebugLogger.i("MainActivity", "setSupportActionBar complete — title='LocationMap'")

        // ── Menu manager (Phase 1 — just wires intent, no items inflated yet) ─
        appBarMenuManager = AppBarMenuManager(
            context           = this,
            toolbar           = binding.toolbar,
            viewModel         = viewModel,
            menuEventListener = menuEventListenerImpl
        )
        appBarMenuManager.setupToolbarMenus()
        DebugLogger.i("MainActivity", "AppBarMenuManager.setupToolbarMenus() complete — waiting for onCreateOptionsMenu")

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

    // ── Phase 2: items exist on toolbar NOW — wire click handlers ─────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        DebugLogger.i("MainActivity", "onCreateOptionsMenu() — inflating menu_main_toolbar")
        menuInflater.inflate(R.menu.menu_main_toolbar, menu)
        DebugLogger.i("MainActivity", "onCreateOptionsMenu() — ${menu.size()} items inflated:")
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            DebugLogger.i("MainActivity", "  [$i] id=0x${item.itemId.toString(16)} " +
                    "title='${item.title}' visible=${item.isVisible} enabled=${item.isEnabled}")
        }
        appBarMenuManager.onMenuInflated(menu)
        DebugLogger.i("MainActivity", "onCreateOptionsMenu() — onMenuInflated() called, click listeners wired")
        return true
    }

    override fun onStart() {
        super.onStart()
        DebugLogger.i("MainActivity", "onStart() toolbar.childCount=${binding.toolbar.childCount} " +
                "toolbarMenu.size=${binding.toolbar.menu?.size()}")
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)

        // Populate is never auto-restored — user must pick a location and start manually
        prefs.edit().putBoolean(AppBarMenuManager.PREF_POPULATE_POIS, false).apply()

        // Restore radar — add the tile overlay AND restart the refresh scheduler
        val radarOn = prefs.getBoolean(AppBarMenuManager.PREF_RADAR_ON, true)
        DebugLogger.i("MainActivity", "onStart() radarOn=$radarOn interval=${appBarMenuManager.radarUpdateMinutes}min")
        if (radarOn) {
            if (radarTileOverlay == null) {
                addRadarOverlay()
                DebugLogger.i("MainActivity", "Radar overlay restored from onStart()")
            }
            radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { viewModel.refreshRadar() }
            DebugLogger.i("MainActivity", "Radar scheduler restarted from onStart()")
        }

        // Restore MBTA layers from persisted toggle state
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_TRAINS, true) && trainRefreshJob?.isActive != true) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA trains")
            startTrainRefresh()
        }
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_SUBWAY, true) && subwayRefreshJob?.isActive != true) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA subway")
            startSubwayRefresh()
        }
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_BUSES, true) && busRefreshJob?.isActive != true) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA buses")
            startBusRefresh()
        }
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_STATIONS, true) && stationMarkers.isEmpty()) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA stations")
            viewModel.fetchMbtaStations()
        }
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_BUS_STOPS, false) && allBusStops.isEmpty()) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA bus stops")
            viewModel.fetchMbtaBusStops()
        }

        // Defer METAR restore until GPS fix so the map has a real bounding box
        if (prefs.getBoolean(AppBarMenuManager.PREF_METAR_DISPLAY, true)) {
            pendingMetarRestore = true
            DebugLogger.i("MainActivity", "onStart() METAR restore deferred — waiting for GPS fix")
        }

        // Defer Aircraft restore
        if (prefs.getBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, false)) {
            pendingAircraftRestore = true
            DebugLogger.i("MainActivity", "onStart() Aircraft restore deferred — waiting for GPS fix")
        }

        // Restore auto-follow aircraft if it was active
        if (prefs.getBoolean(AppBarMenuManager.PREF_AUTO_FOLLOW_AIRCRAFT, false)) {
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
        if (prefs.getBoolean(AppBarMenuManager.PREF_WEBCAMS_ON, true)) {
            pendingWebcamRestore = true
            DebugLogger.i("MainActivity", "onStart() Webcam restore deferred — waiting for GPS fix")
        }

        // Always load full cached POI coverage after GPS fix
        pendingCachedPoiLoad = true
    }

    override fun onStop()   { super.onStop();   radarScheduler.stop(); DebugLogger.i("MainActivity","onStop()") }
    override fun onResume() {
        super.onResume(); binding.mapView.onResume()
        DebugHttpServer.endpoints = DebugEndpoints(this, viewModel)
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
    private fun handleDebugIntent(intent: Intent?) {
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
            AppBarMenuManager.PREF_MBTA_STATIONS   to { menuEventListenerImpl.onMbtaStationsToggled(it) },
            AppBarMenuManager.PREF_MBTA_BUS_STOPS  to { menuEventListenerImpl.onMbtaBusStopsToggled(it) },
            AppBarMenuManager.PREF_MBTA_TRAINS     to { menuEventListenerImpl.onMbtaTrainsToggled(it) },
            AppBarMenuManager.PREF_MBTA_SUBWAY     to { menuEventListenerImpl.onMbtaSubwayToggled(it) },
            AppBarMenuManager.PREF_MBTA_BUSES      to { menuEventListenerImpl.onMbtaBusesToggled(it) },
            AppBarMenuManager.PREF_RADAR_ON         to { menuEventListenerImpl.onRadarToggled(it) },
            AppBarMenuManager.PREF_METAR_DISPLAY    to { menuEventListenerImpl.onMetarDisplayToggled(it) },
            AppBarMenuManager.PREF_AIRCRAFT_DISPLAY to { menuEventListenerImpl.onAircraftDisplayToggled(it) },
            AppBarMenuManager.PREF_WEBCAMS_ON       to { menuEventListenerImpl.onWebcamToggled(it) },
            AppBarMenuManager.PREF_WEATHER_ALERTS   to { menuEventListenerImpl.onWeatherAlertsToggled(it) },
            AppBarMenuManager.PREF_NAT_ALERTS       to { menuEventListenerImpl.onNationalAlertsToggled(it) },
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

    private fun setupMap() {
        DebugLogger.i("MainActivity", "setupMap() — initial center=US_DEFAULT zoom=6 (will update on GPS fix)")
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
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
                return false
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                DebugLogger.i("MainActivity", "Long-press → manual mode at ${p.latitude},${p.longitude}")
                // Stop populate scanner and silent fill — user is moving to a new location
                if (populateJob != null) stopPopulatePois()
                stopSilentFill()
                viewModel.setManualLocation(p)
                // Zoom to 14 if currently zoomed out; leave alone if already 14+
                val targetZoom = if (binding.mapView.zoomLevelDouble < 14.0) 14.0 else binding.mapView.zoomLevelDouble
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
                toast("Manual mode — searching POIs…")
                return true
            }
        }
        binding.mapView.overlays.add(0, MapEventsOverlay(eventsReceiver))
        DebugLogger.i("MainActivity", "Map configured — overlays=${binding.mapView.overlays.size}")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoomSlider() {
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
                scheduleAircraftReload()
                scheduleBusStopReload()
                // Debounce: load cached POIs for visible bbox 500ms after scrolling stops
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(500)
                    if (findFilterActive) loadFilteredPois() else loadCachedPoisForVisibleArea()
                }
                // Suppress webcam reloads while populate scanner is running
                if (populateJob == null) scheduleWebcamReload()
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                updateZoomBubble()
                scheduleAircraftReload()
                scheduleBusStopReload()
                if (populateJob == null) scheduleWebcamReload()
                // Hide transit markers when zoomed out to 10 or less
                val zoom = binding.mapView.zoomLevelDouble
                if (zoom <= 10.0) {
                    if (transitMarkersVisible) {
                        transitMarkersVisible = false
                        clearTrainMarkers(); clearSubwayMarkers(); clearBusMarkers()
                        clearStationMarkers(); clearBusStopMarkers()
                        binding.mapView.invalidate()
                    }
                } else if (!transitMarkersVisible) {
                    transitMarkersVisible = true
                    // Re-add from latest LiveData values
                    viewModel.mbtaTrains.value?.let { it.forEach { v -> addTrainMarker(v) } }
                    viewModel.mbtaSubway.value?.let { it.forEach { v -> addSubwayMarker(v) } }
                    viewModel.mbtaBuses.value?.let { it.forEach { v -> addBusMarker(v) } }
                    viewModel.mbtaStations.value?.let { it.forEach { s -> addStationMarker(s) } }
                    refreshBusStopMarkersForViewport()
                    binding.mapView.invalidate()
                }
                // Refresh POI marker icons when crossing the zoom-18 label threshold
                val nowLabeled = zoom >= 18.0
                if (nowLabeled != poiLabelsShowing) {
                    poiLabelsShowing = nowLabeled
                    refreshPoiMarkerIcons()
                }
                return false
            }
        })

        // Initial position
        binding.zoomBubble.post { updateZoomBubble() }
    }

    private fun updateZoomBubble() {
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

    private fun requestLocationPermission() {
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
    private fun startGpsUpdatesAndCenter() {
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
    private fun toggleAircraftFromFab() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        val wasOn = prefs.getBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, false)
        val nowOn = !wasOn
        prefs.edit().putBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, nowOn).apply()
        if (nowOn) {
            startAircraftRefresh()
            toast("Aircraft tracking ON")
        } else {
            stopAircraftRefresh()
            viewModel.clearAircraft()
            clearAircraftMarkers()
            // Cancel auto-follow if aircraft layer toggled off via FAB
            if (autoFollowAircraftJob?.isActive == true) {
                autoFollowAircraftJob?.cancel()
                autoFollowAircraftJob = null
                prefs.edit().putBoolean(AppBarMenuManager.PREF_AUTO_FOLLOW_AIRCRAFT, false).apply()
                stopFollowing()
                DebugLogger.i("MainActivity", "Auto-follow cancelled — aircraft layer FAB off")
            }
            toast("Aircraft tracking OFF")
        }
    }

    // =========================================================================
    // FAB SPEED DIAL
    // =========================================================================

    private data class FabDef(val label: String, val iconRes: Int, val color: String, val action: () -> Unit)

    private fun buildFabSpeedDial() {
        DebugLogger.i("MainActivity", "buildFabSpeedDial() start")
        val defs = listOf(
            FabDef("Radar",        R.drawable.ic_radar,         "#0277BD") { toggleRadar() },
            FabDef("METAR",        R.drawable.ic_metar,         "#1B5E20") { loadMetarsForVisibleArea(); toast("Loading METARs…") },
            FabDef("Aircraft",     R.drawable.ic_aircraft,      "#1565C0") { toggleAircraftFromFab() },
            FabDef("Search Here",  R.drawable.ic_search,        "#4A148C") { searchFromCurrentLocation() },
            FabDef("Weather",      R.drawable.ic_weather_alert, "#006064") { viewModel.fetchWeatherAlerts() },
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

    private fun updateFabTriggerIcon(open: Boolean) {
        val res = if (open) android.R.drawable.ic_menu_close_clear_cancel else R.drawable.ic_radar
        val icon = ContextCompat.getDrawable(this, res)!!.mutate()
        DrawableCompat.setTint(icon, Color.WHITE)
        binding.fabMain.setImageDrawable(icon)
    }

    private fun openFabMenu()  { fabMenuOpen = true;  binding.fabMenu.visibility = View.VISIBLE; updateFabTriggerIcon(true) }
    private fun closeFabMenu() { fabMenuOpen = false; binding.fabMenu.visibility = View.GONE;    updateFabTriggerIcon(false) }

    // =========================================================================
    // OBSERVERS
    // =========================================================================

    private fun observeViewModel() {
        DebugLogger.i("MainActivity", "observeViewModel() — attaching all observers")

        viewModel.currentLocation.observe(this) { point ->
            updateGpsMarker(point)
            // Only auto-center on the first GPS fix
            if (followedVehicleId == null && !initialCenterDone) {
                initialCenterDone = true
                DebugLogger.i("MainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — initial center zoom=14")
                binding.mapView.controller.animateTo(point)
                binding.mapView.controller.setZoom(14.0)
            } else {
                DebugLogger.d("MainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} (marker only)")
            }
            // Fire deferred POI display now that we have a real GPS position
            // Display is driven by /pois/bbox — no need to fire per-category Overpass queries on startup
            if (pendingPoiRestore) {
                pendingPoiRestore = false
                DebugLogger.i("MainActivity", "POI restore — loading cached POIs for visible area")
                binding.mapView.postDelayed({ loadCachedPoisForVisibleArea() }, 1500)
                // Silent background fill — search Overpass at GPS position to populate cache
                scheduleSilentFill(point, 3000)
            }
            // Fire deferred METAR load after the map animation settles
            if (pendingMetarRestore) {
                pendingMetarRestore = false
                binding.mapView.postDelayed({
                    DebugLogger.i("MainActivity", "METAR restore triggered — loading for visible area")
                    loadMetarsForVisibleArea()
                }, 1500)
            }
            // Fire deferred Aircraft load after the map animation settles
            if (pendingAircraftRestore) {
                pendingAircraftRestore = false
                binding.mapView.postDelayed({
                    DebugLogger.i("MainActivity", "Aircraft restore triggered — starting refresh")
                    startAircraftRefresh()
                }, 1500)
            }
            // Restore auto-follow after aircraft data has had time to load
            if (pendingAutoFollowRestore) {
                pendingAutoFollowRestore = false
                binding.mapView.postDelayed({
                    DebugLogger.i("MainActivity", "Auto-follow restore triggered")
                    startAutoFollowAircraft()
                }, 5000)  // extra delay so aircraft list is populated
            }
            // Load cached POIs for visible area
            if (pendingCachedPoiLoad) {
                pendingCachedPoiLoad = false
                binding.mapView.postDelayed({
                    DebugLogger.i("MainActivity", "Loading cached POIs for visible area")
                    loadCachedPoisForVisibleArea()
                }, 2000)
                // Silent fill to ensure cache is populated at restored position
                scheduleSilentFill(point, 4000)
            }
            // Webcam restore
            if (pendingWebcamRestore) {
                pendingWebcamRestore = false
                binding.mapView.postDelayed({
                    DebugLogger.i("MainActivity", "Webcam restore triggered — loading for visible area")
                    loadWebcamsForVisibleArea()
                }, 2000)
            }
        }
        viewModel.places.observe(this) { (layerId, places) ->
            DebugLogger.i("MainActivity", "places → ${places.size} results layerId=$layerId")
            if (layerId == "bbox") {
                // Viewport bbox fetch — replace ALL POI markers with only visible results
                replaceAllPoiMarkers(places)
            } else {
                // User-initiated search or category restore — data goes to proxy cache.
                // Trigger a bbox refresh so the newly cached POIs appear on screen.
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(1000)
                    loadCachedPoisForVisibleArea()
                }
            }
        }
        viewModel.metars.observe(this) { metars ->
            DebugLogger.i("MainActivity", "metars → ${metars.size} stations")
            clearMetarMarkers(); metars.forEach { addMetarMarker(it) }
            bringStationMarkersToFront()
        }
        viewModel.weatherAlerts.observe(this) { alerts ->
            DebugLogger.i("MainActivity", "weatherAlerts → ${alerts.size} alerts")
            if (alerts.isNotEmpty()) toast("${alerts.size} weather alert(s) active")
        }
        viewModel.radarRefreshTick.observe(this) {
            DebugLogger.i("MainActivity", "radarRefreshTick → refreshing overlay")
            refreshRadarOverlay()
        }
        viewModel.webcams.observe(this) { webcams ->
            DebugLogger.i("MainActivity", "webcams → ${webcams.size} on map")
            clearWebcamMarkers()
            webcams.forEach { addWebcamMarker(it) }
            bringStationMarkersToFront()
        }
        viewModel.error.observe(this) { msg ->
            DebugLogger.e("MainActivity", "VM error: $msg")
            toast(msg)
        }
        viewModel.aircraft.observe(this) { aircraft ->
            DebugLogger.i("MainActivity", "aircraft → ${aircraft.size} on map")
            clearAircraftMarkers()
            // Merge followed aircraft into list if it's outside the bbox results
            val icao = followedAircraftIcao
            val merged = if (icao != null && aircraft.none { it.icao24 == icao }) {
                val followed = lastFollowedAircraftState
                if (followed != null) aircraft + followed else aircraft
            } else aircraft
            merged.forEach { addAircraftMarker(it) }
            updateFollowedAircraft(merged)
        }
        viewModel.followedAircraft.observe(this) { state ->
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
            lastFollowedAircraftState = state
            DebugLogger.i("MainActivity", "Followed aircraft update: ${state.callsign ?: state.icao24} at ${state.lat},${state.lon}")
            binding.mapView.controller.animateTo(state.toGeoPoint())
            showAircraftFollowBanner(state)
            // Grow the flight trail with the new position
            appendToFlightTrail(state)
            // Pre-fill POI cache at aircraft's current position
            val point = state.toGeoPoint()
            DebugLogger.i("MainActivity", "Aircraft follow POI prefetch at ${point.latitude},${point.longitude}")
            viewModel.searchPoisAt(point)
            // Refresh full cache display after prefetch; check for empty POI zone (auto-follow)
            binding.mapView.postDelayed({
                loadCachedPoisForVisibleArea()
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
        viewModel.mbtaTrains.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA trains update — ${vehicles.size} vehicles")
            clearTrainMarkers()
            if (transitMarkersVisible) vehicles.forEach { addTrainMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        viewModel.mbtaSubway.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA subway update — ${vehicles.size} vehicles")
            clearSubwayMarkers()
            if (transitMarkersVisible) vehicles.forEach { addSubwayMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        viewModel.mbtaBuses.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA buses update — ${vehicles.size} vehicles")
            clearBusMarkers()
            if (transitMarkersVisible) vehicles.forEach { addBusMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        viewModel.mbtaStations.observe(this) { stations ->
            DebugLogger.i("MainActivity", "MBTA stations update — ${stations.size} stations")
            clearStationMarkers()
            if (transitMarkersVisible) stations.forEach { addStationMarker(it) }
        }
        viewModel.mbtaBusStops.observe(this) { stops ->
            DebugLogger.i("MainActivity", "MBTA bus stops update — ${stops.size} total stops loaded")
            allBusStops = stops
            refreshBusStopMarkersForViewport()
        }
        DebugLogger.i("MainActivity", "observeViewModel() complete — all observers attached")
    }

    // =========================================================================
    // RADAR
    // =========================================================================

    fun toggleRadar() {
        DebugLogger.i("MainActivity", "toggleRadar() — currently ${if (radarTileOverlay != null) "ON" else "OFF"}")
        if (radarTileOverlay != null) {
            binding.mapView.overlays.remove(radarTileOverlay); radarTileOverlay = null
            binding.mapView.invalidate()
            DebugLogger.i("MainActivity", "Radar overlay removed")
        } else {
            addRadarOverlay()
        }
    }

    private fun addRadarOverlay() {
        try {
            // NWS NEXRAD composite radar via Iowa State Mesonet (no API key, no timestamp fetch)
            val src = org.osmdroid.tileprovider.tilesource.XYTileSource(
                "NWS-NEXRAD", 0, 12, 256, ".png",
                arrayOf("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/")
            )
            val tp = org.osmdroid.tileprovider.MapTileProviderBasic(applicationContext)
            tp.tileSource = src
            radarTileOverlay = TilesOverlay(tp, applicationContext).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor       = Color.TRANSPARENT
                val alpha = radarAlphaPercent / 100f
                val matrix = android.graphics.ColorMatrix().apply { setScale(1f, 1f, 1f, alpha) }
                setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
            }
            binding.mapView.overlays.add(radarTileOverlay)
            binding.mapView.invalidate()
            DebugLogger.i("MainActivity", "Radar overlay added (NWS NEXRAD)")
        } catch (e: Exception) {
            DebugLogger.e("MainActivity", "Radar overlay FAILED: ${e.message}")
            toast("Radar overlay failed: ${e.message}")
        }
    }

    private fun refreshRadarOverlay() {
        if (radarTileOverlay != null) {
            binding.mapView.overlays.remove(radarTileOverlay); radarTileOverlay = null
            addRadarOverlay()
            DebugLogger.i("MainActivity", "Radar tiles refreshed")
        }
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    private fun searchFromCurrentLocation() {
        val loc = viewModel.currentLocation.value ?: GeoPoint(42.3601, -71.0589)
        DebugLogger.i("MainActivity", "searchFromCurrentLocation() lat=${loc.latitude} lon=${loc.longitude}")
        viewModel.searchPoisAt(loc)
        toast("Searching POIs…")
    }

    private fun triggerFullSearch(point: GeoPoint) {
        DebugLogger.i("MainActivity", "triggerFullSearch() lat=${point.latitude} lon=${point.longitude}")
        viewModel.searchPoisAt(point)
        toast("Searching around tapped location…")
    }

    // =========================================================================
    // MARKERS
    // =========================================================================

    private fun updateGpsMarker(point: GeoPoint) {
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

    private fun addPoiMarker(layerId: String, place: com.example.locationmapapp.data.model.PlaceResult) {
        val labeled = binding.mapView.zoomLevelDouble >= 18.0
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
        }
        poiMarkers.getOrPut(layerId) { mutableListOf() }.add(m)
        binding.mapView.overlays.add(m)
        binding.mapView.invalidate()
    }

    /** Swap all POI marker icons between dot and labeled-dot when crossing zoom 18. */
    private fun refreshPoiMarkerIcons() {
        val labeled = binding.mapView.zoomLevelDouble >= 18.0
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

    /** Ask the proxy for cached POIs within the visible map bounding box.
     *  Skips loading at zoom ≤ 8 — viewport too large, too many markers. */
    private fun loadCachedPoisForVisibleArea() {
        if (binding.mapView.zoomLevelDouble <= 8.0) {
            // Clear POI markers when zoomed out too far
            replaceAllPoiMarkers(emptyList())
            return
        }
        val bb = binding.mapView.boundingBox
        viewModel.loadCachedPoisForBbox(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }

    private fun addMetarMarker(station: com.example.locationmapapp.data.model.MetarStation) {
        val fltCatColor = when (station.flightCategory?.uppercase()) {
            "VFR"  -> Color.parseColor("#2E7D32")
            "MVFR" -> Color.parseColor("#1565C0")
            "IFR"  -> Color.parseColor("#C62828")
            "LIFR" -> Color.parseColor("#AD1457")
            else   -> Color.parseColor("#546E7A")
        }
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(station.lat, station.lon)
            icon     = buildMetarStationIcon(station, fltCatColor)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = "${station.stationId} — ${station.name ?: station.flightCategory ?: "?"}"
            snippet  = buildMetarSnippet(station)
        }
        metarMarkers.add(m); binding.mapView.overlays.add(m); binding.mapView.invalidate()
    }

    private fun buildMetarStationIcon(s: com.example.locationmapapp.data.model.MetarStation, color: Int): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density
        val tempF = s.tempC?.let { "%.0f°".format(it * 9.0 / 5.0 + 32) } ?: "?"
        val wind = if (s.windSpeedKt != null && s.windSpeedKt > 0) {
            val dir = s.windDirDeg?.let { windDirToArrow(it) } ?: ""
            val gust = if (s.windGustKt != null) "G${s.windGustKt}" else ""
            "$dir${s.windSpeedKt}$gust"
        } else "Calm"
        val wx = s.wxString ?: s.skyCover ?: ""

        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            this.color = color
        }
        val smallPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9 * density
            typeface = android.graphics.Typeface.DEFAULT
            this.color = Color.parseColor("#333333")
        }

        val lines = listOf(tempF, wind, wx).filter { it.isNotBlank() }
        val lineHeight = textPaint.textSize + 2 * density
        val widths = lines.mapIndexed { i, line -> if (i == 0) textPaint.measureText(line) else smallPaint.measureText(line) }
        val maxW = (widths.maxOrNull() ?: 30f) + 8 * density
        val totalH = lineHeight * lines.size + 4 * density

        val bmp = android.graphics.Bitmap.createBitmap(maxW.toInt(), totalH.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        // Background
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(200, 255, 255, 255)
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, maxW, totalH, 4 * density, 4 * density, bgPaint)
        // Border
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1 * density
        }
        canvas.drawRoundRect(0f, 0f, maxW, totalH, 4 * density, 4 * density, borderPaint)
        // Text
        lines.forEachIndexed { i, line ->
            val p = if (i == 0) textPaint else smallPaint
            val x = (maxW - p.measureText(line)) / 2
            val y = lineHeight * (i + 1)
            canvas.drawText(line, x, y, p)
        }

        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun windDirToArrow(deg: Int): String = when ((deg + 22) / 45 % 8) {
        0 -> "↓"; 1 -> "↙"; 2 -> "←"; 3 -> "↖"
        4 -> "↑"; 5 -> "↗"; 6 -> "→"; 7 -> "↘"
        else -> ""
    }

    private fun buildPlaceSnippet(p: com.example.locationmapapp.data.model.PlaceResult) =
        listOfNotNull(p.address, p.phone, p.openingHours).joinToString("\n").ifBlank { p.category }

    private fun buildMetarSnippet(s: com.example.locationmapapp.data.model.MetarStation): String {
        val lines = mutableListOf<String>()

        // Temperature & dewpoint
        val tempF = s.tempC?.let { "%.0f°F".format(it * 9.0 / 5.0 + 32) }
        val dewF  = s.dewpointC?.let { "%.0f°F".format(it * 9.0 / 5.0 + 32) }
        if (tempF != null) {
            val dewPart = if (dewF != null) ", Dewpoint $dewF" else ""
            lines += "Temperature: $tempF$dewPart"
        }

        // Wind
        val wind = if (s.windSpeedKt != null && s.windSpeedKt > 0) {
            val dirName = s.windDirDeg?.let { degreesToCompass(it) } ?: "Variable"
            val gust = if (s.windGustKt != null) ", gusting to ${s.windGustKt} kt" else ""
            "Wind: $dirName at ${s.windSpeedKt} kt$gust"
        } else "Wind: Calm"
        lines += wind

        // Visibility
        s.visibilityMiles?.let {
            val visStr = if (it >= 10.0) "10+ miles" else "${"%.0f".format(it)} miles"
            lines += "Visibility: $visStr"
        }

        // Sky condition
        s.skyCover?.let { cover ->
            val decoded = when (cover.uppercase()) {
                "CLR", "SKC" -> "Clear"
                "FEW"        -> "Few clouds"
                "SCT"        -> "Scattered clouds"
                "BKN"        -> "Broken clouds"
                "OVC"        -> "Overcast"
                else         -> cover
            }
            lines += "Sky: $decoded"
        }

        // Weather phenomena
        s.wxString?.let { lines += "Weather: ${decodeWx(it)}" }

        // Altimeter
        s.altimeterInHg?.let { lines += "Altimeter: ${"%.2f".format(it)} inHg" }

        // Sea level pressure
        s.slpMb?.let { lines += "Sea Level Pressure: ${"%.1f".format(it)} mb" }

        // Flight category
        s.flightCategory?.let { cat ->
            val decoded = when (cat.uppercase()) {
                "VFR"  -> "VFR (Visual Flight Rules)"
                "MVFR" -> "MVFR (Marginal VFR)"
                "IFR"  -> "IFR (Instrument Flight Rules)"
                "LIFR" -> "LIFR (Low IFR)"
                else   -> cat
            }
            lines += "Flight Category: $decoded"
        }

        // Observation time
        s.observationTime?.let {
            try {
                val instant = java.time.Instant.parse(it)
                val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                lines += "Observed: ${local.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}"
            } catch (_: Exception) {}
        }

        // Raw METAR at the bottom
        lines += "\n${s.rawMetar}"

        return lines.joinToString("\n")
    }

    private fun degreesToCompass(deg: Int): String = when ((deg + 22) / 45 % 8) {
        0 -> "North"; 1 -> "Northeast"; 2 -> "East"; 3 -> "Southeast"
        4 -> "South"; 5 -> "Southwest"; 6 -> "West"; 7 -> "Northwest"
        else -> "${deg}°"
    }

    private fun decodeWx(wx: String): String {
        val map = mapOf(
            "RA" to "Rain", "SN" to "Snow", "DZ" to "Drizzle",
            "FG" to "Fog", "BR" to "Mist", "HZ" to "Haze",
            "TS" to "Thunderstorm", "SH" to "Showers", "FZ" to "Freezing",
            "GR" to "Hail", "GS" to "Small hail", "PL" to "Ice pellets",
            "SG" to "Snow grains", "IC" to "Ice crystals",
            "UP" to "Unknown precip", "FU" to "Smoke",
            "VA" to "Volcanic ash", "DU" to "Dust", "SA" to "Sand",
            "SQ" to "Squall", "FC" to "Funnel cloud",
            "SS" to "Sandstorm", "DS" to "Duststorm"
        )
        val intensityMap = mapOf("-" to "Light ", "+" to "Heavy ", "VC" to "Vicinity ")
        var result = wx
        for ((code, name) in map) result = result.replace(code, name + " ")
        for ((code, prefix) in intensityMap) result = result.replace(code, prefix)
        return result.trim().replace("\\s+".toRegex(), " ")
    }

    fun clearMetarMarkers() {
        metarMarkers.forEach { binding.mapView.overlays.remove(it) }
        metarMarkers.clear(); binding.mapView.invalidate()
    }

    // =========================================================================
    // AIRCRAFT
    // =========================================================================

    /** Altitude → color for aircraft markers and flight trail segments. */
    private fun altitudeColor(altitudeMeters: Double?, onGround: Boolean = false): Int {
        val altFt = altitudeMeters?.let { it * 3.28084 }
        return when {
            onGround              -> Color.parseColor("#78909C")  // gray
            altFt == null         -> Color.parseColor("#1565C0")  // blue default
            altFt < 5000          -> Color.parseColor("#2E7D32")  // green — low
            altFt < 20000         -> Color.parseColor("#1565C0")  // blue — mid
            else                  -> Color.parseColor("#6A1B9A")  // purple — high
        }
    }

    private fun addAircraftMarker(state: com.example.locationmapapp.data.model.AircraftState) {
        val tint = altitudeColor(state.baroAltitude, state.onGround)
        val heading = state.track?.toInt() ?: 0
        val m = Marker(binding.mapView).apply {
            position = state.toGeoPoint()
            icon     = MarkerIconHelper.aircraftMarker(
                this@MainActivity, heading, tint,
                state.callsign, state.verticalRate, state.spi
            )
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = state.callsign?.ifBlank { null } ?: state.icao24
            snippet  = buildAircraftSnippet(state)
            relatedObject = state
            setOnMarkerClickListener { _, _ ->
                onAircraftMarkerTapped(state)
                true
            }
        }
        aircraftMarkers.add(m)
        binding.mapView.overlays.add(m)
        binding.mapView.invalidate()
    }

    private fun buildAircraftSnippet(s: com.example.locationmapapp.data.model.AircraftState): String {
        val lines = mutableListOf<String>()
        // SPI emergency flag
        if (s.spi) lines += "⚠ SPECIAL PURPOSE INDICATOR ACTIVE"
        // Altitude
        val altFt = s.baroAltitude?.let { "%.0f ft".format(it * 3.28084) }
        val geoFt = s.geoAltitude?.let { "%.0f ft".format(it * 3.28084) }
        if (altFt != null) lines += "Altitude: $altFt (baro)" + if (geoFt != null) " / $geoFt (geo)" else ""
        // Speed
        s.velocity?.let { lines += "Speed: ${"%.0f".format(it * 1.94384)} kt (${"%.0f".format(it * 2.237)} mph)" }
        // Heading
        s.track?.let { lines += "Heading: ${"%.0f".format(it)}°" }
        // Vertical rate
        s.verticalRate?.let {
            val fpm = it * 196.85
            val dir = when { fpm > 100 -> "↑ climbing"; fpm < -100 -> "↓ descending"; else -> "level" }
            lines += "Vertical: ${"%.0f".format(fpm)} ft/min ($dir)"
        }
        // Squawk
        s.squawk?.let { lines += "Squawk: $it" }
        // On ground
        if (s.onGround) lines += "Status: On ground"
        // Origin
        if (s.originCountry.isNotBlank()) lines += "Origin: ${s.originCountry}"
        // Category
        val catName = aircraftCategoryName(s.category)
        if (catName.isNotBlank()) lines += "Category: $catName"
        // Position source
        val srcName = when (s.positionSource) {
            0 -> "ADS-B"; 1 -> "ASTERIX"; 2 -> "MLAT"; 3 -> "FLARM"; else -> null
        }
        srcName?.let { lines += "Source: $it" }
        // Data age
        s.timePosition?.let {
            val ageSec = (System.currentTimeMillis() / 1000) - it
            lines += "Position age: ${ageSec}s"
        }
        // ICAO24
        lines += "ICAO24: ${s.icao24}"
        return lines.joinToString("\n")
    }

    private fun aircraftCategoryName(cat: Int): String = when (cat) {
        0  -> ""
        1  -> "No ADS-B category"
        2  -> "Light (<15,500 lbs)"
        3  -> "Small (15,500–75,000 lbs)"
        4  -> "Large (75,000–300,000 lbs)"
        5  -> "High Vortex Large"
        6  -> "Heavy (>300,000 lbs)"
        7  -> "High Performance"
        8  -> "Rotorcraft"
        9  -> "Glider/Sailplane"
        10 -> "Lighter-than-air"
        11 -> "Skydiver"
        12 -> "Ultralight"
        13 -> "UAV"
        14 -> "Space vehicle"
        else -> "Cat $cat"
    }

    private fun clearAircraftMarkers() {
        aircraftMarkers.forEach { binding.mapView.overlays.remove(it) }
        aircraftMarkers.clear()
        binding.mapView.invalidate()
    }

    // ── Flight trail ─────────────────────────────────────────────────────────

    /** Rebuild all trail polylines from flightTrailPoints. Skips gaps >30min. Cap: 1000 points. */
    private fun redrawFlightTrail() {
        // Remove existing trail overlays
        flightTrailOverlays.forEach { binding.mapView.overlays.remove(it) }
        flightTrailOverlays.clear()
        if (flightTrailPoints.size < 2) { binding.mapView.invalidate(); return }

        // Cap at 1000 points (keep most recent)
        while (flightTrailPoints.size > 1000) flightTrailPoints.removeAt(0)

        val GAP_THRESHOLD_MS = 30 * 60 * 1000L  // 30 minutes
        // Find insert position: before aircraft markers (trail renders underneath)
        val insertIdx = binding.mapView.overlays.indexOfFirst { it is Marker && aircraftMarkers.contains(it) }
            .let { if (it < 0) binding.mapView.overlays.size else it }

        var segStart = 0
        for (i in 1 until flightTrailPoints.size) {
            val prev = flightTrailPoints[i - 1]
            val cur = flightTrailPoints[i]
            val isGap = (cur.timestamp - prev.timestamp) > GAP_THRESHOLD_MS
            val isLast = i == flightTrailPoints.size - 1

            if (isGap || isLast) {
                val endIdx = if (isGap) i else i + 1
                if (endIdx - segStart >= 2) {
                    val segment = flightTrailPoints.subList(segStart, endIdx)
                    val line = Polyline(binding.mapView).apply {
                        outlinePaint.apply {
                            color = altitudeColor(segment.last().altitudeMeters)
                            alpha = 200
                            strokeWidth = 6f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            isAntiAlias = true
                        }
                        setPoints(segment.map { it.toGeoPoint() })
                        isEnabled = false  // non-interactive
                    }
                    flightTrailOverlays.add(line)
                    binding.mapView.overlays.add(insertIdx, line)
                }
                segStart = i
            }
        }
        binding.mapView.invalidate()
    }

    /** Append a live aircraft position to the trail incrementally. */
    private fun appendToFlightTrail(state: com.example.locationmapapp.data.model.AircraftState) {
        val point = com.example.locationmapapp.data.model.FlightPathPoint(
            lat = state.lat, lon = state.lon,
            altitudeMeters = state.baroAltitude,
            timestamp = (state.timePosition ?: (System.currentTimeMillis() / 1000)) * 1000
        )
        // Deduplicate — skip if same position as last point
        val last = flightTrailPoints.lastOrNull()
        if (last != null && last.lat == point.lat && last.lon == point.lon) return

        flightTrailPoints.add(point)

        // Cap at 1000 points
        while (flightTrailPoints.size > 1000) flightTrailPoints.removeAt(0)

        // Incremental: add a single segment from previous to new point (if we have ≥2 points)
        if (flightTrailPoints.size >= 2) {
            val prev = flightTrailPoints[flightTrailPoints.size - 2]
            val GAP_THRESHOLD_MS = 30 * 60 * 1000L
            if ((point.timestamp - prev.timestamp) <= GAP_THRESHOLD_MS) {
                val insertIdx = binding.mapView.overlays.indexOfFirst { it is Marker && aircraftMarkers.contains(it) }
                    .let { if (it < 0) binding.mapView.overlays.size else it }
                val line = Polyline(binding.mapView).apply {
                    outlinePaint.apply {
                        color = altitudeColor(point.altitudeMeters)
                        alpha = 200
                        strokeWidth = 6f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        isAntiAlias = true
                    }
                    setPoints(listOf(prev.toGeoPoint(), point.toGeoPoint()))
                    isEnabled = false
                }
                flightTrailOverlays.add(line)
                binding.mapView.overlays.add(insertIdx, line)
                binding.mapView.invalidate()
            }
        }
    }

    /** Remove all trail overlays and clear the point list. */
    private fun clearFlightTrail() {
        flightTrailOverlays.forEach { binding.mapView.overlays.remove(it) }
        flightTrailOverlays.clear()
        flightTrailPoints.clear()
        binding.mapView.invalidate()
    }

    /** Load DB history for an aircraft and draw initial trail. */
    private fun loadFlightTrailHistory(icao24: String, currentState: com.example.locationmapapp.data.model.AircraftState? = null) {
        lifecycleScope.launch {
            val history = viewModel.fetchFlightHistoryDirectly(icao24)
            if (history.isNotEmpty()) {
                flightTrailPoints.addAll(history)
            }
            // Append current position if available
            if (currentState != null) {
                val point = com.example.locationmapapp.data.model.FlightPathPoint(
                    lat = currentState.lat, lon = currentState.lon,
                    altitudeMeters = currentState.baroAltitude,
                    timestamp = (currentState.timePosition ?: (System.currentTimeMillis() / 1000)) * 1000
                )
                val last = flightTrailPoints.lastOrNull()
                if (last == null || last.lat != point.lat || last.lon != point.lon) {
                    flightTrailPoints.add(point)
                }
            }
            redrawFlightTrail()
            DebugLogger.i("MainActivity", "Flight trail loaded: ${flightTrailPoints.size} points, ${flightTrailOverlays.size} segments")
        }
    }

    private fun startAircraftRefresh() {
        aircraftRefreshJob?.cancel()
        DebugLogger.i("MainActivity", "Starting aircraft refresh every ${aircraftRefreshIntervalSec}s")
        aircraftRefreshJob = lifecycleScope.launch {
            while (true) {
                loadAircraftForVisibleArea()
                delay(aircraftRefreshIntervalSec * 1000L)
            }
        }
    }

    private fun stopAircraftRefresh() {
        aircraftRefreshJob?.cancel()
        aircraftRefreshJob = null
        DebugLogger.i("MainActivity", "Aircraft refresh stopped")
    }

    private fun clearPoiMarkers(layerId: String) {
        poiMarkers[layerId]?.forEach { binding.mapView.overlays.remove(it) }
        poiMarkers[layerId]?.clear()
        binding.mapView.invalidate()
    }

    /** Clear ALL POI markers from every layer at once. */
    private fun clearAllPoiMarkers() {
        poiMarkers.values.forEach { list ->
            list.forEach { binding.mapView.overlays.remove(it) }
        }
        poiMarkers.clear()
    }

    /** Replace all POI markers with only the given viewport results. */
    private fun replaceAllPoiMarkers(places: List<com.example.locationmapapp.data.model.PlaceResult>) {
        clearAllPoiMarkers()
        places.forEach { addPoiMarker("bbox", it) }
        // Re-add station markers so they stay on top and receive taps first
        bringStationMarkersToFront()
        binding.mapView.invalidate()
    }

    /** Move station markers to the end of the overlay list so they draw on top and get taps first. */
    private fun bringStationMarkersToFront() {
        if (stationMarkers.isEmpty()) return
        val overlays = binding.mapView.overlays
        stationMarkers.forEach { overlays.remove(it) }
        overlays.addAll(stationMarkers)
    }

    // =========================================================================
    // WEBCAMS
    // =========================================================================

    private fun addWebcamMarker(webcam: com.example.locationmapapp.data.model.Webcam) {
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

    private fun clearWebcamMarkers() {
        webcamMarkers.forEach { binding.mapView.overlays.remove(it) }
        webcamMarkers.clear()
        binding.mapView.invalidate()
    }

    private fun loadWebcamsForVisibleArea() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(AppBarMenuManager.PREF_WEBCAMS_ON, true)) return
        val bb = binding.mapView.boundingBox
        val cats = prefs.getStringSet(AppBarMenuManager.PREF_WEBCAM_CATEGORIES, setOf("traffic")) ?: setOf("traffic")
        if (cats.isEmpty()) return
        // Windy API returns 0 results for very small bboxes — enforce minimum ~0.5° span
        val centerLat = (bb.latNorth + bb.latSouth) / 2.0
        val centerLon = (bb.lonEast + bb.lonWest) / 2.0
        val halfLat = maxOf((bb.latNorth - bb.latSouth) / 2.0, 0.25)
        val halfLon = maxOf((bb.lonEast - bb.lonWest) / 2.0, 0.25)
        viewModel.loadWebcams(centerLat - halfLat, centerLon - halfLon, centerLat + halfLat, centerLon + halfLon, cats.joinToString(","))
    }

    /** Debounced: reload webcams 500ms after user stops scrolling/zooming. */
    private fun scheduleWebcamReload() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean(AppBarMenuManager.PREF_WEBCAMS_ON, true)) return
        webcamReloadJob?.cancel()
        webcamReloadJob = lifecycleScope.launch {
            delay(500)
            loadWebcamsForVisibleArea()
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun showWebcamPreviewDialog(webcam: com.example.locationmapapp.data.model.Webcam) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // ── Header: title + X button ──
        val titleText = TextView(this).apply {
            text = webcam.title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "X"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
            addView(titleText)
            addView(closeBtn)
        }

        // ── Preview image ──
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#222222"))
        }

        // ── Info text ──
        val catText = webcam.categories.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        val infoText = TextView(this).apply {
            text = buildString {
                append(catText)
                webcam.lastUpdated?.let { append("\nLast updated: $it") }
                append("\nStatus: ${webcam.status}")
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
        }

        // ── View Live button ──
        val liveBtn = if (webcam.playerUrl.isNotBlank()) {
            android.widget.Button(this, null, android.R.attr.buttonBarButtonStyle).apply {
                text = "View Live"
                textSize = 15f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1976D2"))
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) }
                setOnClickListener {
                    // Replace image + info + button with WebView
                    val parent = this.parent as LinearLayout
                    val idx = parent.indexOfChild(imageView)
                    parent.removeView(imageView)
                    parent.removeView(infoText)
                    parent.removeView(this)
                    val webView = android.webkit.WebView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        setBackgroundColor(Color.BLACK)
                        loadUrl(webcam.playerUrl)
                    }
                    parent.addView(webView, idx)
                    dialog.setOnDismissListener { webView.destroy() }
                }
            }
        } else null

        // ── Container ──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(imageView)
            addView(infoText)
            liveBtn?.let { addView(it) }
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.9).toInt())
            win.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()

        // Load preview image async
        if (webcam.previewUrl.isNotBlank()) {
            lifecycleScope.launch {
                try {
                    val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val request = okhttp3.Request.Builder().url(webcam.previewUrl).build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.let { android.graphics.BitmapFactory.decodeStream(it) }
                        } else null
                    }
                    if (bitmap != null && dialog.isShowing) {
                        imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    DebugLogger.e("MainActivity", "Webcam image load failed: ${e.message}")
                }
            }
        }
    }

    // =========================================================================
    // MBTA TRAINS
    // =========================================================================

    private fun addTrainMarker(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        val tint = when {
            vehicle.routeId.startsWith("CR-") -> Color.parseColor("#6A1B9A")  // Purple — Commuter Rail
            vehicle.routeId.startsWith("Green") -> Color.parseColor("#2E7D32")
            vehicle.routeId == "Red"    -> Color.parseColor("#C62828")
            vehicle.routeId == "Orange" -> Color.parseColor("#E65100")
            vehicle.routeId == "Blue"   -> Color.parseColor("#1565C0")
            vehicle.routeId == "Silver" -> Color.parseColor("#546E7A")
            else -> Color.parseColor("#37474F")
        }
        val m = Marker(binding.mapView).apply {
            position = vehicle.toGeoPoint()
            icon     = MarkerIconHelper.withArrow(this@MainActivity, R.drawable.ic_transit_rail, 30, tint, vehicle.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title   = "Train ${vehicle.label} — ${vehicle.routeName}"
            snippet = buildTrainSnippet(vehicle)
            relatedObject = vehicle
            setOnMarkerClickListener { _, _ ->
                onVehicleMarkerTapped(vehicle)
                true
            }
        }
        trainMarkers.add(m)
        binding.mapView.overlays.add(m)
        binding.mapView.invalidate()
    }

    private fun buildTrainSnippet(v: com.example.locationmapapp.data.model.MbtaVehicle): String {
        val statusLine = when {
            v.stopName != null -> "${v.currentStatus.display} ${v.stopName}"
            else               -> v.currentStatus.display
        }
        val speedLine  = "Speed: ${v.speedDisplay}"
        val updated    = v.updatedAt.take(19).replace("T", " ")
        val staleTag   = vehicleStalenessTag(v.updatedAt)
        return "$statusLine\n$speedLine\nUpdated: $updated$staleTag"
    }

    /**
     * Parse an ISO-8601 timestamp and return a staleness tag if the update is old.
     * Returns "" if fresh (≤2 min), or " — STALE (Xm ago)" / "(Xh ago)" etc.
     */
    private fun vehicleStalenessTag(isoTimestamp: String): String {
        return try {
            val updatedMs = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                .parse(isoTimestamp)?.time ?: return ""
            val ageMs = System.currentTimeMillis() - updatedMs
            val ageSec = ageMs / 1000
            when {
                ageSec < 120  -> ""  // fresh
                ageSec < 3600 -> " — STALE (${ageSec / 60}m ago)"
                else          -> " — STALE (${ageSec / 3600}h ${(ageSec % 3600) / 60}m ago)"
            }
        } catch (_: Exception) { "" }
    }

    private fun clearTrainMarkers() {
        trainMarkers.forEach { binding.mapView.overlays.remove(it) }
        trainMarkers.clear()
        binding.mapView.invalidate()
    }

    /**
     * Start auto-refreshing train positions at trainRefreshIntervalSec cadence.
     * Cancels any existing job first so frequency changes take effect immediately.
     */
    private fun startTrainRefresh() {
        trainRefreshJob?.cancel()
        DebugLogger.i("MainActivity", "Starting MBTA train refresh every ${trainRefreshIntervalSec}s")
        trainRefreshJob = lifecycleScope.launch {
            while (true) {
                viewModel.fetchMbtaTrains()
                delay(trainRefreshIntervalSec * 1000L)
            }
        }
    }

    private fun stopTrainRefresh() {
        trainRefreshJob?.cancel()
        trainRefreshJob = null
        DebugLogger.i("MainActivity", "MBTA train refresh stopped")
    }

    // =========================================================================
    // MBTA SUBWAY
    // =========================================================================

    private fun addSubwayMarker(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        val tint = when {
            vehicle.routeId.startsWith("Green") -> Color.parseColor("#2E7D32")
            vehicle.routeId == "Red"    -> Color.parseColor("#C62828")
            vehicle.routeId == "Orange" -> Color.parseColor("#E65100")
            vehicle.routeId == "Blue"   -> Color.parseColor("#1565C0")
            vehicle.routeId == "Mattapan" -> Color.parseColor("#C62828")
            else -> Color.parseColor("#37474F")
        }
        val m = Marker(binding.mapView).apply {
            position = vehicle.toGeoPoint()
            icon     = MarkerIconHelper.withArrow(this@MainActivity, R.drawable.ic_transit_rail, 26, tint, vehicle.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title   = "Subway ${vehicle.label} — ${vehicle.routeName}"
            snippet = buildTrainSnippet(vehicle)
            relatedObject = vehicle
            setOnMarkerClickListener { _, _ ->
                onVehicleMarkerTapped(vehicle)
                true
            }
        }
        subwayMarkers.add(m)
        binding.mapView.overlays.add(m)
        binding.mapView.invalidate()
    }

    private fun clearSubwayMarkers() {
        subwayMarkers.forEach { binding.mapView.overlays.remove(it) }
        subwayMarkers.clear()
        binding.mapView.invalidate()
    }

    private fun startSubwayRefresh() {
        subwayRefreshJob?.cancel()
        DebugLogger.i("MainActivity", "Starting MBTA subway refresh every ${subwayRefreshIntervalSec}s")
        subwayRefreshJob = lifecycleScope.launch {
            while (true) {
                viewModel.fetchMbtaSubway()
                delay(subwayRefreshIntervalSec * 1000L)
            }
        }
    }

    private fun stopSubwayRefresh() {
        subwayRefreshJob?.cancel()
        subwayRefreshJob = null
        DebugLogger.i("MainActivity", "MBTA subway refresh stopped")
    }

    // =========================================================================
    // MBTA BUSES
    // =========================================================================

    private fun addBusMarker(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        val tint = Color.parseColor("#00695C")  // Teal for buses
        val m = Marker(binding.mapView).apply {
            position = vehicle.toGeoPoint()
            icon     = MarkerIconHelper.withArrow(this@MainActivity, R.drawable.ic_bus, 22, tint, vehicle.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title   = "Bus ${vehicle.label} — ${vehicle.routeName}"
            snippet = buildTrainSnippet(vehicle)
            relatedObject = vehicle
            setOnMarkerClickListener { _, _ ->
                onVehicleMarkerTapped(vehicle)
                true
            }
        }
        busMarkers.add(m)
        binding.mapView.overlays.add(m)
        binding.mapView.invalidate()
    }

    private fun clearBusMarkers() {
        busMarkers.forEach { binding.mapView.overlays.remove(it) }
        busMarkers.clear()
        binding.mapView.invalidate()
    }

    private fun startBusRefresh() {
        busRefreshJob?.cancel()
        DebugLogger.i("MainActivity", "Starting MBTA bus refresh every ${busRefreshIntervalSec}s")
        busRefreshJob = lifecycleScope.launch {
            while (true) {
                viewModel.fetchMbtaBuses()
                delay(busRefreshIntervalSec * 1000L)
            }
        }
    }

    private fun stopBusRefresh() {
        busRefreshJob?.cancel()
        busRefreshJob = null
        DebugLogger.i("MainActivity", "MBTA bus refresh stopped")
    }

    // =========================================================================
    // MBTA TRAIN STATIONS
    // =========================================================================

    /** Get the MBTA line color for a route ID. */
    private fun routeColor(routeId: String): Int = when {
        routeId == "Red" || routeId == "Mattapan" -> Color.parseColor("#C62828")
        routeId == "Orange"                       -> Color.parseColor("#E65100")
        routeId == "Blue"                         -> Color.parseColor("#1565C0")
        routeId.startsWith("Green")               -> Color.parseColor("#2E7D32")
        routeId == "CR" || routeId.startsWith("CR-") -> Color.parseColor("#6A1B9A")
        routeId == "Silver"                       -> Color.parseColor("#546E7A")
        else                                      -> Color.parseColor("#37474F")
    }

    /** Abbreviated route label for arrival board. */
    private fun routeAbbrev(routeId: String): String = when {
        routeId == "Red"                          -> "RL"
        routeId == "Orange"                       -> "OL"
        routeId == "Blue"                         -> "BL"
        routeId == "Mattapan"                     -> "M"
        routeId.startsWith("Green-")              -> "GL-${routeId.removePrefix("Green-")}"
        routeId == "CR" || routeId.startsWith("CR-") -> "CR"
        routeId == "Silver"                       -> "SL"
        else                                      -> routeId.take(4)
    }

    private fun addStationMarker(stop: com.example.locationmapapp.data.model.MbtaStop) {
        val tint = if (stop.routeIds.size > 1) {
            Color.parseColor("#37474F")  // neutral dark gray for multi-line stations
        } else {
            routeColor(stop.routeIds.firstOrNull() ?: "")
        }
        val m = Marker(binding.mapView).apply {
            position = stop.toGeoPoint()
            icon     = MarkerIconHelper.stationIcon(this@MainActivity, tint)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = stop.name
            snippet  = stop.routeIds.joinToString(", ") { routeAbbrev(it) }
            relatedObject = stop
            setOnMarkerClickListener { _, _ ->
                showArrivalBoardDialog(stop)
                true
            }
        }
        stationMarkers.add(m)
        binding.mapView.overlays.add(m)
    }

    private fun clearStationMarkers() {
        stationMarkers.forEach { binding.mapView.overlays.remove(it) }
        stationMarkers.clear()
        binding.mapView.invalidate()
    }

    // =========================================================================
    // MBTA BUS STOPS (viewport-filtered)
    // =========================================================================

    /** Debounced: refresh bus stop markers 300ms after scroll/zoom. */
    private fun scheduleBusStopReload() {
        if (allBusStops.isEmpty()) return
        busStopReloadJob?.cancel()
        busStopReloadJob = lifecycleScope.launch {
            delay(300)
            refreshBusStopMarkersForViewport()
        }
    }

    /** Show bus stop markers only at zoom >= 15, filtered by visible bounding box. */
    private fun refreshBusStopMarkersForViewport() {
        clearBusStopMarkers()
        val zoom = binding.mapView.zoomLevelDouble
        if (zoom < 15.0 || allBusStops.isEmpty()) return

        val bb = binding.mapView.boundingBox
        val visible = allBusStops.filter { stop ->
            stop.lat in bb.latSouth..bb.latNorth && stop.lon in bb.lonWest..bb.lonEast
        }
        DebugLogger.d("MainActivity", "Bus stops viewport: ${visible.size} of ${allBusStops.size} visible at zoom ${zoom.toInt()}")
        visible.forEach { addBusStopMarker(it) }
        binding.mapView.invalidate()
    }

    private fun addBusStopMarker(stop: com.example.locationmapapp.data.model.MbtaStop) {
        val tint = Color.parseColor("#00695C")  // Teal
        val m = Marker(binding.mapView).apply {
            position = stop.toGeoPoint()
            icon     = MarkerIconHelper.busStopIcon(this@MainActivity, tint)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = stop.name
            snippet  = "Bus Stop"
            relatedObject = stop
            setOnMarkerClickListener { _, _ ->
                showArrivalBoardDialog(stop)
                true
            }
        }
        busStopMarkers.add(m)
        binding.mapView.overlays.add(m)
    }

    private fun clearBusStopMarkers() {
        busStopMarkers.forEach { binding.mapView.overlays.remove(it) }
        busStopMarkers.clear()
    }

    // ── Arrival Board Dialog ─────────────────────────────────────────────────

    private var arrivalRefreshJob: Job? = null

    @SuppressLint("SetTextI18n")
    private fun showArrivalBoardDialog(stop: com.example.locationmapapp.data.model.MbtaStop) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // ── Header ──
        val titleText = TextView(this).apply {
            text = stop.name
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "X"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(4))
            addView(titleText)
            addView(closeBtn)
        }

        // ── Subtitle: lines served (updated once predictions arrive) ──
        val linesText = TextView(this).apply {
            val staticLines = stop.routeIds.joinToString(", ") { routeAbbrev(it) }
            text = if (staticLines.isNotEmpty()) "Lines: $staticLines" else "Lines: loading…"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(dp(16), 0, dp(16), dp(8))
        }

        // ── Column headers ──
        val colHeaders = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            setBackgroundColor(Color.parseColor("#333333"))
            addView(TextView(this@MainActivity).apply {
                text = "Line"; textSize = 12f; setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Destination"; textSize = 12f; setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Arrives"; textSize = 12f; setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = android.view.Gravity.END
            })
        }

        // ── Scrollable list container ──
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(listContainer)
        }

        // ── Loading indicator ──
        val loadingText = TextView(this).apply {
            text = "Loading arrivals…"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(dp(16), dp(24), dp(16), dp(24))
            gravity = android.view.Gravity.CENTER
        }
        listContainer.addView(loadingText)

        // ── Container ──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(linesText)
            addView(colHeaders)
            addView(scrollView)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.9).toInt())
            win.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // ── Load predictions and auto-refresh ──
        fun loadPredictions() {
            lifecycleScope.launch {
                val predictions = viewModel.fetchPredictionsDirectly(stop.id)
                if (!dialog.isShowing) return@launch
                listContainer.removeAllViews()
                // Update lines subtitle from actual prediction data
                val linesList = predictions.map { it.routeId }.distinct()
                    .joinToString(", ") { routeAbbrev(it) }
                if (linesList.isNotEmpty()) linesText.text = "Lines: $linesList"

                if (predictions.isEmpty()) {
                    listContainer.addView(TextView(this@MainActivity).apply {
                        text = "No upcoming arrivals"
                        textSize = 14f
                        setTextColor(Color.parseColor("#999999"))
                        setPadding(dp(0), dp(24), dp(0), dp(24))
                        gravity = android.view.Gravity.CENTER
                    })
                } else {
                    predictions.forEach { pred ->
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setPadding(0, dp(8), 0, dp(8))
                        }

                        // Route dot + abbreviation
                        val routeLabel = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        val dotView = View(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                                setMargins(0, 0, dp(4), 0)
                            }
                            background = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                setColor(routeColor(pred.routeId))
                            }
                        }
                        routeLabel.addView(dotView)
                        routeLabel.addView(TextView(this@MainActivity).apply {
                            text = routeAbbrev(pred.routeId)
                            textSize = 13f
                            setTextColor(Color.WHITE)
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        })
                        row.addView(routeLabel)

                        // Headsign / destination
                        row.addView(TextView(this@MainActivity).apply {
                            text = pred.headsign ?: pred.routeName
                            textSize = 14f
                            setTextColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })

                        // Arrival time
                        row.addView(TextView(this@MainActivity).apply {
                            text = formatArrivalTime(pred.arrivalTime ?: pred.departureTime)
                            textSize = 14f
                            setTextColor(Color.parseColor("#4FC3F7"))
                            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
                            gravity = android.view.Gravity.END
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        })

                        // Tap row → trip schedule
                        if (pred.tripId != null) {
                            row.isClickable = true
                            row.isFocusable = true
                            row.setBackgroundResource(android.R.drawable.list_selector_background)
                            row.setOnClickListener {
                                showTripScheduleDialog(pred)
                            }
                        }

                        listContainer.addView(row)
                        // Divider
                        listContainer.addView(View(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 1
                            )
                            setBackgroundColor(Color.parseColor("#333333"))
                        })
                    }
                }
            }
        }

        loadPredictions()

        // Auto-refresh every 30s
        arrivalRefreshJob?.cancel()
        arrivalRefreshJob = lifecycleScope.launch {
            while (dialog.isShowing) {
                delay(30_000)
                if (dialog.isShowing) loadPredictions()
            }
        }

        dialog.setOnDismissListener {
            arrivalRefreshJob?.cancel()
            arrivalRefreshJob = null
        }

        dialog.show()
    }

    /** Format ISO-8601 arrival time to "Now", "X min", or "H:MM AM/PM". */
    private fun formatArrivalTime(isoTime: String?): String {
        if (isoTime == null) return "—"
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val arrivalMs = sdf.parse(isoTime)?.time ?: return isoTime
            val diffMs = arrivalMs - System.currentTimeMillis()
            val diffMin = diffMs / 60_000
            when {
                diffMin <= 0  -> "Now"
                diffMin < 60  -> "${diffMin} min"
                else -> {
                    val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                    timeFmt.format(java.util.Date(arrivalMs))
                }
            }
        } catch (_: Exception) { isoTime.takeLast(8).take(5) }
    }

    // ── Trip Schedule Dialog ─────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun showTripScheduleDialog(pred: com.example.locationmapapp.data.model.MbtaPrediction) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // ── Header with back button ──
        val backBtn = TextView(this).apply {
            text = "\u2190"  // ← arrow
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(4), dp(12), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        val titleText = TextView(this).apply {
            text = "${pred.routeName} to ${pred.headsign ?: "?"}"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "X"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(4))
            addView(backBtn)
            addView(titleText)
            addView(closeBtn)
        }

        // ── Route color bar ──
        val colorBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
            )
            setBackgroundColor(routeColor(pred.routeId))
        }

        // ── Scrollable stop list ──
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(listContainer)
        }

        // ── Loading ──
        listContainer.addView(TextView(this).apply {
            text = "Loading schedule…"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, dp(24), 0, dp(24))
            gravity = android.view.Gravity.CENTER
        })

        // ── Container ──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(colorBar)
            addView(scrollView)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.9).toInt())
            win.setBackgroundDrawableResource(android.R.color.transparent)
        }

        // ── Load schedule ──
        val tripId = pred.tripId ?: return
        lifecycleScope.launch {
            val entries = viewModel.fetchTripScheduleDirectly(tripId)
            if (!dialog.isShowing) return@launch
            listContainer.removeAllViews()
            if (entries.isEmpty()) {
                listContainer.addView(TextView(this@MainActivity).apply {
                    text = "No schedule data available"
                    textSize = 14f
                    setTextColor(Color.parseColor("#999999"))
                    setPadding(0, dp(24), 0, dp(24))
                    gravity = android.view.Gravity.CENTER
                })
            } else {
                val lineColor = routeColor(pred.routeId)
                entries.forEach { entry ->
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, dp(6), 0, dp(6))
                    }

                    // Colored dot
                    row.addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                            setMargins(0, 0, dp(8), 0)
                        }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(lineColor)
                        }
                    })

                    // Stop name
                    row.addView(TextView(this@MainActivity).apply {
                        text = entry.stopName
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    // Time (12h format)
                    val timeStr = formatScheduleTime(entry.arrivalTime ?: entry.departureTime)
                    row.addView(TextView(this@MainActivity).apply {
                        text = timeStr
                        textSize = 13f
                        setTextColor(Color.parseColor("#B0BEC5"))
                        layoutParams = LinearLayout.LayoutParams(dp(75), LinearLayout.LayoutParams.WRAP_CONTENT)
                        gravity = android.view.Gravity.END
                    })

                    // Track number (commuter rail)
                    if (entry.platformCode != null) {
                        row.addView(TextView(this@MainActivity).apply {
                            text = "Trk ${entry.platformCode}"
                            textSize = 12f
                            setTextColor(Color.parseColor("#78909C"))
                            layoutParams = LinearLayout.LayoutParams(dp(45), LinearLayout.LayoutParams.WRAP_CONTENT)
                            gravity = android.view.Gravity.END
                        })
                    }

                    listContainer.addView(row)
                    // Divider
                    listContainer.addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        setBackgroundColor(Color.parseColor("#2A2A2A"))
                    })
                }
            }
        }

        dialog.show()
    }

    /** Format ISO-8601 schedule time to "h:mm a" for display. */
    private fun formatScheduleTime(isoTime: String?): String {
        if (isoTime == null) return "—"
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val ms = sdf.parse(isoTime)?.time ?: return isoTime
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(ms))
        } catch (_: Exception) { isoTime.takeLast(8).take(5) }
    }

    // =========================================================================
    // VEHICLE FOLLOW MODE
    // =========================================================================

    private fun onVehicleMarkerTapped(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        // Stop populate scanner and silent fill — user is interacting with an object
        if (populateJob != null) stopPopulatePois()
        stopSilentFill()
        showVehicleDetailDialog(vehicle)
    }

    // ── Vehicle Detail Dialog ────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun showVehicleDetailDialog(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val typeLabel = when (vehicle.routeType) {
            3    -> "Bus"
            2    -> "Commuter Rail"
            0    -> "Light Rail"
            1    -> "Subway"
            else -> "Vehicle"
        }
        val lineColor = vehicleRouteColor(vehicle)

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // ── Header ──
        val titleText = TextView(this).apply {
            text = "$typeLabel ${vehicle.label}"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "X"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(4))
            addView(titleText)
            addView(closeBtn)
        }

        // ── Color bar ──
        val colorBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
            )
            setBackgroundColor(lineColor)
        }

        // ── Info rows ──
        val infoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        fun addInfoRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 14f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            infoContainer.addView(row)
        }

        addInfoRow("Route", vehicle.routeName)
        if (vehicle.headsign != null) {
            addInfoRow("Destination", vehicle.headsign)
        }
        addInfoRow("Vehicle", vehicle.label)
        val statusText = when {
            vehicle.stopName != null -> "${vehicle.currentStatus.display} ${vehicle.stopName}"
            else -> vehicle.currentStatus.display
        }
        addInfoRow("Status", statusText)
        addInfoRow("Speed", vehicle.speedDisplay)
        val updated = vehicle.updatedAt.take(19).replace("T", " ")
        val staleTag = vehicleStalenessTag(vehicle.updatedAt)
        addInfoRow("Updated", "$updated$staleTag")

        // ── Action buttons ──
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val buttonLp = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        }

        // Follow button
        val followBtn = TextView(this).apply {
            text = if (followedVehicleId == vehicle.id) "Unfollow" else "Follow"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#00695C"))
            layoutParams = buttonLp
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                dialog.dismiss()
                if (followedVehicleId == vehicle.id) {
                    stopFollowing()
                } else {
                    startFollowing(vehicle)
                }
            }
        }

        // View Route button
        val routeBtn = TextView(this).apply {
            text = "View Route"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#546E7A"))
            layoutParams = buttonLp
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val tripId = vehicle.tripId
            if (tripId != null) {
                setOnClickListener {
                    dialog.dismiss()
                    val syntheticPred = com.example.locationmapapp.data.model.MbtaPrediction(
                        id = "vehicle-${vehicle.id}",
                        routeId = vehicle.routeId,
                        routeName = vehicle.routeName,
                        tripId = tripId,
                        headsign = vehicle.headsign ?: vehicle.stopName,
                        arrivalTime = null,
                        departureTime = null,
                        directionId = 0,
                        status = null,
                        vehicleId = vehicle.id
                    )
                    showTripScheduleDialog(syntheticPred)
                }
            } else {
                alpha = 0.4f
                setOnClickListener { toast("No trip info available") }
            }
        }

        // Arrivals at Stop button
        val arrivalsBtn = TextView(this).apply {
            text = "Arrivals"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1565C0"))
            layoutParams = buttonLp
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val stopId = vehicle.stopId
            val stopName = vehicle.stopName
            if (stopId != null && stopName != null) {
                setOnClickListener {
                    dialog.dismiss()
                    val syntheticStop = com.example.locationmapapp.data.model.MbtaStop(
                        id = stopId,
                        name = stopName,
                        lat = vehicle.lat,
                        lon = vehicle.lon,
                        routeIds = listOf(vehicle.routeId)
                    )
                    showArrivalBoardDialog(syntheticStop)
                }
            } else {
                alpha = 0.4f
                setOnClickListener { toast("No stop info available") }
            }
        }

        buttonContainer.addView(followBtn)
        buttonContainer.addView(routeBtn)
        buttonContainer.addView(arrivalsBtn)

        // ── Container ──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(colorBar)
            addView(infoContainer)
            addView(buttonContainer)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.85).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(android.view.Gravity.CENTER)
        }
        dialog.show()
    }

    // ── Find Dialog (POI Discovery) ──────────────────────────────────────────

    // Filter mode state
    private var findFilterActive = false
    private var findFilterTags = listOf<String>()
    private var findFilterLabel = ""
    private var findFilterBanner: View? = null

    @SuppressLint("SetTextI18n")
    private fun showFindDialog() {
        // Auto-exit filter mode when reopening Find
        if (findFilterActive) exitFindFilterMode()
        viewModel.loadFindCounts()
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        showFindCategoryGrid(dialog)
        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showFindCategoryGrid(dialog: android.app.Dialog) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }
        val counts = viewModel.findCounts.value

        // ── Header ──
        val titleText = TextView(this).apply {
            text = "Find"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(titleText)
            addView(closeBtn)
        }

        // ── 4×4 Category Grid ──
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(8), 0, dp(8), dp(12))
        }

        for (cat in PoiCategories.ALL) {
            val catCount = counts?.let { c ->
                cat.tags.sumOf { tag -> c.counts[tag] ?: 0 }
            }

            val cell = android.widget.FrameLayout(this).apply {
                val lp = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(64)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                layoutParams = lp
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.argb(77, Color.red(cat.color), Color.green(cat.color), Color.blue(cat.color)))
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }

            // Label
            val label = TextView(this).apply {
                text = cat.label
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 2
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM
                )
            }

            // Count badge
            if (catCount != null && catCount > 0) {
                val badge = TextView(this).apply {
                    text = if (catCount >= 1000) "%.1fk".format(catCount / 1000.0) else catCount.toString()
                    textSize = 10f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.TOP or android.view.Gravity.END
                    )
                }
                cell.addView(badge)
            }

            cell.addView(label)

            // Short tap
            cell.setOnClickListener {
                if (cat.subtypes != null) {
                    showFindSubtypeGrid(dialog, cat)
                } else {
                    showFindResults(dialog, cat.label, cat.tags, null)
                }
            }

            // Long press → filter mode
            cell.setOnLongClickListener {
                dialog.dismiss()
                enterFindFilterMode(cat.tags, cat.label)
                true
            }

            grid.addView(cell)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(grid)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.90).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(android.view.Gravity.CENTER)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showFindSubtypeGrid(dialog: android.app.Dialog, cat: com.example.locationmapapp.ui.menu.PoiCategory) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }
        val counts = viewModel.findCounts.value
        val subtypes = cat.subtypes ?: return

        // ── Header with back ──
        val backBtn = TextView(this).apply {
            text = "\u2190"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(12), 0)
            setOnClickListener { showFindCategoryGrid(dialog) }
        }
        val titleText = TextView(this).apply {
            text = cat.label
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(backBtn)
            addView(titleText)
            addView(closeBtn)
        }

        // ── Dynamic column count ──
        val colCount = when {
            subtypes.size <= 4 -> 2
            else -> 3
        }

        val grid = android.widget.GridLayout(this).apply {
            columnCount = colCount
            setPadding(dp(8), 0, dp(8), dp(12))
        }

        for (sub in subtypes) {
            val subCount = counts?.let { c ->
                sub.tags.sumOf { tag -> c.counts[tag] ?: 0 }
            }

            val cell = android.widget.FrameLayout(this).apply {
                val lp = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(56)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                layoutParams = lp
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.argb(77, Color.red(cat.color), Color.green(cat.color), Color.blue(cat.color)))
                    cornerRadius = dp(6).toFloat()
                }
                background = bg
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }

            val label = TextView(this).apply {
                text = sub.label
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 2
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM
                )
            }

            if (subCount != null && subCount > 0) {
                val badge = TextView(this).apply {
                    text = if (subCount >= 1000) "%.1fk".format(subCount / 1000.0) else subCount.toString()
                    textSize = 10f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.TOP or android.view.Gravity.END
                    )
                }
                cell.addView(badge)
            }

            cell.addView(label)

            cell.setOnClickListener {
                showFindResults(dialog, sub.label, sub.tags, cat)
            }

            cell.setOnLongClickListener {
                dialog.dismiss()
                enterFindFilterMode(sub.tags, sub.label)
                true
            }

            grid.addView(cell)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(grid)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.90).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(android.view.Gravity.CENTER)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showFindResults(
        dialog: android.app.Dialog,
        title: String,
        tags: List<String>,
        parentCategory: com.example.locationmapapp.ui.menu.PoiCategory?
    ) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        // ── Header with back ──
        val backBtn = TextView(this).apply {
            text = "\u2190"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(12), 0)
            setOnClickListener {
                if (parentCategory != null) {
                    showFindSubtypeGrid(dialog, parentCategory)
                } else {
                    showFindCategoryGrid(dialog)
                }
            }
        }
        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(backBtn)
            addView(titleText)
            addView(closeBtn)
        }

        // ── Loading spinner ──
        val spinner = android.widget.ProgressBar(this).apply {
            setPadding(0, dp(24), 0, dp(24))
        }

        val resultsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }

        val footer = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#757575"))
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(resultsList)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(header)
            addView(spinner)
            addView(scrollView)
            addView(footer)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.75).toInt())
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(android.view.Gravity.CENTER)
        }

        // Fetch results
        val center = binding.mapView.mapCenter
        lifecycleScope.launch {
            val response = viewModel.findNearbyDirectly(center.latitude, center.longitude, tags, 50)
            spinner.visibility = View.GONE

            if (response == null || response.results.isEmpty()) {
                resultsList.addView(TextView(this@MainActivity).apply {
                    text = "No results found nearby"
                    textSize = 14f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    setPadding(dp(16), dp(24), dp(16), dp(24))
                })
                return@launch
            }

            for (result in response.results) {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                // Left column: distance + direction
                val distText = TextView(this@MainActivity).apply {
                    text = formatDistanceDirection(
                        center.latitude, center.longitude,
                        result.lat, result.lon
                    )
                    textSize = 12f
                    setTextColor(Color.parseColor("#4FC3F7"))
                    layoutParams = LinearLayout.LayoutParams(dp(65), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(8)
                    }
                }

                // Right column: name + details
                val infoCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val nameText = TextView(this@MainActivity).apply {
                    text = result.name ?: result.typeValue.replaceFirstChar { it.uppercase() }
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                infoCol.addView(nameText)

                // Detail line (cuisine/type)
                val detailStr = result.detail ?: result.typeValue.replaceFirstChar { it.uppercase() }
                val detailText = TextView(this@MainActivity).apply {
                    text = detailStr
                    textSize = 12f
                    setTextColor(Color.parseColor("#9E9E9E"))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                infoCol.addView(detailText)

                // Address line
                if (result.address != null) {
                    val addrText = TextView(this@MainActivity).apply {
                        text = result.address
                        textSize = 11f
                        setTextColor(Color.parseColor("#616161"))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    infoCol.addView(addrText)
                }

                row.addView(distText)
                row.addView(infoCol)

                // Tap → POI detail dialog
                row.setOnClickListener {
                    dialog.dismiss()
                    showPoiDetailDialog(result)
                }

                resultsList.addView(row)

                // Separator line
                resultsList.addView(View(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { setMargins(dp(65), 0, 0, 0) }
                    setBackgroundColor(Color.parseColor("#2A2A2A"))
                })
            }

            // Footer
            val maxDistMi = if (response.results.isNotEmpty()) {
                response.results.last().distanceM / 1609.34
            } else 0.0
            footer.text = "Showing ${response.results.size} nearest (within %.1f mi)".format(maxDistMi)
        }
    }

    private fun poiCategoryColor(categoryTag: String): Int {
        return PoiCategories.ALL.firstOrNull { it.tags.contains(categoryTag) }?.color
            ?: Color.parseColor("#757575")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPoiDetailDialog(result: com.example.locationmapapp.data.model.FindResult) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }
        val catColor = poiCategoryColor(result.category)
        val center = binding.mapView.mapCenter

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // ── Header: color dot + compact distance + name + close ──
        val dot = View(this).apply {
            val size = dp(12)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(6) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(catColor)
            }
        }
        // Compact distance from current GPS: "1.4mi(NE)"
        val gpsLoc = viewModel.currentLocation.value
        val compactDist = if (gpsLoc != null) {
            val results = FloatArray(2)
            android.location.Location.distanceBetween(
                gpsLoc.latitude, gpsLoc.longitude, result.lat, result.lon, results
            )
            val distMi = results[0] / 1609.34
            val cardinal = bearingToCardinal(results[1])
            val distStr = if (distMi < 0.1) "%.0fft".format(results[0] * 3.28084)
                else if (distMi < 10) "%.1fmi".format(distMi)
                else "%.0fmi".format(distMi)
            "$distStr($cardinal)"
        } else null
        val distLabel = TextView(this).apply {
            text = compactDist ?: ""
            textSize = 12f
            setTextColor(Color.parseColor("#4FC3F7"))
            setPadding(0, 0, dp(8), 0)
            if (compactDist == null) visibility = View.GONE
        }
        val titleText = TextView(this).apply {
            text = result.name ?: result.typeValue.replaceFirstChar { it.uppercase() }
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(4))
            addView(dot)
            addView(distLabel)
            addView(titleText)
            addView(closeBtn)
        }

        // ── Color bar ──
        val colorBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
            )
            setBackgroundColor(catColor)
        }

        // ── Info rows ──
        val infoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        fun addInfoRow(label: String, value: String, tappable: Boolean = false, onClick: (() -> Unit)? = null) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 14f
                setTextColor(if (tappable) Color.parseColor("#4FC3F7") else Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (onClick != null) setOnClickListener { onClick() }
            })
            infoContainer.addView(row)
        }

        // Distance
        addInfoRow("Distance", formatDistanceDirection(
            center.latitude, center.longitude, result.lat, result.lon
        ))

        // Type
        val typeLine = buildString {
            append(result.typeValue.replaceFirstChar { it.uppercase() })
            result.detail?.let { append(" ($it)") }
        }
        addInfoRow("Type", typeLine)

        // Address
        if (result.address != null) addInfoRow("Address", result.address)

        // Phone (tappable)
        if (result.phone != null) {
            addInfoRow("Phone", result.phone, tappable = true) {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${result.phone}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    })
                } catch (_: Exception) {}
            }
        }

        // Hours
        if (result.openingHours != null) addInfoRow("Hours", result.openingHours)

        // ── Website button area ──
        val websiteArea = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#111111"))
        }

        // Spinner while resolving
        val loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        loadingLayout.addView(android.widget.ProgressBar(this))
        loadingLayout.addView(TextView(this).apply {
            text = "Resolving website..."
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        websiteArea.addView(loadingLayout)

        // Resolved URL stored here for Reviews button fallback
        var resolvedUrl: String? = null

        // Async resolve → show big button or "no website"
        lifecycleScope.launch {
            val websiteInfo = viewModel.fetchPoiWebsiteDirectly(
                result.type, result.id, result.name, result.lat, result.lon
            )
            websiteArea.removeAllViews()
            if (websiteInfo?.url != null) {
                resolvedUrl = websiteInfo.url
                websiteArea.addView(TextView(this@MainActivity).apply {
                    text = "\uD83C\uDF10  Load Website"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#1565C0"))
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        dp(260), dp(56)
                    ).apply { gravity = android.view.Gravity.CENTER }
                    setOnClickListener { showFullScreenWebView(websiteInfo.url, result.name ?: "Website") }
                })
            } else {
                websiteArea.addView(TextView(this@MainActivity).apply {
                    text = "No website available"
                    textSize = 14f
                    setTextColor(Color.parseColor("#616161"))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                })
            }
        }

        // ── Action buttons ──
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val buttonLp = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }

        // Directions button
        buttonContainer.addView(TextView(this).apply {
            text = "Directions"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#2E7D32"))
            layoutParams = buttonLp
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener {
                val dest = if (result.name != null) {
                    Uri.encode("${result.name}, ${result.lat},${result.lon}")
                } else {
                    "${result.lat},${result.lon}"
                }
                val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$dest"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    })
                } catch (_: Exception) {}
            }
        })

        // Call button
        buttonContainer.addView(TextView(this).apply {
            text = "Call"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1565C0"))
            layoutParams = buttonLp
            setPadding(dp(4), dp(8), dp(4), dp(8))
            alpha = if (result.phone != null) 1.0f else 0.4f
            setOnClickListener {
                if (result.phone != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${result.phone}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        })
                    } catch (_: Exception) {}
                }
            }
        })

        // Reviews button
        buttonContainer.addView(TextView(this).apply {
            text = "Reviews"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F57F17"))
            layoutParams = buttonLp
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener {
                val name = Uri.encode(result.name ?: "")
                val loc = Uri.encode("${result.lat},${result.lon}")
                val yelpUrl = "https://www.yelp.com/search?find_desc=$name&find_loc=$loc"
                showFullScreenWebView(yelpUrl, "Reviews")
            }
        })

        // Map button
        buttonContainer.addView(TextView(this).apply {
            text = "Map"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#546E7A"))
            layoutParams = buttonLp
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnClickListener {
                dialog.dismiss()
                val point = result.toGeoPoint()
                binding.mapView.controller.animateTo(point, 18.0, 800L)
                lifecycleScope.launch {
                    delay(1000)
                    loadCachedPoisForVisibleArea()
                }
            }
        })

        // ── Container ──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(header)
            addView(colorBar)
            addView(infoContainer)
            addView(websiteArea)
            addView(buttonContainer)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.85).toInt())
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(android.view.Gravity.CENTER)
        }
        dialog.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showFullScreenWebView(url: String, title: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }
        var webView: android.webkit.WebView? = null

        // ── Top bar: back + title + close ──
        val backBtn = TextView(this).apply {
            text = "\u2190"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                if (webView?.canGoBack() == true) webView?.goBack() else dialog.dismiss()
            }
        }
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { dialog.dismiss() }
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(backBtn)
            addView(titleText)
            addView(closeBtn)
        }

        // ── WebView ──
        val wv = android.webkit.WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            setInitialScale(0)
            setBackgroundColor(Color.BLACK)
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onRenderProcessGone(
                    view: android.webkit.WebView?,
                    detail: android.webkit.RenderProcessGoneDetail?
                ): Boolean {
                    dialog.dismiss()
                    return true
                }
            }
            loadUrl(url)
        }
        webView = wv

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(topBar)
            addView(wv)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            win.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            win.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.setOnDismissListener { wv.destroy() }
        dialog.show()
    }

    /** Format distance + cardinal direction between two points. */
    private fun formatDistanceDirection(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
        val results = FloatArray(2)
        android.location.Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
        val distM = results[0]
        val bearing = results[1]
        val cardinal = bearingToCardinal(bearing)
        val distMi = distM / 1609.34

        return when {
            distMi < 0.1 -> "%.0f ft %s".format(distM * 3.28084, cardinal)
            distMi < 10.0 -> "%.1f mi %s".format(distMi, cardinal)
            else -> "%.0f mi %s".format(distMi, cardinal)
        }
    }

    private fun bearingToCardinal(bearing: Float): String {
        val normalized = ((bearing % 360) + 360) % 360
        return when {
            normalized < 22.5 || normalized >= 337.5 -> "N"
            normalized < 67.5 -> "NE"
            normalized < 112.5 -> "E"
            normalized < 157.5 -> "SE"
            normalized < 202.5 -> "S"
            normalized < 247.5 -> "SW"
            normalized < 292.5 -> "W"
            else -> "NW"
        }
    }

    // ── Map Filter Mode ──────────────────────────────────────────────────────

    private fun enterFindFilterMode(tags: List<String>, label: String) {
        findFilterActive = true
        findFilterTags = tags
        findFilterLabel = label
        DebugLogger.i("MainActivity", "enterFindFilterMode: $label tags=$tags")

        // Show dismissible banner
        showFindFilterBanner(label)

        // Load filtered POIs
        loadFilteredPois()
    }

    @SuppressLint("SetTextI18n")
    private fun showFindFilterBanner(label: String) {
        removeFindFilterBanner()
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val banner = TextView(this).apply {
            text = "Showing: $label  \u2715"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC333333"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            gravity = android.view.Gravity.CENTER
            setOnClickListener { exitFindFilterMode() }
        }

        val parent = binding.mapView.parent as? ViewGroup ?: return
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP
        )
        parent.addView(banner, lp)
        findFilterBanner = banner
    }

    private fun removeFindFilterBanner() {
        findFilterBanner?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        findFilterBanner = null
    }

    private fun loadFilteredPois() {
        val center = binding.mapView.mapCenter
        lifecycleScope.launch {
            val response = viewModel.findNearbyDirectly(center.latitude, center.longitude, findFilterTags, 200)
            if (response != null) {
                val places = response.results.map { it.toPlaceResult() }
                replaceAllPoiMarkers(places)
            }
        }
    }

    private fun exitFindFilterMode() {
        DebugLogger.i("MainActivity", "exitFindFilterMode")
        findFilterActive = false
        findFilterTags = emptyList()
        findFilterLabel = ""
        removeFindFilterBanner()
        // Restore normal POI display
        loadCachedPoisForVisibleArea()
    }

    // ── Legend Dialog ─────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun showLegendDialog() {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // ── Header ──
        val titleText = TextView(this).apply {
            text = "Map Legend"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "\u2715"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { dialog.dismiss() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(titleText)
            addView(closeBtn)
        }

        // ── Scrollable content ──
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }

        fun sectionHeader(title: String) {
            content.addView(TextView(this).apply {
                text = title
                textSize = 13f
                setTextColor(Color.parseColor("#9E9E9E"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(6))
            })
        }

        fun legendRow(icon: android.graphics.drawable.Drawable, label: String, note: String? = null) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val iconView = android.widget.ImageView(this).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    marginEnd = dp(12)
                }
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            row.addView(iconView)
            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textCol.addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
            })
            if (note != null) {
                textCol.addView(TextView(this@MainActivity).apply {
                    text = note
                    textSize = 11f
                    setTextColor(Color.parseColor("#9E9E9E"))
                })
            }
            row.addView(textCol)
            content.addView(row)
        }

        fun colorDot(color: Int, sizeDp: Int = 16): android.graphics.drawable.BitmapDrawable {
            val px = dp(sizeDp)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; this.color = color; alpha = 180
            }
            c.drawCircle(px / 2f, px / 2f, px / 2f, p)
            p.alpha = 255
            c.drawCircle(px / 2f, px / 2f, px * 0.2f, p)
            return android.graphics.drawable.BitmapDrawable(resources, bmp)
        }

        fun colorRect(borderColor: Int, sizeDp: Int = 16): android.graphics.drawable.BitmapDrawable {
            val px = dp(sizeDp)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); this.color = borderColor
            }
            val r = dp(3).toFloat()
            c.drawRoundRect(android.graphics.RectF(dp(2).toFloat(), dp(2).toFloat(),
                (px - dp(2)).toFloat(), (px - dp(2)).toFloat()), r, r, p)
            return android.graphics.drawable.BitmapDrawable(resources, bmp)
        }

        fun gradientBar(): android.graphics.drawable.BitmapDrawable {
            val w = dp(32); val h = dp(16)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val colors = intArrayOf(
                Color.parseColor("#0000FF"), Color.parseColor("#00FF00"),
                Color.parseColor("#FFFF00"), Color.parseColor("#FF8C00"), Color.parseColor("#FF0000")
            )
            val grad = LinearGradient(0f, 0f, w.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
            c.drawRoundRect(android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), dp(3).toFloat(), dp(3).toFloat(), p)
            return android.graphics.drawable.BitmapDrawable(resources, bmp)
        }

        fun colorLine(lineColor: Int): android.graphics.drawable.BitmapDrawable {
            val w = dp(32); val h = dp(16)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
                this.color = lineColor; strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(dp(2).toFloat(), h / 2f, (w - dp(2)).toFloat(), h / 2f, p)
            return android.graphics.drawable.BitmapDrawable(resources, bmp)
        }

        // ═══════════════════════════════════════════════════════════════════════
        // YOUR LOCATION
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("YOUR LOCATION")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_gps, 24, Color.parseColor("#37474F")),
            "Your GPS Location")

        // ═══════════════════════════════════════════════════════════════════════
        // POINTS OF INTEREST
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("POINTS OF INTEREST")
        for (cat in com.example.locationmapapp.ui.menu.PoiCategories.ALL) {
            legendRow(colorDot(cat.color), cat.label)
        }
        legendRow(colorDot(Color.TRANSPARENT, 1), "", "Names shown at zoom \u2265 18")

        // ═══════════════════════════════════════════════════════════════════════
        // WEATHER
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("WEATHER")
        legendRow(colorRect(Color.parseColor("#2E7D32")), "METAR — VFR", "Visual Flight Rules")
        legendRow(colorRect(Color.parseColor("#1565C0")), "METAR — MVFR", "Marginal VFR")
        legendRow(colorRect(Color.parseColor("#C62828")), "METAR — IFR", "Instrument Flight Rules")
        legendRow(colorRect(Color.parseColor("#AD1457")), "METAR — LIFR", "Low IFR")
        legendRow(gradientBar(), "Radar", "Precipitation intensity")

        // ═══════════════════════════════════════════════════════════════════════
        // TRANSIT VEHICLES
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("TRANSIT VEHICLES")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#6A1B9A")),
            "Commuter Rail", "Purple")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#C62828")),
            "Red Line")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#E65100")),
            "Orange Line")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#1565C0")),
            "Blue Line")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#2E7D32")),
            "Green Line")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#546E7A")),
            "Silver Line")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_bus, 24, Color.parseColor("#00695C")),
            "Bus", "Arrow shows direction of travel")

        // ═══════════════════════════════════════════════════════════════════════
        // TRANSIT STOPS
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("TRANSIT STOPS")
        legendRow(MarkerIconHelper.stationIcon(this, Color.parseColor("#37474F")),
            "Train Station")
        legendRow(MarkerIconHelper.busStopIcon(this, Color.parseColor("#00695C")),
            "Bus Stop", "Visible at zoom \u2265 15")

        // ═══════════════════════════════════════════════════════════════════════
        // AIRCRAFT
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("AIRCRAFT")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#78909C")),
            "Ground", "Gray")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#2E7D32")),
            "< 5,000 ft", "Green — low altitude")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#1565C0")),
            "5,000 – 20,000 ft", "Blue — mid altitude")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#6A1B9A")),
            "> 20,000 ft", "Purple — high altitude")
        legendRow(colorRect(Color.RED), "Emergency (SPI)", "Special Purpose Indicator")
        legendRow(colorLine(Color.parseColor("#1565C0")), "Flight trail", "Altitude-colored polyline")

        // ═══════════════════════════════════════════════════════════════════════
        // CAMERAS
        // ═══════════════════════════════════════════════════════════════════════
        sectionHeader("CAMERAS")
        legendRow(MarkerIconHelper.get(this, R.drawable.ic_camera, 24, Color.parseColor("#455A64")),
            "Webcam", "Tap to preview, view live")

        // ── Assemble ──
        val scrollView = android.widget.ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(header)
            addView(scrollView)
        }

        dialog.setContentView(container)
        dialog.window?.let { win ->
            val dm = resources.displayMetrics
            win.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.85).toInt())
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setGravity(android.view.Gravity.CENTER)
        }
        dialog.show()
    }

    /** Get the route color for a vehicle (handles buses with numeric route IDs). */
    private fun vehicleRouteColor(vehicle: com.example.locationmapapp.data.model.MbtaVehicle): Int {
        // Buses — use teal for all bus routes
        if (vehicle.routeType == 3) return Color.parseColor("#00695C")
        // Rail/subway — use standard routeColor
        return routeColor(vehicle.routeId)
    }

    private fun startFollowing(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        followedAircraftIcao = null   // stop any aircraft follow
        followedVehicleId = vehicle.id
        DebugLogger.i("MainActivity", "Following vehicle ${vehicle.id} (${vehicle.label} — ${vehicle.routeName})")

        binding.mapView.controller.animateTo(vehicle.toGeoPoint())
        binding.mapView.controller.setZoom(16.0)

        showFollowBanner(vehicle)
    }

    private fun stopFollowing() {
        DebugLogger.i("MainActivity", "Stopped following vehicle=$followedVehicleId aircraft=$followedAircraftIcao")
        clearFlightTrail()
        followedVehicleId = null
        followedAircraftIcao = null
        followedAircraftFailCount = 0
        autoFollowEmptyPoiCount = 0
        stopFollowedAircraftRefresh()
        hideFollowBanner()
    }

    private fun updateFollowedVehicle(vehicles: List<com.example.locationmapapp.data.model.MbtaVehicle>) {
        val id = followedVehicleId ?: return
        val vehicle = vehicles.find { it.id == id }
        if (vehicle != null) {
            DebugLogger.i("MainActivity", "Follow update: ${vehicle.label} at ${vehicle.lat},${vehicle.lon}")
            binding.mapView.controller.animateTo(vehicle.toGeoPoint())
            showFollowBanner(vehicle)
            // Pre-fill cache: search POIs around the vehicle's current position
            val point = vehicle.toGeoPoint()
            DebugLogger.i("MainActivity", "Follow POI prefetch at ${point.latitude},${point.longitude}")
            viewModel.searchPoisAt(point)
            // Refresh full cache display after prefetch
            binding.mapView.postDelayed({ loadCachedPoisForVisibleArea() }, 3000)
        }
        // If not found in this list, it may be in another vehicle type's list — don't stop yet
    }

    private fun showFollowBanner(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        val typeLabel = when (vehicle.routeType) {
            3    -> "Bus"
            2    -> "Train"
            else -> "Subway"
        }
        val statusText = when {
            vehicle.stopName != null -> "${vehicle.currentStatus.display} ${vehicle.stopName}"
            else -> vehicle.currentStatus.display
        }
        val staleTag = vehicleStalenessTag(vehicle.updatedAt)
        val text = "Following $typeLabel ${vehicle.label} — ${vehicle.routeName}$staleTag\n" +
                   "$statusText  •  ${vehicle.speedDisplay}"

        if (followBanner == null) {
            followBanner = TextView(this).apply {
                setBackgroundColor(Color.parseColor("#DD212121"))
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(32, 20, 32, 20)
                val params = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
                }
                layoutParams = params
                elevation = 12f
                setOnClickListener { stopFollowing() }
            }
            (binding.root as ViewGroup).addView(followBanner)
        }
        followBanner?.text = text
        followBanner?.visibility = View.VISIBLE
    }

    private fun hideFollowBanner() {
        followBanner?.visibility = View.GONE
    }

    // ── Aircraft follow ─────────────────────────────────────────────────────

    private fun onAircraftMarkerTapped(state: com.example.locationmapapp.data.model.AircraftState) {
        // Stop populate scanner and silent fill — user is interacting with an object
        if (populateJob != null) stopPopulatePois()
        stopSilentFill()
        if (followedAircraftIcao == state.icao24) {
            stopFollowing()
        } else {
            startFollowingAircraft(state)
        }
    }

    private fun startFollowingAircraft(state: com.example.locationmapapp.data.model.AircraftState) {
        // Stop any existing vehicle follow
        followedVehicleId = null
        followedAircraftIcao = state.icao24
        lastFollowedAircraftState = state
        DebugLogger.i("MainActivity", "Following aircraft ${state.icao24} (${state.callsign})")

        binding.mapView.controller.animateTo(state.toGeoPoint())
        showAircraftFollowBanner(state)

        // Load DB flight history and draw trail
        clearFlightTrail()
        loadFlightTrailHistory(state.icao24, state)

        // Start dedicated icao24 refresh — tracks globally, not limited to bbox
        startFollowedAircraftRefresh(state.icao24)
    }

    private fun startFollowedAircraftRefresh(icao24: String) {
        followedAircraftRefreshJob?.cancel()
        DebugLogger.i("MainActivity", "Starting followed aircraft refresh for $icao24 every ${aircraftRefreshIntervalSec}s")
        followedAircraftRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(aircraftRefreshIntervalSec * 1000L)
                viewModel.loadFollowedAircraft(icao24)
            }
        }
    }

    private fun stopFollowedAircraftRefresh() {
        followedAircraftRefreshJob?.cancel()
        followedAircraftRefreshJob = null
        viewModel.clearFollowedAircraft()
        lastFollowedAircraftState = null
    }

    // ── Auto-follow random high-altitude aircraft ────────────────────────

    private fun startAutoFollowAircraft() {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        // Ensure aircraft layer is on
        if (!prefs.getBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, true)) {
            prefs.edit().putBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, true).apply()
            startAircraftRefresh()
            DebugLogger.i("MainActivity", "Auto-follow: enabled aircraft layer")
        }
        pickAndFollowRandomAircraft()
        autoFollowAircraftJob?.cancel()
        autoFollowAircraftJob = lifecycleScope.launch {
            while (true) {
                delay(20 * 60 * 1000L)  // 20 minutes
                DebugLogger.i("MainActivity", "Auto-follow: 20-min rotation — picking new aircraft")
                pickAndFollowRandomAircraft()
            }
        }
        toast("Auto-follow ON — picking high-altitude aircraft")
    }

    private fun stopAutoFollowAircraft() {
        autoFollowAircraftJob?.cancel()
        autoFollowAircraftJob = null
        stopFollowing()
        toast("Auto-follow stopped")
        DebugLogger.i("MainActivity", "Auto-follow stopped")
    }

    /**
     * Compute a wide bbox (~zoom 8) centered on the map center, query aircraft in that area,
     * and follow a random high-altitude one matching current direction preference.
     * Does NOT change the user's zoom or position.
     */
    private fun pickAndFollowRandomAircraft() {
        val dirLabel = "${if (autoFollowPreferWest) "W" else "E"}${if (autoFollowPreferSouth) "S" else "N"}"
        // First check already-loaded aircraft
        val currentList = viewModel.aircraft.value ?: emptyList()
        val candidates = filterHighAltitude(currentList)
        if (candidates.isNotEmpty()) {
            selectAndFollow(candidates)
            return
        }
        // Build a wide bbox (~zoom 8, covers most of CONUS) to maximize candidates
        val center = binding.mapView.mapCenter
        val halfLat = 3.0   // ~330 km north/south
        val halfLon = 4.0   // ~340 km east/west
        val south = center.latitude - halfLat
        val north = center.latitude + halfLat
        val west  = center.longitude - halfLon
        val east  = center.longitude + halfLon
        DebugLogger.i("MainActivity", "Auto-follow: wide bbox query ${south},${west},${north},${east} pref=$dirLabel")
        viewModel.loadAircraft(south, west, north, east)
        // Wait for the network response then pick
        lifecycleScope.launch {
            delay(3000)
            val freshList = viewModel.aircraft.value ?: emptyList()
            val freshCandidates = filterHighAltitude(freshList)
            if (freshCandidates.isNotEmpty()) {
                selectAndFollow(freshCandidates)
            } else {
                DebugLogger.i("MainActivity", "Auto-follow: no aircraft ≥ 10,000 ft in wide bbox — will retry in 20 min")
                toast("No aircraft above 10,000 ft — will retry")
            }
        }
    }

    private fun filterHighAltitude(
        aircraft: List<com.example.locationmapapp.data.model.AircraftState>
    ): List<com.example.locationmapapp.data.model.AircraftState> {
        return aircraft.filter { it.baroAltitude != null && it.baroAltitude * 3.28084 >= 10000 }
    }

    private fun selectAndFollow(candidates: List<com.example.locationmapapp.data.model.AircraftState>) {
        // Exclude currently followed aircraft for variety
        val currentIcao = followedAircraftIcao
        val others = candidates.filter { it.icao24 != currentIcao }
        val pool = if (others.isNotEmpty()) others else candidates

        // Filter by preferred E/W direction
        val ewFiltered = pool.filter { it.track != null && if (autoFollowPreferWest) it.track >= 180.0 && it.track <= 360.0 else it.track >= 0.0 && it.track < 180.0 }
        // Further filter by preferred N/S direction (secondary preference — relaxed if too few)
        val nsFiltered = ewFiltered.filter { it.track != null && if (autoFollowPreferSouth) it.track >= 90.0 && it.track <= 270.0 else it.track < 90.0 || it.track > 270.0 }

        val dirLabel = "${if (autoFollowPreferWest) "W" else "E"}${if (autoFollowPreferSouth) "S" else "N"}"
        val pick = if (nsFiltered.isNotEmpty()) {
            DebugLogger.i("MainActivity", "Auto-follow: ${nsFiltered.size} $dirLabel of ${pool.size} candidates")
            nsFiltered.random()
        } else if (ewFiltered.isNotEmpty()) {
            DebugLogger.i("MainActivity", "Auto-follow: ${ewFiltered.size} ${if (autoFollowPreferWest) "W" else "E"}-bound of ${pool.size} (relaxed N/S)")
            ewFiltered.random()
        } else {
            DebugLogger.i("MainActivity", "Auto-follow: no $dirLabel — picking from all ${pool.size}")
            pool.random()
        }
        val hdg = pick.track?.let { "%.0f°".format(it) } ?: "?"
        DebugLogger.i("MainActivity", "Auto-follow: selected ${pick.icao24} (${pick.callsign}) " +
                "alt=${"%.0f".format((pick.baroAltitude ?: 0.0) * 3.28084)} ft hdg=$hdg pref=$dirLabel")
        autoFollowEmptyPoiCount = 0
        followedAircraftFailCount = 0
        startFollowingAircraft(pick)
    }


    private fun updateFollowedAircraft(aircraft: List<com.example.locationmapapp.data.model.AircraftState>) {
        val icao = followedAircraftIcao ?: return
        // If the followed aircraft is in the bbox results, update its last known state
        val state = aircraft.find { it.icao24 == icao }
        if (state != null) {
            lastFollowedAircraftState = state
            showAircraftFollowBanner(state)
        }
        // Don't stop following if missing from bbox — the dedicated icao24 query handles that
    }

    private fun showAircraftFollowBanner(state: com.example.locationmapapp.data.model.AircraftState) {
        val label = state.callsign?.ifBlank { null } ?: state.icao24
        val altFt = state.baroAltitude?.let { "%.0f ft".format(it * 3.28084) } ?: "—"
        val speedKt = state.velocity?.let { "%.0f kt".format(it * 1.94384) } ?: "—"
        val vertDesc = state.verticalRate?.let {
            val fpm = it * 196.85
            when {
                fpm > 100  -> "\u2191 %.0f ft/min".format(fpm)
                fpm < -100 -> "\u2193 %.0f ft/min".format(fpm)
                else       -> "level"
            }
        } ?: ""
        val headingStr = state.track?.let { "%.0f\u00B0".format(it) } ?: ""
        val spiFlag = if (state.spi) "  \u26A0 SPI" else ""

        val dirLabel = if (autoFollowAircraftJob?.isActive == true) {
            val ew = if (autoFollowPreferWest) "W" else "E"
            val ns = if (autoFollowPreferSouth) "S" else "N"
            " [$ew$ns]"
        } else ""
        val prefix = if (autoFollowAircraftJob?.isActive == true) "Auto-following$dirLabel" else "Following"
        val text = "$prefix \u2708 $label$spiFlag\n" +
                   "Alt $altFt  •  $speedKt  •  $headingStr  •  $vertDesc"

        if (followBanner == null) {
            followBanner = TextView(this).apply {
                setBackgroundColor(Color.parseColor("#DD212121"))
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(32, 20, 32, 20)
                val params = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
                }
                layoutParams = params
                elevation = 12f
                setOnClickListener { stopFollowing() }
            }
            (binding.root as ViewGroup).addView(followBanner)
        }
        followBanner?.text = text
        followBanner?.visibility = View.VISIBLE
    }

    // =========================================================================
    // SILENT BACKGROUND POI FILL
    // =========================================================================

    /**
     * Starts a single background Overpass search at [center] to fill POI cache
     * for the current viewport. Cancels any previous silent fill in progress.
     * Shows a debug banner if PREF_SILENT_FILL_DEBUG is enabled.
     */
    private fun startSilentFill(center: org.osmdroid.util.GeoPoint) {
        silentFillJob?.cancel()
        // Don't run if full populate scanner is active
        if (populateJob != null) return
        // Don't run if following something (follow has its own POI prefetch)
        if (followedVehicleId != null || followedAircraftIcao != null) return

        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        val showDebug = prefs.getBoolean(AppBarMenuManager.PREF_SILENT_FILL_DEBUG, true)

        silentFillJob = lifecycleScope.launch {
            DebugLogger.i("MainActivity", "Silent fill starting at ${center.latitude},${center.longitude}")
            if (showDebug) showSilentFillBanner("Filling POIs\u2026")
            try {
                val result = viewModel.populateSearchAt(center)
                if (result != null) {
                    DebugLogger.i("MainActivity", "Silent fill complete — ${result.results.size} POIs (${result.poiNew} new) at ${result.radiusM}m")
                    loadCachedPoisForVisibleArea()
                    if (showDebug) {
                        showSilentFillBanner("Fill: ${result.results.size} POIs (${result.poiNew} new) at ${result.radiusM}m")
                        delay(3000)
                        hideSilentFillBanner()
                    }
                } else {
                    DebugLogger.w("MainActivity", "Silent fill returned null (error)")
                    if (showDebug) {
                        showSilentFillBanner("Fill failed")
                        delay(2000)
                        hideSilentFillBanner()
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                DebugLogger.i("MainActivity", "Silent fill cancelled")
                if (showDebug) hideSilentFillBanner()
            } catch (e: Exception) {
                DebugLogger.e("MainActivity", "Silent fill error: ${e.message}", e)
                if (showDebug) {
                    showSilentFillBanner("Fill error")
                    delay(2000)
                    hideSilentFillBanner()
                }
            }
        }
    }

    /** Schedule a silent fill after a delay, cancelling any pending/running fill first. */
    private fun scheduleSilentFill(center: org.osmdroid.util.GeoPoint, delayMs: Long) {
        silentFillRunnable?.let { binding.mapView.removeCallbacks(it) }
        val runnable = Runnable { startSilentFill(center) }
        silentFillRunnable = runnable
        binding.mapView.postDelayed(runnable, delayMs)
    }

    private fun stopSilentFill() {
        silentFillRunnable?.let { binding.mapView.removeCallbacks(it) }
        silentFillRunnable = null
        silentFillJob?.cancel()
        silentFillJob = null
        hideSilentFillBanner()
    }

    private fun showSilentFillBanner(text: String) {
        runOnUiThread {
            if (followBanner == null) {
                followBanner = TextView(this).apply {
                    setBackgroundColor(Color.parseColor("#DD212121"))
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setPadding(32, 20, 32, 20)
                    val params = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
                    }
                    layoutParams = params
                    elevation = 12f
                    setOnClickListener { stopSilentFill() }
                }
                (binding.root as ViewGroup).addView(followBanner)
            }
            followBanner?.setOnClickListener { stopSilentFill() }
            followBanner?.text = "\uD83D\uDD0D $text"
            followBanner?.visibility = View.VISIBLE
        }
    }

    private fun hideSilentFillBanner() {
        runOnUiThread { followBanner?.visibility = View.GONE }
    }

    // =========================================================================
    // POPULATE POIs — SYSTEMATIC GRID SCANNER
    // =========================================================================

    /** Mutable counters shared across the populate coroutine and recursive subdivision. */
    private class PopulateStats {
        var cells = 0       // main grid cells completed
        var pois = 0        // total POIs found
        var newPois = 0     // POIs new to cache
        var knownPois = 0   // POIs already in cache
        var searches = 0    // total successful searches (main + sub)
        var fails = 0       // total failed searches
        var subdivs = 0     // number of cells that triggered subdivision
        var gridRadius = 0  // calibrated grid radius from probe
        var currentRadius = 0  // radius of current/last search
        var depth = 0       // current subdivision depth (0 = main grid)
        var status = ""     // narrative status line
    }

    private fun startPopulatePois() {
        // Guard: don't allow while following something
        if (followedVehicleId != null || followedAircraftIcao != null) {
            toast("Stop following first")
            // Reset pref back to OFF
            val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean(AppBarMenuManager.PREF_POPULATE_POIS, false).apply()
            return
        }
        // Cancel silent fill — full scanner takes over
        stopSilentFill()

        val center = binding.mapView.mapCenter
        val centerLat = center.latitude
        val centerLon = center.longitude
        DebugLogger.i("MainActivity", "startPopulatePois() center=$centerLat,$centerLon")

        val stats = PopulateStats()
        var consecutiveErrors = 0

        populateJob = lifecycleScope.launch {
            // ── Phase 1: Probe center to discover settled radius ──
            val probePoint = GeoPoint(centerLat, centerLon)
            placeScanningMarker(probePoint)
            stats.status = "Probing center to calibrate grid\u2026"
            showPopulateBanner(0, stats, 0)

            var probeResult: com.example.locationmapapp.data.model.PopulateSearchResult? = null
            for (probeAttempt in 1..3) {
                probeResult = viewModel.populateSearchAt(probePoint)
                if (probeResult != null) break
                DebugLogger.w("MainActivity", "Probe attempt $probeAttempt failed — retrying in 30s")
                stats.status = "Probe attempt $probeAttempt failed — retrying\u2026"
                for (sec in 30 downTo 1) {
                    showPopulateBanner(0, stats, sec)
                    delay(1_000L)
                }
            }
            if (probeResult == null) {
                toast("Probe failed after 3 attempts — cannot calibrate grid")
                stopPopulatePois()
                return@launch
            }

            val settledRadius = probeResult.radiusM
            stats.pois += probeResult.results.size
            stats.newPois += probeResult.poiNew
            stats.knownPois += probeResult.poiKnown
            stats.searches++
            stats.cells++
            loadCachedPoisForVisibleArea()

            // ── Phase 2: Calculate grid from settled radius ──
            val stepLat = 0.8 * 2 * settledRadius.toDouble() / 111320.0
            val stepLon = stepLat / Math.cos(Math.toRadians(centerLat))
            DebugLogger.i("MainActivity",
                "Populate grid calibrated: settledRadius=${settledRadius}m, stepLat=${"%.5f".format(stepLat)}°, stepLon=${"%.5f".format(stepLon)}°")
            stats.gridRadius = settledRadius
            stats.currentRadius = settledRadius
            stats.status = "Grid calibrated at ${settledRadius}m — ${probeResult.results.size} POIs (${probeResult.poiNew} new)"

            // No subdivision needed for probe — grid is calibrated from its settled radius,
            // so ring 1 cells inherently cover the area around center at the right spacing.

            // Countdown after probe
            for (sec in 30 downTo 1) {
                showPopulateBanner(0, stats, sec)
                delay(1_000L)
            }

            // ── Phase 3: Spiral outward using calibrated grid ──
            for (ring in 1..15) {
                val points = generateRingPoints(ring, centerLat, centerLon, stepLat, stepLon)
                var pointIdx = 0
                while (pointIdx < points.size) {
                    kotlinx.coroutines.yield()

                    val (gridLat, gridLon) = points[pointIdx]
                    val point = GeoPoint(gridLat, gridLon)

                    stats.depth = 0
                    stats.currentRadius = settledRadius
                    stats.status = "Searching cell ${pointIdx + 1}/${points.size} at ${settledRadius}m\u2026"
                    placeScanningMarker(point)
                    showPopulateBanner(ring, stats, 0)

                    val result = viewModel.populateSearchAt(point)

                    if (result != null) {
                        consecutiveErrors = 0
                        stats.pois += result.results.size
                        stats.newPois += result.poiNew
                        stats.knownPois += result.poiKnown
                        stats.searches++
                        stats.cells++
                        stats.currentRadius = result.radiusM
                        pointIdx++
                        loadCachedPoisForVisibleArea()

                        val newStr = if (result.poiNew > 0) "${result.poiNew} new" else "all known"
                        stats.status = "Found ${result.results.size} POIs ($newStr) at ${result.radiusM}m"

                        // Recursive subdivision if settled smaller than grid radius
                        if (result.radiusM < settledRadius) {
                            stats.status = "Dense area! ${settledRadius}m\u2192${result.radiusM}m — filling 8 gaps"
                            showPopulateBanner(ring, stats, 0)
                            searchCellSubdivisions(point, settledRadius, result.radiusM, 0, ring, stats)
                            stats.depth = 0
                            stats.currentRadius = settledRadius
                            stats.status = "Subdivision done — back to main grid"
                        }
                    } else {
                        stats.fails++
                        stats.status = "Search failed — retrying\u2026"
                        consecutiveErrors++
                        if (consecutiveErrors >= 5) {
                            toast("Populate stopped — 5 consecutive errors")
                            DebugLogger.w("MainActivity", "Populate auto-stopped after 5 consecutive errors")
                            break
                        }
                    }

                    // Countdown 30s between main grid cells
                    for (sec in 30 downTo 1) {
                        showPopulateBanner(ring, stats, sec)
                        delay(1_000L)
                    }
                }
                if (consecutiveErrors >= 5) break
            }

            DebugLogger.i("MainActivity",
                "Populate complete: ${stats.cells} cells, ${stats.pois} POIs, ${stats.searches} searches, ${stats.fails} fail, ${stats.subdivs} subdivisions")
            stopPopulatePois()
            toast("Populate done — ${stats.cells} cells, ${stats.pois} POIs")
        }
    }

    /**
     * Recursive 3x3 subdivision: when a cell settles at a smaller radius than the grid expects,
     * search 8 surrounding fill-in points at the settled radius spacing.
     * If any of those also settle smaller, recurse again. Stops at MIN_RADIUS.
     */
    private suspend fun searchCellSubdivisions(
        center: GeoPoint,
        gridRadius: Int,      // radius this grid level was designed for
        settledRadius: Int,   // radius the center actually settled at
        depth: Int,
        ring: Int,
        stats: PopulateStats
    ) {
        val minRadius = com.example.locationmapapp.data.repository.PlacesRepository.MIN_RADIUS_M
        if (settledRadius < minRadius * 2) return  // can't subdivide further

        stats.subdivs++
        val subStepLat = 0.8 * 2 * settledRadius.toDouble() / 111320.0
        val subStepLon = subStepLat / Math.cos(Math.toRadians(center.latitude))

        DebugLogger.i("MainActivity",
            "Subdivide depth=$depth: grid=${gridRadius}m → settled=${settledRadius}m, 8 fill points around ${center.latitude},${center.longitude}")

        var fillIdx = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dy == 0 && dx == 0) continue  // center already searched
                fillIdx++
                kotlinx.coroutines.yield()

                val subPoint = GeoPoint(
                    center.latitude + dy * subStepLat,
                    center.longitude + dx * subStepLon
                )
                stats.depth = depth + 1
                stats.currentRadius = settledRadius
                stats.status = "Fill $fillIdx/8 at ${settledRadius}m (depth ${depth + 1})\u2026"
                placeScanningMarker(subPoint)
                showPopulateBanner(ring, stats, 0)

                val result = viewModel.populateSearchAt(subPoint)
                if (result != null) {
                    stats.pois += result.results.size
                    stats.newPois += result.poiNew
                    stats.knownPois += result.poiKnown
                    stats.searches++
                    stats.currentRadius = result.radiusM
                    val newStr = if (result.poiNew > 0) "${result.poiNew} new" else "all known"
                    stats.status = "Fill $fillIdx/8: ${result.results.size} POIs ($newStr) at ${result.radiusM}m"
                    showPopulateBanner(ring, stats, 0)
                    loadCachedPoisForVisibleArea()

                    // Recurse if this sub-cell settled even smaller
                    if (result.radiusM < settledRadius) {
                        stats.status = "Deeper! ${settledRadius}m\u2192${result.radiusM}m — subdividing again"
                        showPopulateBanner(ring, stats, 0)
                        searchCellSubdivisions(subPoint, settledRadius, result.radiusM, depth + 1, ring, stats)
                    }
                } else {
                    stats.fails++
                    stats.status = "Fill $fillIdx/8 failed"
                }
            }
        }
    }

    private fun stopPopulatePois() {
        populateJob?.cancel()
        populateJob = null
        removeScanningMarker()
        hideFollowBanner()
        loadCachedPoisForVisibleArea()
        // Reset menu pref
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean(AppBarMenuManager.PREF_POPULATE_POIS, false).apply()
        DebugLogger.i("MainActivity", "stopPopulatePois()")
    }

    /**
     * Generate the ordered perimeter points for a square spiral ring.
     * Ring 0 = center (1 point), Ring N = perimeter of (2N+1)² grid (8N points).
     */
    private fun generateRingPoints(
        ring: Int, centerLat: Double, centerLon: Double,
        stepLat: Double, stepLon: Double
    ): List<Pair<Double, Double>> {
        if (ring == 0) return listOf(Pair(centerLat, centerLon))

        val points = mutableListOf<Pair<Double, Double>>()
        val n = ring

        // Top edge: left to right
        for (dx in -n..n) {
            points.add(Pair(centerLat + n * stepLat, centerLon + dx * stepLon))
        }
        // Right edge: top-1 to bottom
        for (dy in (n - 1) downTo -n) {
            points.add(Pair(centerLat + dy * stepLat, centerLon + n * stepLon))
        }
        // Bottom edge: right-1 to left
        for (dx in (n - 1) downTo -n) {
            points.add(Pair(centerLat - n * stepLat, centerLon + dx * stepLon))
        }
        // Left edge: bottom+1 to top-1
        for (dy in (-n + 1)..(n - 1)) {
            points.add(Pair(centerLat + dy * stepLat, centerLon - n * stepLon))
        }

        return points
    }

    private fun placeScanningMarker(point: GeoPoint) {
        runOnUiThread {
            if (scanningMarker == null) {
                scanningMarker = Marker(binding.mapView).apply {
                    icon = MarkerIconHelper.forCategory(this@MainActivity, "crosshair", 32)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Scanning…"
                }
                binding.mapView.overlays.add(scanningMarker)
            }
            scanningMarker?.position = point
            // Zoom to 14 if zoomed out; leave alone if already 14+
            if (binding.mapView.zoomLevelDouble < 14.0) {
                binding.mapView.controller.setZoom(14.0)
            }
            binding.mapView.controller.animateTo(point)
        }
    }

    private fun removeScanningMarker() {
        scanningMarker?.let { binding.mapView.overlays.remove(it) }
        scanningMarker = null
        binding.mapView.invalidate()
    }

    private fun showPopulateBanner(ring: Int, stats: PopulateStats, countdown: Int) {
        val countdownStr = if (countdown > 0) "  Next in ${countdown}s" else ""
        val failStr = if (stats.fails > 0) " \u26A0${stats.fails}err" else ""
        val gridStr = if (stats.gridRadius > 0) "grid ${stats.gridRadius}m" else "probing"
        val line1 = "\u2316 R$ring | ${stats.cells} cells | ${stats.pois} POIs (${stats.newPois} new)$failStr | $gridStr"
        val line2 = stats.status + countdownStr
        val text = "$line1\n$line2 — tap to stop"

        runOnUiThread {
            if (followBanner == null) {
                followBanner = TextView(this).apply {
                    setBackgroundColor(Color.parseColor("#DD212121"))
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setPadding(32, 20, 32, 20)
                    val params = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
                    }
                    layoutParams = params
                    elevation = 12f
                    setOnClickListener { stopPopulatePois() }
                }
                (binding.root as ViewGroup).addView(followBanner)
            }
            followBanner?.setOnClickListener { stopPopulatePois() }
            followBanner?.text = text
            followBanner?.visibility = View.VISIBLE
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

    private val menuEventListenerImpl = object : MenuEventListener {

        // ── Radar (live) ──────────────────────────────────────────────────────

        override fun onRadarToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onRadarToggled: enabled=$enabled")
            toggleRadar()
            if (enabled) radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { viewModel.refreshRadar() }
            else         radarScheduler.stop()
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

        // ── GPS Alerts ────────────────────────────────────────────────────────

        override fun onWeatherAlertsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onWeatherAlertsToggled: $enabled")
            if (enabled) viewModel.fetchWeatherAlerts()
        }

        override fun onWeatherBannerToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onWeatherBannerToggled: $enabled")
            // STUB: show/hide scrolling weather ribbon at bottom of map
            stub("weather_banner", enabled)
        }

        override fun onHighwayAlertsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onHighwayAlertsToggled: $enabled")
            // STUB: overlay highway incident markers from traffic API
            stub("highway_alerts", enabled)
        }

        override fun onHighwayAlertsFrequencyChanged(minutes: Int) {
            DebugLogger.i("MainActivity", "onHighwayAlertsFrequencyChanged: ${minutes}min")
            // STUB: update refresh interval for highway alerts scheduler
            stub("highway_alerts_freq", minutes)
        }

        override fun onTrafficSpeedToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onTrafficSpeedToggled: $enabled")
            // STUB: color road polylines by current traffic speed
            stub("traffic_speed", enabled)
        }

        override fun onTrafficSpeedFrequencyChanged(minutes: Int) {
            DebugLogger.i("MainActivity", "onTrafficSpeedFrequencyChanged: ${minutes}min")
            // STUB: update refresh interval for traffic speed layer
            stub("traffic_speed_freq", minutes)
        }

        override fun onMetarDisplayToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMetarDisplayToggled: $enabled")
            if (enabled) { loadMetarsForVisibleArea(); toast("Loading METARs…") }
            else          clearMetarMarkers()
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
                viewModel.clearAircraft()
                clearAircraftMarkers()
                if (followedAircraftIcao != null) stopFollowing()
                // Cancel auto-follow if aircraft layer turned off
                if (autoFollowAircraftJob?.isActive == true) {
                    autoFollowAircraftJob?.cancel()
                    autoFollowAircraftJob = null
                    val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean(AppBarMenuManager.PREF_AUTO_FOLLOW_AIRCRAFT, false).apply()
                    DebugLogger.i("MainActivity", "Auto-follow cancelled — aircraft layer turned off")
                }
            }
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
                viewModel.fetchMbtaStations()
                toast("Loading train stations…")
            } else {
                viewModel.clearMbtaStations()
                clearStationMarkers()
            }
        }

        override fun onMbtaBusStopsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onMbtaBusStopsToggled: $enabled")
            if (enabled) {
                viewModel.fetchMbtaBusStops()
                toast("Loading bus stops…")
            } else {
                viewModel.clearMbtaBusStops()
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
                viewModel.clearMbtaTrains()
                if (followedVehicleId != null) stopFollowing()
            }
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
                viewModel.clearMbtaSubway()
                if (followedVehicleId != null) stopFollowing()
            }
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
                viewModel.clearMbtaBuses()
                if (followedVehicleId != null) stopFollowing()
            }
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
                viewModel.clearWebcams()
                clearWebcamMarkers()
            }
        }

        override fun onWebcamCategoriesChanged(categories: Set<String>) {
            DebugLogger.i("MainActivity", "onWebcamCategoriesChanged: $categories")
            val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
            if (prefs.getBoolean(AppBarMenuManager.PREF_WEBCAMS_ON, true)) {
                if (categories.isEmpty()) {
                    viewModel.clearWebcams()
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
            val loc = viewModel.currentLocation.value ?: GeoPoint(42.3601, -71.0589)
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

        override fun onSilentFillDebugToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onSilentFillDebugToggled: $enabled")
            // Toggle only controls banner visibility — silent fill always runs
            if (!enabled) hideSilentFillBanner()
        }

        override fun onGpsModeToggled(autoGps: Boolean) {
            DebugLogger.i("MainActivity", "onGpsModeToggled: autoGps=$autoGps")
            toggleLocationMode()
        }

        // ── Find / Legend ─────────────────────────────────────────────────────

        override fun onFindRequested() {
            DebugLogger.i("MainActivity", "onFindRequested")
            showFindDialog()
        }

        override fun onLegendRequested() {
            DebugLogger.i("MainActivity", "onLegendRequested")
            showLegendDialog()
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // =========================================================================
    // DEBUG HTTP SERVER — accessor methods
    // =========================================================================

    /** Returns the map view for direct control from DebugEndpoints. */
    internal fun debugMapView() = binding.mapView

    /** Returns a snapshot of current app state for /state and /map endpoints. */
    internal fun debugState(): Map<String, Any?> {
        val map = binding.mapView
        val bb = map.boundingBox
        return mapOf(
            "center" to mapOf("lat" to map.mapCenter.latitude, "lon" to map.mapCenter.longitude),
            "zoom" to map.zoomLevelDouble,
            "bounds" to mapOf(
                "north" to bb.latNorth, "south" to bb.latSouth,
                "east" to bb.lonEast, "west" to bb.lonWest
            ),
            "markers" to mapOf(
                "poi" to poiMarkers.values.sumOf { it.size },
                "stations" to stationMarkers.size,
                "busStops" to busStopMarkers.size,
                "busStopsTotal" to allBusStops.size,
                "trains" to trainMarkers.size,
                "subway" to subwayMarkers.size,
                "buses" to busMarkers.size,
                "aircraft" to aircraftMarkers.size,
                "webcams" to webcamMarkers.size,
                "metar" to metarMarkers.size,
                "gps" to if (gpsMarker != null) 1 else 0,
                "flightTrailPoints" to flightTrailPoints.size,
                "flightTrailSegments" to flightTrailOverlays.size
            ),
            "followedVehicle" to followedVehicleId,
            "followedAircraft" to followedAircraftIcao,
            "findFilter" to mapOf(
                "active" to findFilterActive,
                "label" to findFilterLabel,
                "tags" to findFilterTags
            ),
            "silentFill" to (silentFillJob?.isActive == true),
            "overlays" to map.overlays.size
        )
    }

    /** Returns serializable marker info for /markers endpoint. */
    internal fun debugMarkers(type: String?): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        fun add(markers: List<Marker>, markerType: String) {
            markers.forEachIndexed { i, m ->
                val info = mutableMapOf<String, Any?>(
                    "type" to markerType,
                    "index" to i,
                    "lat" to m.position.latitude,
                    "lon" to m.position.longitude,
                    "title" to (m.title ?: ""),
                    "snippet" to (m.snippet ?: "")
                )
                // Include key fields from relatedObject when present
                when (val obj = m.relatedObject) {
                    is com.example.locationmapapp.data.model.MbtaVehicle -> {
                        info["vehicleId"] = obj.id
                        info["label"] = obj.label
                        info["routeId"] = obj.routeId
                        info["routeName"] = obj.routeName
                        info["tripId"] = obj.tripId
                        info["headsign"] = obj.headsign
                        info["stopId"] = obj.stopId
                        info["stopName"] = obj.stopName
                        info["bearing"] = obj.bearing
                        info["speedMph"] = obj.speedMph
                        info["currentStatus"] = obj.currentStatus.display
                        info["updatedAt"] = obj.updatedAt
                        info["routeType"] = obj.routeType
                    }
                    is com.example.locationmapapp.data.model.MbtaStop -> {
                        info["stopId"] = obj.id
                        info["stopName"] = obj.name
                        info["routeIds"] = obj.routeIds
                    }
                    is com.example.locationmapapp.data.model.AircraftState -> {
                        info["icao24"] = obj.icao24
                        info["callsign"] = obj.callsign
                        info["altitude_ft"] = obj.baroAltitude?.let { it * 3.28084 }
                        info["velocity_kt"] = obj.velocity?.let { it * 1.94384 }
                        info["track"] = obj.track
                        info["onGround"] = obj.onGround
                    }
                    is com.example.locationmapapp.data.model.Webcam -> {
                        info["webcamId"] = obj.id
                        info["categories"] = obj.categories
                        info["status"] = obj.status
                    }
                }
                result.add(info)
            }
        }
        when (type) {
            "poi"       -> poiMarkers.values.flatten().let { add(it, "poi") }
            "stations"  -> add(stationMarkers, "stations")
            "bus_stops" -> add(busStopMarkers, "bus_stops")
            "trains"    -> add(trainMarkers, "trains")
            "subway"    -> add(subwayMarkers, "subway")
            "buses"     -> add(busMarkers, "buses")
            "aircraft"  -> add(aircraftMarkers, "aircraft")
            "webcams"   -> add(webcamMarkers, "webcams")
            "metar"     -> add(metarMarkers, "metar")
            "gps"       -> gpsMarker?.let { add(listOf(it), "gps") }
            null -> {
                poiMarkers.values.flatten().let { add(it, "poi") }
                add(stationMarkers, "stations")
                add(busStopMarkers, "bus_stops")
                add(trainMarkers, "trains")
                add(subwayMarkers, "subway")
                add(busMarkers, "buses")
                add(aircraftMarkers, "aircraft")
                add(webcamMarkers, "webcams")
                add(metarMarkers, "metar")
                gpsMarker?.let { add(listOf(it), "gps") }
            }
            else -> {} // unknown type — empty list
        }
        return result
    }

    /** Triggers the click handler on a marker, as if the user tapped it. */
    internal fun debugTapMarker(marker: Marker) {
        // Invoke the marker's custom OnMarkerClickListener if set, otherwise default
        val listener = try {
            val field = Marker::class.java.getDeclaredField("mOnMarkerClickListener")
            field.isAccessible = true
            field.get(marker) as? Marker.OnMarkerClickListener
        } catch (_: Exception) { null }
        if (listener != null) {
            listener.onMarkerClick(marker, binding.mapView)
        } else {
            // No custom listener — show info window (default behavior)
            marker.showInfoWindow()
        }
    }

    /** Returns raw Marker objects for /markers/tap endpoint. */
    internal fun debugRawMarkers(type: String): List<Marker> {
        return when (type) {
            "poi"       -> poiMarkers.values.flatten()
            "stations"  -> stationMarkers
            "bus_stops" -> busStopMarkers
            "trains"    -> trainMarkers
            "subway"    -> subwayMarkers
            "buses"     -> busMarkers
            "aircraft"  -> aircraftMarkers
            "webcams"   -> webcamMarkers
            "metar"     -> metarMarkers
            "gps"       -> listOfNotNull(gpsMarker)
            else        -> emptyList()
        }
    }

    /** Toggle a preference and fire the corresponding layer handler. */
    internal fun debugTogglePref(pref: String, value: Boolean) {
        val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean(pref, value).apply()
        val toggleMap = mapOf<String, (Boolean) -> Unit>(
            AppBarMenuManager.PREF_MBTA_STATIONS   to { menuEventListenerImpl.onMbtaStationsToggled(it) },
            AppBarMenuManager.PREF_MBTA_BUS_STOPS  to { menuEventListenerImpl.onMbtaBusStopsToggled(it) },
            AppBarMenuManager.PREF_MBTA_TRAINS     to { menuEventListenerImpl.onMbtaTrainsToggled(it) },
            AppBarMenuManager.PREF_MBTA_SUBWAY     to { menuEventListenerImpl.onMbtaSubwayToggled(it) },
            AppBarMenuManager.PREF_MBTA_BUSES      to { menuEventListenerImpl.onMbtaBusesToggled(it) },
            AppBarMenuManager.PREF_RADAR_ON         to { menuEventListenerImpl.onRadarToggled(it) },
            AppBarMenuManager.PREF_METAR_DISPLAY    to { menuEventListenerImpl.onMetarDisplayToggled(it) },
            AppBarMenuManager.PREF_AIRCRAFT_DISPLAY to { menuEventListenerImpl.onAircraftDisplayToggled(it) },
            AppBarMenuManager.PREF_WEBCAMS_ON       to { menuEventListenerImpl.onWebcamToggled(it) },
            AppBarMenuManager.PREF_WEATHER_ALERTS   to { menuEventListenerImpl.onWeatherAlertsToggled(it) },
            AppBarMenuManager.PREF_NAT_ALERTS       to { menuEventListenerImpl.onNationalAlertsToggled(it) },
        )
        toggleMap[pref]?.invoke(value)
            ?: DebugLogger.w("DebugHttp", "debugTogglePref: unknown pref '$pref'")
    }

    /** Force refresh a specific data layer. */
    internal fun debugRefreshLayer(layer: String) {
        when (layer) {
            "trains"   -> viewModel.fetchMbtaTrains()
            "subway"   -> viewModel.fetchMbtaSubway()
            "buses"    -> viewModel.fetchMbtaBuses()
            "stations"  -> viewModel.fetchMbtaStations()
            "bus_stops" -> viewModel.fetchMbtaBusStops()
            "aircraft"  -> loadAircraftForVisibleArea()
            "metar"    -> loadMetarsForVisibleArea()
            "webcams"  -> loadWebcamsForVisibleArea()
            "pois"     -> loadCachedPoisForVisibleArea()
            "radar"    -> viewModel.refreshRadar()
            else -> DebugLogger.w("DebugHttp", "debugRefreshLayer: unknown layer '$layer'")
        }
    }

    /** Follow an aircraft by icao24. */
    internal fun debugFollowAircraft(icao24: String) {
        // Start following directly — no dialog
        followedVehicleId = null
        followedAircraftIcao = icao24
        followedAircraftFailCount = 0
        clearFlightTrail()
        loadFlightTrailHistory(icao24)
        viewModel.loadFollowedAircraft(icao24)
        startFollowedAircraftRefresh(icao24)
    }

    /** Follow a vehicle by type and marker index — starts follow directly (bypasses detail dialog). */
    internal fun debugFollowVehicleByIndex(type: String, index: Int): Map<String, Any?> {
        val markers = debugRawMarkers(type)
        if (index < 0 || index >= markers.size) {
            return mapOf("error" to "Index $index out of range (0..${markers.size - 1})")
        }
        val marker = markers[index]
        // Get the vehicle from relatedObject and start following directly
        val vehicle = marker.relatedObject as? com.example.locationmapapp.data.model.MbtaVehicle
        if (vehicle != null) {
            startFollowing(vehicle)
        } else {
            // Fallback: tap the marker (opens detail dialog)
            debugTapMarker(marker)
        }
        return mapOf(
            "status" to "ok",
            "following" to type,
            "index" to index,
            "title" to (marker.title ?: ""),
            "vehicleId" to (vehicle?.id ?: ""),
            "position" to mapOf("lat" to marker.position.latitude, "lon" to marker.position.longitude)
        )
    }

    /** Stop following any vehicle or aircraft. */
    internal fun debugStopFollow() {
        stopFollowing()
    }

    /** Returns raw MbtaVehicle list from LiveData for /vehicles endpoint. */
    internal fun debugVehicles(type: String): List<com.example.locationmapapp.data.model.MbtaVehicle> {
        return when (type) {
            "trains" -> viewModel.mbtaTrains.value ?: emptyList()
            "subway" -> viewModel.mbtaSubway.value ?: emptyList()
            "buses"  -> viewModel.mbtaBuses.value ?: emptyList()
            else     -> emptyList()
        }
    }

    /** Returns raw MbtaStop list from LiveData for /stations endpoint. */
    internal fun debugStations(): List<com.example.locationmapapp.data.model.MbtaStop> {
        return viewModel.mbtaStations.value ?: emptyList()
    }

    /** Returns all cached bus stops for /bus-stops endpoint. */
    internal fun debugBusStops(): List<com.example.locationmapapp.data.model.MbtaStop> {
        return allBusStops
    }
}
