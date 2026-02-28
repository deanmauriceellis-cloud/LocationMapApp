package com.example.locationmapapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.TcpLogStreamer
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
    private var routeOverlay: Polyline? = null
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

    // POI label zoom threshold tracking
    private var poiLabelsShowing = false

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
        TcpLogStreamer.start()

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

        // Defer METAR restore until GPS fix so the map has a real bounding box
        if (prefs.getBoolean(AppBarMenuManager.PREF_METAR_DISPLAY, true)) {
            pendingMetarRestore = true
            DebugLogger.i("MainActivity", "onStart() METAR restore deferred — waiting for GPS fix")
        }

        // Defer Aircraft restore
        if (prefs.getBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, true)) {
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
        // Always load full cached POI coverage after GPS fix
        pendingCachedPoiLoad = true
    }

    override fun onStop()   { super.onStop();   radarScheduler.stop(); DebugLogger.i("MainActivity","onStop()") }
    override fun onResume() { super.onResume(); binding.mapView.onResume(); DebugLogger.i("MainActivity","onResume()") }
    override fun onPause()  { super.onPause();  binding.mapView.onPause();  DebugLogger.i("MainActivity","onPause()") }

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
                viewModel.setManualLocation(p)
                binding.mapView.controller.animateTo(p)
                triggerFullSearch(p)
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
                // Debounce: load cached POIs for visible bbox 500ms after scrolling stops
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(500)
                    loadCachedPoisForVisibleArea()
                }
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                updateZoomBubble()
                scheduleAircraftReload()
                // Refresh POI marker icons when crossing the zoom-18 label threshold
                val nowLabeled = binding.mapView.zoomLevelDouble >= 18.0
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
        val wasOn = prefs.getBoolean(AppBarMenuManager.PREF_AIRCRAFT_DISPLAY, true)
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
            // Fire deferred POI queries now that we have a real GPS position
            if (pendingPoiRestore) {
                pendingPoiRestore = false
                val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
                DebugLogger.i("MainActivity", "POI restore triggered at lat=${point.latitude} lon=${point.longitude}")
                for (cat in PoiCategories.ALL) {
                    if (prefs.getBoolean(cat.prefKey, true)) {
                        val tags = appBarMenuManager.getActiveTags(cat.id)
                        viewModel.searchPoisAt(point, tags, cat.id)
                    }
                }
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
        }
        viewModel.weatherAlerts.observe(this) { alerts ->
            DebugLogger.i("MainActivity", "weatherAlerts → ${alerts.size} alerts")
            if (alerts.isNotEmpty()) toast("${alerts.size} weather alert(s) active")
        }
        viewModel.radarRefreshTick.observe(this) {
            DebugLogger.i("MainActivity", "radarRefreshTick → refreshing overlay")
            refreshRadarOverlay()
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
            // Pre-fill POI cache at aircraft's current position
            val point = state.toGeoPoint()
            DebugLogger.i("MainActivity", "Aircraft follow POI prefetch at ${point.latitude},${point.longitude}")
            viewModel.searchPoisAt(point)
            // Refresh full cache display after prefetch; check for empty POI zone (auto-follow)
            binding.mapView.postDelayed({
                loadCachedPoisForVisibleArea()
                // If auto-follow is active, check altitude and POI desert
                if (autoFollowAircraftJob?.isActive == true) {
                    val lat = state.lat
                    val lon = state.lon
                    // Check if aircraft left the continental US (north into Canada or south past border)
                    if (lat > 49.0 || lat < 25.0 || lon < -125.0 || lon > -66.0) {
                        DebugLogger.i("MainActivity", "Auto-follow: aircraft outside US bounds (${lat},${lon}) — switching to interior")
                        toast("Outside US — switching to interior flight")
                        autoFollowEmptyPoiCount = 0
                        pickInteriorAircraft()
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
                        DebugLogger.i("MainActivity", "Auto-follow: no POIs — switching to furthest-west aircraft")
                        toast("No POIs — switching to furthest west")
                        autoFollowEmptyPoiCount = 0
                        pickFurthestWestAircraft()
                    } else {
                        autoFollowEmptyPoiCount = 0
                    }
                }
            }, 3000)
        }
        viewModel.mbtaTrains.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA trains update — ${vehicles.size} vehicles on map")
            clearTrainMarkers()
            vehicles.forEach { addTrainMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        viewModel.mbtaSubway.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA subway update — ${vehicles.size} vehicles on map")
            clearSubwayMarkers()
            vehicles.forEach { addSubwayMarker(it) }
            updateFollowedVehicle(vehicles)
        }
        viewModel.mbtaBuses.observe(this) { vehicles ->
            DebugLogger.i("MainActivity", "MBTA buses update — ${vehicles.size} vehicles on map")
            clearBusMarkers()
            vehicles.forEach { addBusMarker(it) }
            updateFollowedVehicle(vehicles)
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

    /** Ask the proxy for cached POIs within the visible map bounding box. */
    private fun loadCachedPoisForVisibleArea() {
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

    private fun addAircraftMarker(state: com.example.locationmapapp.data.model.AircraftState) {
        val altFt = state.baroAltitude?.let { it * 3.28084 }
        val tint = when {
            state.onGround           -> Color.parseColor("#78909C")  // gray
            altFt == null            -> Color.parseColor("#1565C0")  // blue default
            altFt < 5000             -> Color.parseColor("#2E7D32")  // green — low
            altFt < 20000            -> Color.parseColor("#1565C0")  // blue — mid
            else                     -> Color.parseColor("#6A1B9A")  // purple — high
        }
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
        binding.mapView.invalidate()
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
        return "$statusLine\n$speedLine\nUpdated: $updated"
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
    // VEHICLE FOLLOW MODE
    // =========================================================================

    private fun onVehicleMarkerTapped(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
        if (followedVehicleId == vehicle.id) {
            stopFollowing()
        } else {
            startFollowing(vehicle)
        }
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
        val text = "Following $typeLabel ${vehicle.label} — ${vehicle.routeName}\n" +
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
     * Compute a wide bbox (~zoom 11) centered on the map center, query aircraft in that area,
     * and follow a random high-altitude one. Does NOT change the user's zoom or position.
     */
    private fun pickAndFollowRandomAircraft(westboundOnly: Boolean = false) {
        // First check already-loaded aircraft
        val currentList = viewModel.aircraft.value ?: emptyList()
        val candidates = filterHighAltitude(currentList)
        if (candidates.isNotEmpty()) {
            selectAndFollow(candidates, westboundOnly)
            return
        }
        // Build a wide bbox (~zoom 8, covers most of the northeast) to maximize candidates
        val center = binding.mapView.mapCenter
        val halfLat = 3.0   // ~330 km north/south
        val halfLon = 4.0   // ~340 km east/west
        val south = center.latitude - halfLat
        val north = center.latitude + halfLat
        val west  = center.longitude - halfLon
        val east  = center.longitude + halfLon
        DebugLogger.i("MainActivity", "Auto-follow: wide bbox query ${south},${west},${north},${east}")
        viewModel.loadAircraft(south, west, north, east)
        // Wait for the network response then pick
        lifecycleScope.launch {
            delay(3000)
            val freshList = viewModel.aircraft.value ?: emptyList()
            val freshCandidates = filterHighAltitude(freshList)
            if (freshCandidates.isNotEmpty()) {
                selectAndFollow(freshCandidates, westboundOnly)
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

    private fun selectAndFollow(candidates: List<com.example.locationmapapp.data.model.AircraftState>, westboundOnly: Boolean = false) {
        // Exclude currently followed aircraft for variety
        val currentIcao = followedAircraftIcao
        val others = candidates.filter { it.icao24 != currentIcao }
        val pool = if (others.isNotEmpty()) others else candidates

        // Prioritize westbound aircraft (heading 180–360°) — they stay over land in New England
        val westbound = pool.filter { it.track != null && it.track >= 180.0 && it.track <= 360.0 }
        val pick = if (westbound.isNotEmpty()) {
            DebugLogger.i("MainActivity", "Auto-follow: ${westbound.size} westbound of ${pool.size} candidates${if (westboundOnly) " (forced)" else ""}")
            westbound.random()
        } else if (westboundOnly) {
            DebugLogger.i("MainActivity", "Auto-follow: no westbound available — will retry in 20 min")
            toast("No westbound aircraft — will retry")
            return
        } else {
            DebugLogger.i("MainActivity", "Auto-follow: no westbound — picking from all ${pool.size}")
            pool.random()
        }
        val hdg = pick.track?.let { "%.0f°".format(it) } ?: "?"
        DebugLogger.i("MainActivity", "Auto-follow: selected ${pick.icao24} (${pick.callsign}) " +
                "alt=${"%.0f".format((pick.baroAltitude ?: 0.0) * 3.28084)} ft hdg=$hdg from ${candidates.size} candidates")
        autoFollowEmptyPoiCount = 0
        followedAircraftFailCount = 0
        startFollowingAircraft(pick)
    }

    private fun pickFurthestWestAircraft() {
        val center = binding.mapView.mapCenter
        val halfLat = 3.0
        val halfLon = 4.0
        val south = center.latitude - halfLat
        val north = center.latitude + halfLat
        val west  = center.longitude - halfLon
        val east  = center.longitude + halfLon
        DebugLogger.i("MainActivity", "Auto-follow: wide bbox for furthest-west query")
        viewModel.loadAircraft(south, west, north, east)
        lifecycleScope.launch {
            delay(3000)
            val freshList = viewModel.aircraft.value ?: emptyList()
            val candidates = filterHighAltitude(freshList)
                .filter { it.icao24 != followedAircraftIcao && it.lon != null }
            if (candidates.isNotEmpty()) {
                val pick = candidates.minByOrNull { it.lon }!!
                val hdg = pick.track?.let { "%.0f°".format(it) } ?: "?"
                DebugLogger.i("MainActivity", "Auto-follow: furthest west ${pick.icao24} (${pick.callsign}) " +
                        "lon=${pick.lon} alt=${"%.0f".format((pick.baroAltitude ?: 0.0) * 3.28084)} ft hdg=$hdg")
                autoFollowEmptyPoiCount = 0
                followedAircraftFailCount = 0
                startFollowingAircraft(pick)
            } else {
                DebugLogger.i("MainActivity", "Auto-follow: no candidates for furthest-west — will retry in 20 min")
                toast("No aircraft available — will retry")
            }
        }
    }

    private fun pickInteriorAircraft() {
        // Query a wide US-centered bbox
        val south = 25.0; val north = 49.0; val west = -125.0; val east = -66.0
        DebugLogger.i("MainActivity", "Auto-follow: querying full CONUS for interior aircraft")
        viewModel.loadAircraft(south, west, north, east)
        lifecycleScope.launch {
            delay(3000)
            val freshList = viewModel.aircraft.value ?: emptyList()
            // Filter: high altitude, inside US bounds, exclude current
            val candidates = filterHighAltitude(freshList)
                .filter { it.icao24 != followedAircraftIcao && it.lat in 26.0..48.0 && it.lon in -124.0..-67.0 }
            if (candidates.isNotEmpty()) {
                // Pick the one closest to US center (~39°N, -98°W)
                val pick = candidates.minByOrNull { a ->
                    val dLat = a.lat - 39.0
                    val dLon = a.lon - (-98.0)
                    dLat * dLat + dLon * dLon
                }!!
                val hdg = pick.track?.let { "%.0f°".format(it) } ?: "?"
                DebugLogger.i("MainActivity", "Auto-follow: interior pick ${pick.icao24} (${pick.callsign}) " +
                        "at ${pick.lat},${pick.lon} alt=${"%.0f".format((pick.baroAltitude ?: 0.0) * 3.28084)} ft hdg=$hdg")
                autoFollowEmptyPoiCount = 0
                followedAircraftFailCount = 0
                startFollowingAircraft(pick)
            } else {
                DebugLogger.i("MainActivity", "Auto-follow: no interior candidates — falling back to random")
                pickAndFollowRandomAircraft()
            }
        }
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

        val prefix = if (autoFollowAircraftJob?.isActive == true) "Auto-following" else "Following"
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

        override fun onTrafficCamsToggled(enabled: Boolean) {
            DebugLogger.i("MainActivity", "onTrafficCamsToggled: $enabled")
            // STUB: overlay highway camera markers (MassDOT / 511 feed)
            stub("traffic_cams", enabled)
        }

        override fun onCamsMoreRequested() {
            DebugLogger.i("MainActivity", "onCamsMoreRequested")
            // STUB: open camera-source selection dialog / activity
            stub("cams_more")
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

        override fun onGpsModeToggled(autoGps: Boolean) {
            DebugLogger.i("MainActivity", "onGpsModeToggled: autoGps=$autoGps")
            toggleLocationMode()
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
}
