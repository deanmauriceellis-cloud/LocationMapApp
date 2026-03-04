/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.R
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import android.content.Context
import android.graphics.Paint
import android.view.View
import com.example.locationmapapp.ui.menu.AppBarMenuManager
import kotlinx.coroutines.Job
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityAircraft.kt"

// =========================================================================
// AIRCRAFT
// =========================================================================

/** Altitude → color for aircraft markers and flight trail segments. */
internal fun MainActivity.altitudeColor(altitudeMeters: Double?, onGround: Boolean = false): Int {
    val altFt = altitudeMeters?.let { it * 3.28084 }
    return when {
        onGround              -> Color.parseColor("#78909C")  // gray
        altFt == null         -> Color.parseColor("#1565C0")  // blue default
        altFt < 5000          -> Color.parseColor("#2E7D32")  // green — low
        altFt < 20000         -> Color.parseColor("#1565C0")  // blue — mid
        else                  -> Color.parseColor("#6A1B9A")  // purple — high
    }
}

internal fun MainActivity.addAircraftMarker(state: com.example.locationmapapp.data.model.AircraftState) {
    val tint = altitudeColor(state.baroAltitude, state.onGround)
    val heading = state.track?.toInt() ?: 0
    val m = Marker(binding.mapView).apply {
        position = state.toGeoPoint()
        icon     = MarkerIconHelper.aircraftMarker(
            this@addAircraftMarker, heading, tint,
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

internal fun MainActivity.buildAircraftSnippet(s: com.example.locationmapapp.data.model.AircraftState): String {
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

internal fun MainActivity.aircraftCategoryName(cat: Int): String = when (cat) {
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

internal fun MainActivity.clearAircraftMarkers() {
    aircraftMarkers.forEach { binding.mapView.overlays.remove(it) }
    aircraftMarkers.clear()
    binding.mapView.invalidate()
}

// ── Flight trail ─────────────────────────────────────────────────────────

/** Rebuild all trail polylines from flightTrailPoints. Skips gaps >30min. Cap: 1000 points. */
internal fun MainActivity.redrawFlightTrail() {
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
internal fun MainActivity.appendToFlightTrail(state: com.example.locationmapapp.data.model.AircraftState) {
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
internal fun MainActivity.clearFlightTrail() {
    flightTrailOverlays.forEach { binding.mapView.overlays.remove(it) }
    flightTrailOverlays.clear()
    flightTrailPoints.clear()
    binding.mapView.invalidate()
}

/** Load DB history for an aircraft and draw initial trail. */
internal fun MainActivity.loadFlightTrailHistory(icao24: String, currentState: com.example.locationmapapp.data.model.AircraftState? = null) {
    lifecycleScope.launch {
        val history = aircraftViewModel.fetchFlightHistoryDirectly(icao24)
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

internal fun MainActivity.startAircraftRefresh() {
    aircraftRefreshJob?.cancel()
    DebugLogger.i("MainActivity", "Starting aircraft refresh every ${aircraftRefreshIntervalSec}s")
    aircraftRefreshJob = lifecycleScope.launch {
        while (true) {
            loadAircraftForVisibleArea()
            delay(aircraftRefreshIntervalSec * 1000L)
        }
    }
}

internal fun MainActivity.stopAircraftRefresh() {
    aircraftRefreshJob?.cancel()
    aircraftRefreshJob = null
    DebugLogger.i("MainActivity", "Aircraft refresh stopped")
}

/** Get the route color for a vehicle (handles buses with numeric route IDs). */
internal fun MainActivity.vehicleRouteColor(vehicle: com.example.locationmapapp.data.model.MbtaVehicle): Int {
    // Buses — use teal for all bus routes
    if (vehicle.routeType == 3) return Color.parseColor("#00695C")
    // Rail/subway — use standard routeColor
    return routeColor(vehicle.routeId)
}

internal fun MainActivity.startFollowing(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
    followedAircraftIcao = null   // stop any aircraft follow
    followedVehicleId = vehicle.id
    DebugLogger.i("MainActivity", "Following vehicle ${vehicle.id} (${vehicle.label} — ${vehicle.routeName})")

    binding.mapView.controller.animateTo(vehicle.toGeoPoint())
    binding.mapView.controller.setZoom(16.0)

    showFollowBanner(vehicle)
}

internal fun MainActivity.stopFollowing() {
    DebugLogger.i("MainActivity", "Stopped following vehicle=$followedVehicleId aircraft=$followedAircraftIcao")
    clearFlightTrail()
    followedVehicleId = null
    followedAircraftIcao = null
    followedAircraftFailCount = 0
    autoFollowEmptyPoiCount = 0
    stopFollowedAircraftRefresh()
    hideFollowBanner()
}

internal fun MainActivity.updateFollowedVehicle(vehicles: List<com.example.locationmapapp.data.model.MbtaVehicle>) {
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

internal fun MainActivity.showFollowBanner(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
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
    val text = "Following $typeLabel ${vehicle.label} — ${vehicle.routeName}$staleTag | $statusText • ${vehicle.speedDisplay}"
    statusLineManager.set(StatusLineManager.Priority.VEHICLE_FOLLOW, text) { stopFollowing() }
}

internal fun MainActivity.hideFollowBanner() {
    statusLineManager.clear(StatusLineManager.Priority.VEHICLE_FOLLOW)
    statusLineManager.clear(StatusLineManager.Priority.AIRCRAFT_FOLLOW)
}

// ── Aircraft follow ─────────────────────────────────────────────────────

internal fun MainActivity.onAircraftMarkerTapped(state: com.example.locationmapapp.data.model.AircraftState) {
    // Stop populate scanner, idle scanner, and silent fill — user is interacting with an object
    if (populateJob != null) stopPopulatePois()
    stopIdlePopulate()
    stopSilentFill()
    if (followedAircraftIcao == state.icao24) {
        stopFollowing()
    } else {
        startFollowingAircraft(state)
    }
}

internal fun MainActivity.startFollowingAircraft(state: com.example.locationmapapp.data.model.AircraftState) {
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

internal fun MainActivity.startFollowedAircraftRefresh(icao24: String) {
    followedAircraftRefreshJob?.cancel()
    DebugLogger.i("MainActivity", "Starting followed aircraft refresh for $icao24 every ${aircraftRefreshIntervalSec}s")
    followedAircraftRefreshJob = lifecycleScope.launch {
        while (true) {
            delay(aircraftRefreshIntervalSec * 1000L)
            aircraftViewModel.loadFollowedAircraft(icao24)
        }
    }
}

internal fun MainActivity.stopFollowedAircraftRefresh() {
    followedAircraftRefreshJob?.cancel()
    followedAircraftRefreshJob = null
    aircraftViewModel.clearFollowedAircraft()
    lastFollowedAircraftState = null
}

// ── Auto-follow random high-altitude aircraft ────────────────────────

internal fun MainActivity.startAutoFollowAircraft() {
    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
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

internal fun MainActivity.stopAutoFollowAircraft() {
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
internal fun MainActivity.pickAndFollowRandomAircraft() {
    val dirLabel = "${if (autoFollowPreferWest) "W" else "E"}${if (autoFollowPreferSouth) "S" else "N"}"
    // First check already-loaded aircraft
    val currentList = aircraftViewModel.aircraft.value ?: emptyList()
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
    aircraftViewModel.loadAircraft(south, west, north, east)
    // Wait for the network response then pick
    lifecycleScope.launch {
        delay(3000)
        val freshList = aircraftViewModel.aircraft.value ?: emptyList()
        val freshCandidates = filterHighAltitude(freshList)
        if (freshCandidates.isNotEmpty()) {
            selectAndFollow(freshCandidates)
        } else {
            DebugLogger.i("MainActivity", "Auto-follow: no aircraft ≥ 10,000 ft in wide bbox — will retry in 20 min")
            toast("No aircraft above 10,000 ft — will retry")
        }
    }
}

internal fun MainActivity.filterHighAltitude(
    aircraft: List<com.example.locationmapapp.data.model.AircraftState>
): List<com.example.locationmapapp.data.model.AircraftState> {
    return aircraft.filter { it.baroAltitude != null && it.baroAltitude * 3.28084 >= 10000 }
}

internal fun MainActivity.selectAndFollow(candidates: List<com.example.locationmapapp.data.model.AircraftState>) {
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


internal fun MainActivity.updateFollowedAircraft(aircraft: List<com.example.locationmapapp.data.model.AircraftState>) {
    val icao = followedAircraftIcao ?: return
    // If the followed aircraft is in the bbox results, update its last known state
    val state = aircraft.find { it.icao24 == icao }
    if (state != null) {
        lastFollowedAircraftState = state
        showAircraftFollowBanner(state)
    }
    // Don't stop following if missing from bbox — the dedicated icao24 query handles that
}

internal fun MainActivity.showAircraftFollowBanner(state: com.example.locationmapapp.data.model.AircraftState) {
    val label = state.callsign?.ifBlank { null } ?: state.icao24
    val altFt = state.baroAltitude?.let { "%.0f ft".format(it * 3.28084) } ?: "—"
    val speedKt = state.velocity?.let { "%.0f kt".format(it * 1.94384) } ?: "—"
    val vertDesc = state.verticalRate?.let {
        val fpm = it * 196.85
        when {
            fpm > 100  -> "\u2191%.0f fpm".format(fpm)
            fpm < -100 -> "\u2193%.0f fpm".format(fpm)
            else       -> "level"
        }
    } ?: ""
    val headingStr = state.track?.let { "%.0f\u00B0".format(it) } ?: ""
    val spiFlag = if (state.spi) " \u26A0SPI" else ""

    val dirLabel = if (autoFollowAircraftJob?.isActive == true) {
        val ew = if (autoFollowPreferWest) "W" else "E"
        val ns = if (autoFollowPreferSouth) "S" else "N"
        " [$ew$ns]"
    } else ""
    val prefix = if (autoFollowAircraftJob?.isActive == true) "Auto$dirLabel" else "Following"
    val text = "$prefix \u2708 $label$spiFlag | $altFt • $speedKt • $headingStr • $vertDesc"
    statusLineManager.set(StatusLineManager.Priority.AIRCRAFT_FOLLOW, text) { stopFollowing() }
}

