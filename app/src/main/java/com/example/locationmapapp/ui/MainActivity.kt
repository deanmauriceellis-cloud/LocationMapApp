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
    private val earthquakeMarkers = mutableListOf<Marker>()
    private val placeMarkers      = mutableListOf<Marker>()
    private val gasStationMarkers = mutableListOf<Marker>()
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

    // Vehicle follow mode
    private var followedVehicleId: String? = null
    private var followBanner: TextView? = null

    // Debounced cache-only POI loader on map scroll
    private var cachePoiJob: Job? = null

    // Deferred POI restore — wait for first real GPS fix before querying
    private var pendingPoiRestore = false

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
        val radarOn = prefs.getBoolean(AppBarMenuManager.PREF_RADAR_ON, false)
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
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_TRAINS, false) && trainRefreshJob?.isActive != true) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA trains")
            startTrainRefresh()
        }
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_SUBWAY, false) && subwayRefreshJob?.isActive != true) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA subway")
            startSubwayRefresh()
        }
        if (prefs.getBoolean(AppBarMenuManager.PREF_MBTA_BUSES, false) && busRefreshJob?.isActive != true) {
            DebugLogger.i("MainActivity", "onStart() restoring MBTA buses")
            startBusRefresh()
        }

        // Restore METAR
        if (prefs.getBoolean(AppBarMenuManager.PREF_METAR_DISPLAY, false)) {
            DebugLogger.i("MainActivity", "onStart() restoring METAR display")
            viewModel.loadAllUsMetars()
        }

        // Defer POI restore until first GPS fix so we query at the real location
        val needsRestaurants = prefs.getBoolean(AppBarMenuManager.PREF_POI_RESTAURANTS, false)
        val needsGas = prefs.getBoolean(AppBarMenuManager.PREF_POI_GAS, false)
        val needsEarthquakes = prefs.getBoolean(AppBarMenuManager.PREF_POI_EARTHQUAKES, false)
        if (needsRestaurants || needsGas || needsEarthquakes) {
            pendingPoiRestore = true
            DebugLogger.i("MainActivity", "onStart() POI restore deferred — waiting for GPS fix " +
                    "(restaurants=$needsRestaurants gas=$needsGas earthquakes=$needsEarthquakes)")
        }
    }

    override fun onStop()   { super.onStop();   radarScheduler.stop(); DebugLogger.i("MainActivity","onStop()") }
    override fun onResume() { super.onResume(); binding.mapView.onResume(); DebugLogger.i("MainActivity","onResume()") }
    override fun onPause()  { super.onPause();  binding.mapView.onPause();  DebugLogger.i("MainActivity","onPause()") }

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

        // Listen for zoom changes from pinch-to-zoom or programmatic changes
        map.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                // Debounce: load cached POIs 500ms after user stops scrolling
                cachePoiJob?.cancel()
                cachePoiJob = lifecycleScope.launch {
                    delay(500)
                    val center = binding.mapView.mapCenter as GeoPoint
                    viewModel.searchPoisFromCache(center)
                }
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                updateZoomBubble()
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

    // =========================================================================
    // FAB SPEED DIAL
    // =========================================================================

    private data class FabDef(val label: String, val iconRes: Int, val color: String, val action: () -> Unit)

    private fun buildFabSpeedDial() {
        DebugLogger.i("MainActivity", "buildFabSpeedDial() start")
        val defs = listOf(
            FabDef("Radar",        R.drawable.ic_radar,         "#0277BD") { toggleRadar() },
            FabDef("METAR",        R.drawable.ic_metar,         "#1B5E20") { viewModel.loadAllUsMetars(); toast("Loading METARs…") },
            FabDef("Earthquakes",  R.drawable.ic_earthquake,    "#B71C1C") { viewModel.loadEarthquakesForMap(); toast("Loading earthquakes…") },
            FabDef("Search Here",  R.drawable.ic_search,        "#4A148C") { searchFromCurrentLocation() },
            FabDef("Gas Stations", R.drawable.ic_gas_station,   "#E65100") { loadGasStations() },
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
            DebugLogger.i("MainActivity", "currentLocation → lat=${point.latitude} lon=${point.longitude} — centering map zoom=14")
            updateGpsMarker(point)
            // BUG FIX: zoom to 14 on first real GPS fix so user sees their neighborhood
            binding.mapView.controller.animateTo(point)
            binding.mapView.controller.setZoom(14.0)
            // Fire deferred POI queries now that we have a real GPS position
            if (pendingPoiRestore) {
                pendingPoiRestore = false
                val prefs = getSharedPreferences("app_bar_menu_prefs", MODE_PRIVATE)
                DebugLogger.i("MainActivity", "POI restore triggered at lat=${point.latitude} lon=${point.longitude}")
                if (prefs.getBoolean(AppBarMenuManager.PREF_POI_RESTAURANTS, false)) {
                    viewModel.searchPoisAt(point, listOf("amenity=restaurant"))
                }
                if (prefs.getBoolean(AppBarMenuManager.PREF_POI_GAS, false)) {
                    viewModel.loadGasStations(point)
                }
                if (prefs.getBoolean(AppBarMenuManager.PREF_POI_EARTHQUAKES, false)) {
                    viewModel.loadEarthquakesForMap()
                }
            }
        }
        viewModel.places.observe(this) { places ->
            DebugLogger.i("MainActivity", "places → ${places.size} results")
            clearPlaceMarkers(); places.forEach { addPlaceMarker(it) }
        }
        viewModel.gasStations.observe(this) { stations ->
            DebugLogger.i("MainActivity", "gasStations → ${stations.size} results")
            clearGasStationMarkers(); stations.forEach { addGasStationMarker(it) }
        }
        viewModel.metars.observe(this) { metars ->
            DebugLogger.i("MainActivity", "metars → ${metars.size} stations")
            clearMetarMarkers(); metars.forEach { addMetarMarker(it) }
        }
        viewModel.earthquakes.observe(this) { quakes ->
            DebugLogger.i("MainActivity", "earthquakes → ${quakes.size} events")
            clearEarthquakeMarkers(); quakes.forEach { addEarthquakeMarker(it) }
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

    fun loadGasStations() {
        val loc = viewModel.currentLocation.value ?: GeoPoint(42.3601, -71.0589)
        DebugLogger.i("MainActivity", "loadGasStations() lat=${loc.latitude} lon=${loc.longitude}")
        viewModel.loadGasStations(loc)
        toast("Loading gas stations…")
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

    private fun addPlaceMarker(place: com.example.locationmapapp.data.model.PlaceResult) {
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(place.lat, place.lon)
            icon     = MarkerIconHelper.dot(this@MainActivity, place.category)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = place.name
            snippet  = buildPlaceSnippet(place)
        }
        placeMarkers.add(m); binding.mapView.overlays.add(m); binding.mapView.invalidate()
    }

    private fun addGasStationMarker(place: com.example.locationmapapp.data.model.PlaceResult) {
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(place.lat, place.lon)
            icon     = MarkerIconHelper.dot(this@MainActivity, "gas_station")
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = place.name
            snippet  = place.address ?: "Gas Station"
        }
        gasStationMarkers.add(m); binding.mapView.overlays.add(m); binding.mapView.invalidate()
    }

    private fun addMetarMarker(station: com.example.locationmapapp.data.model.MetarStation) {
        val tint = when (station.flightCategory?.uppercase()) {
            "VFR"  -> Color.parseColor("#2E7D32")
            "MVFR" -> Color.parseColor("#1565C0")
            "IFR"  -> Color.parseColor("#C62828")
            "LIFR" -> Color.parseColor("#AD1457")
            else   -> Color.parseColor("#546E7A")
        }
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(station.lat, station.lon)
            icon     = MarkerIconHelper.get(this@MainActivity, R.drawable.ic_metar, 24, tint)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = "${station.stationId}  ${station.flightCategory ?: "?"}"
            snippet  = buildMetarSnippet(station)
        }
        metarMarkers.add(m); binding.mapView.overlays.add(m); binding.mapView.invalidate()
    }

    private fun addEarthquakeMarker(eq: com.example.locationmapapp.data.model.EarthquakeEvent) {
        val sizeDp = (20 + (eq.magnitude - 2.0).coerceIn(0.0, 6.0) * 2.7).toInt()
        val tint = when {
            eq.magnitude < 3.5 -> Color.parseColor("#558B2F")
            eq.magnitude < 5.0 -> Color.parseColor("#F57F17")
            eq.magnitude < 6.5 -> Color.parseColor("#BF360C")
            else               -> Color.parseColor("#880E4F")
        }
        val m = Marker(binding.mapView).apply {
            position = GeoPoint(eq.lat, eq.lon)
            icon     = MarkerIconHelper.get(this@MainActivity, R.drawable.ic_earthquake, sizeDp, tint)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title    = "M${eq.magnitude}  ${eq.place}"
            snippet  = "Depth: ${eq.depth}km"
        }
        earthquakeMarkers.add(m); binding.mapView.overlays.add(m); binding.mapView.invalidate()
    }

    private fun buildPlaceSnippet(p: com.example.locationmapapp.data.model.PlaceResult) =
        listOfNotNull(p.address, p.phone, p.openingHours).joinToString("\n").ifBlank { p.category }

    private fun buildMetarSnippet(s: com.example.locationmapapp.data.model.MetarStation): String {
        val temp = s.tempC?.let { "${it}°C" } ?: "?"
        val wind = if (s.windDirDeg != null && s.windSpeedKt != null)
            "${s.windDirDeg}° @ ${s.windSpeedKt}kt" else "calm"
        val vis  = s.visibilityMiles?.let { "${it}sm" } ?: "?"
        val alt  = s.altimeterInHg?.let { "%.2f inHg".format(it) } ?: "?"
        return "Temp:$temp  Wind:$wind  Vis:$vis  Alt:$alt\n${s.rawMetar.take(60)}"
    }

    fun clearMetarMarkers() {
        metarMarkers.forEach { binding.mapView.overlays.remove(it) }
        metarMarkers.clear(); binding.mapView.invalidate()
    }
    private fun clearEarthquakeMarkers() {
        earthquakeMarkers.forEach { binding.mapView.overlays.remove(it) }
        earthquakeMarkers.clear(); binding.mapView.invalidate()
    }
    private fun clearPlaceMarkers() {
        placeMarkers.forEach { binding.mapView.overlays.remove(it) }
        placeMarkers.clear(); binding.mapView.invalidate()
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
            icon     = MarkerIconHelper.get(this@MainActivity, R.drawable.ic_transit_rail, 30, tint)
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
            icon     = MarkerIconHelper.get(this@MainActivity, R.drawable.ic_transit_rail, 26, tint)
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
            icon     = MarkerIconHelper.get(this@MainActivity, R.drawable.ic_bus, 22, tint)
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
        followedVehicleId = vehicle.id
        DebugLogger.i("MainActivity", "Following vehicle ${vehicle.id} (${vehicle.label} — ${vehicle.routeName})")

        binding.mapView.controller.animateTo(vehicle.toGeoPoint())
        binding.mapView.controller.setZoom(16.0)

        showFollowBanner(vehicle)
    }

    private fun stopFollowing() {
        DebugLogger.i("MainActivity", "Stopped following vehicle $followedVehicleId")
        followedVehicleId = null
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

    private fun clearGasStationMarkers() {
        gasStationMarkers.forEach { binding.mapView.overlays.remove(it) }
        gasStationMarkers.clear(); binding.mapView.invalidate()
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
            if (enabled) { viewModel.loadAllUsMetars(); toast("Loading METARs…") }
            else          clearMetarMarkers()
        }

        override fun onMetarFrequencyChanged(minutes: Int) {
            DebugLogger.i("MainActivity", "onMetarFrequencyChanged: ${minutes}min")
            // STUB: wire to a periodic METAR refresh scheduler
            stub("metar_freq", minutes)
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
            val loc = viewModel.currentLocation.value ?: GeoPoint(42.3601, -71.0589)
            when (layerId) {
                PoiLayerId.RESTAURANTS   ->
                    if (enabled) { viewModel.searchPoisAt(loc, listOf("amenity=restaurant")); toast("Loading restaurants…") }
                    else          clearPlaceMarkers()

                PoiLayerId.GAS_STATIONS  ->
                    if (enabled) { viewModel.loadGasStations(loc); toast("Loading gas stations…") }
                    else          clearGasStationMarkers()

                PoiLayerId.EARTHQUAKES   ->
                    if (enabled) { viewModel.loadEarthquakesForMap(); toast("Loading earthquakes…") }
                    else          clearEarthquakeMarkers()

                PoiLayerId.TRANSIT_ACCESS ->
                    if (enabled) { viewModel.searchPoisAt(loc, listOf("public_transport=station", "railway=station", "amenity=bus_station")); toast("Loading transit access…") }
                    else          clearPlaceMarkers()

                PoiLayerId.CIVIC ->
                    if (enabled) { viewModel.searchPoisAt(loc, listOf("amenity=townhall", "amenity=courthouse", "amenity=post_office", "office=government")); toast("Loading civic buildings…") }
                    else          clearPlaceMarkers()

                PoiLayerId.PARKS ->
                    if (enabled) { viewModel.searchPoisAt(loc, listOf("leisure=park", "leisure=nature_reserve")); toast("Loading parks…") }
                    else          clearPlaceMarkers()

                else -> stub("poi_unknown:$layerId", enabled)
            }
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
