/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.R
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.views.overlay.Marker
import android.content.Context

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityTransit.kt"

// =========================================================================
// MBTA TRAINS
// =========================================================================

internal fun MainActivity.addTrainMarker(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
    val tint = vehicleRouteColor(vehicle)
    val labeled = binding.mapView.zoomLevelDouble >= 18.0
    val isStale = vehicleStalenessTag(vehicle.updatedAt).isNotEmpty()
    val m = Marker(binding.mapView).apply {
        position = vehicle.toGeoPoint()
        if (labeled) {
            val (icon, anchorY) = MarkerIconHelper.labeledVehicle(
                this@addTrainMarker, R.drawable.ic_transit_rail, 30, tint, vehicle.bearing,
                vehicle.routeName, vehicle.headsign, vehicle.stopName,
                vehicle.currentStatus.display, vehicle.speedDisplay, isStale,
                vehicle.nextStopMinutes
            )
            this.icon = icon
            setAnchor(Marker.ANCHOR_CENTER, anchorY)
        } else {
            icon = MarkerIconHelper.withArrow(this@addTrainMarker, R.drawable.ic_transit_rail, 30, tint, vehicle.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
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

internal fun MainActivity.buildTrainSnippet(v: com.example.locationmapapp.data.model.MbtaVehicle): String {
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
internal fun MainActivity.vehicleStalenessTag(isoTimestamp: String): String {
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

internal fun MainActivity.clearTrainMarkers() {
    trainMarkers.forEach { binding.mapView.overlays.remove(it) }
    trainMarkers.clear()
    binding.mapView.invalidate()
}

/**
 * Start auto-refreshing train positions at trainRefreshIntervalSec cadence.
 * Cancels any existing job first so frequency changes take effect immediately.
 */
internal fun MainActivity.startTrainRefresh() {
    trainRefreshJob?.cancel()
    DebugLogger.i("MainActivity", "Starting MBTA train refresh every ${trainRefreshIntervalSec}s")
    trainRefreshJob = lifecycleScope.launch {
        while (true) {
            transitViewModel.fetchMbtaTrains()
            delay(trainRefreshIntervalSec * 1000L)
        }
    }
}

internal fun MainActivity.stopTrainRefresh() {
    trainRefreshJob?.cancel()
    trainRefreshJob = null
    DebugLogger.i("MainActivity", "MBTA train refresh stopped")
}

// =========================================================================
// MBTA SUBWAY
// =========================================================================

internal fun MainActivity.addSubwayMarker(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
    val tint = vehicleRouteColor(vehicle)
    val labeled = binding.mapView.zoomLevelDouble >= 18.0
    val isStale = vehicleStalenessTag(vehicle.updatedAt).isNotEmpty()
    val m = Marker(binding.mapView).apply {
        position = vehicle.toGeoPoint()
        if (labeled) {
            val (icon, anchorY) = MarkerIconHelper.labeledVehicle(
                this@addSubwayMarker, R.drawable.ic_transit_rail, 26, tint, vehicle.bearing,
                vehicle.routeName, vehicle.headsign, vehicle.stopName,
                vehicle.currentStatus.display, vehicle.speedDisplay, isStale
            )
            this.icon = icon
            setAnchor(Marker.ANCHOR_CENTER, anchorY)
        } else {
            icon = MarkerIconHelper.withArrow(this@addSubwayMarker, R.drawable.ic_transit_rail, 26, tint, vehicle.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
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

internal fun MainActivity.clearSubwayMarkers() {
    subwayMarkers.forEach { binding.mapView.overlays.remove(it) }
    subwayMarkers.clear()
    binding.mapView.invalidate()
}

internal fun MainActivity.startSubwayRefresh() {
    subwayRefreshJob?.cancel()
    DebugLogger.i("MainActivity", "Starting MBTA subway refresh every ${subwayRefreshIntervalSec}s")
    subwayRefreshJob = lifecycleScope.launch {
        while (true) {
            transitViewModel.fetchMbtaSubway()
            delay(subwayRefreshIntervalSec * 1000L)
        }
    }
}

internal fun MainActivity.stopSubwayRefresh() {
    subwayRefreshJob?.cancel()
    subwayRefreshJob = null
    DebugLogger.i("MainActivity", "MBTA subway refresh stopped")
}

// =========================================================================
// MBTA BUSES
// =========================================================================

internal fun MainActivity.addBusMarker(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
    val tint = vehicleRouteColor(vehicle)
    val labeled = binding.mapView.zoomLevelDouble >= 18.0
    val isStale = vehicleStalenessTag(vehicle.updatedAt).isNotEmpty()
    val m = Marker(binding.mapView).apply {
        position = vehicle.toGeoPoint()
        if (labeled) {
            val (icon, anchorY) = MarkerIconHelper.labeledVehicle(
                this@addBusMarker, R.drawable.ic_bus, 22, tint, vehicle.bearing,
                vehicle.routeName, vehicle.headsign, vehicle.stopName,
                vehicle.currentStatus.display, vehicle.speedDisplay, isStale
            )
            this.icon = icon
            setAnchor(Marker.ANCHOR_CENTER, anchorY)
        } else {
            icon = MarkerIconHelper.withArrow(this@addBusMarker, R.drawable.ic_bus, 22, tint, vehicle.bearing)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
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

internal fun MainActivity.clearBusMarkers() {
    busMarkers.forEach { binding.mapView.overlays.remove(it) }
    busMarkers.clear()
    binding.mapView.invalidate()
}

internal fun MainActivity.startBusRefresh() {
    busRefreshJob?.cancel()
    DebugLogger.i("MainActivity", "Starting MBTA bus refresh every ${busRefreshIntervalSec}s")
    busRefreshJob = lifecycleScope.launch {
        while (true) {
            transitViewModel.fetchMbtaBuses()
            delay(busRefreshIntervalSec * 1000L)
        }
    }
}

internal fun MainActivity.stopBusRefresh() {
    busRefreshJob?.cancel()
    busRefreshJob = null
    DebugLogger.i("MainActivity", "MBTA bus refresh stopped")
}

// =========================================================================
// MBTA TRAIN STATIONS
// =========================================================================

/** Get the MBTA line color for a route ID. */
internal fun MainActivity.routeColor(routeId: String): Int = when {
    routeId == "Red" || routeId == "Mattapan" -> Color.parseColor("#C62828")
    routeId == "Orange"                       -> Color.parseColor("#E65100")
    routeId == "Blue"                         -> Color.parseColor("#1565C0")
    routeId.startsWith("Green")               -> Color.parseColor("#2E7D32")
    routeId == "CR" || routeId.startsWith("CR-") -> Color.parseColor("#6A1B9A")
    routeId == "Silver"                       -> Color.parseColor("#546E7A")
    else                                      -> Color.parseColor("#37474F")
}

/** Abbreviated route label for arrival board. */
internal fun MainActivity.routeAbbrev(routeId: String): String = when {
    routeId == "Red"                          -> "RL"
    routeId == "Orange"                       -> "OL"
    routeId == "Blue"                         -> "BL"
    routeId == "Mattapan"                     -> "M"
    routeId.startsWith("Green-")              -> "GL-${routeId.removePrefix("Green-")}"
    routeId == "CR" || routeId.startsWith("CR-") -> "CR"
    routeId == "Silver"                       -> "SL"
    else                                      -> routeId.take(4)
}

internal fun MainActivity.addStationMarker(stop: com.example.locationmapapp.data.model.MbtaStop) {
    val tint = if (stop.routeIds.size > 1) {
        Color.parseColor("#37474F")  // neutral dark gray for multi-line stations
    } else {
        routeColor(stop.routeIds.firstOrNull() ?: "")
    }
    val m = Marker(binding.mapView).apply {
        position = stop.toGeoPoint()
        icon     = MarkerIconHelper.stationIcon(this@addStationMarker, tint)
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

internal fun MainActivity.clearStationMarkers() {
    stationMarkers.forEach { binding.mapView.overlays.remove(it) }
    stationMarkers.clear()
    binding.mapView.invalidate()
}

// =========================================================================
// MBTA BUS STOPS (viewport-filtered)
// =========================================================================

/** Debounced: refresh bus stop markers 300ms after scroll/zoom. */
internal fun MainActivity.scheduleBusStopReload() {
    if (allBusStops.isEmpty()) return
    busStopReloadJob?.cancel()
    busStopReloadJob = lifecycleScope.launch {
        delay(300)
        refreshBusStopMarkersForViewport()
    }
}

/** Show bus stop markers only at zoom >= 15, filtered by visible bounding box. */
internal fun MainActivity.refreshBusStopMarkersForViewport() {
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

internal fun MainActivity.addBusStopMarker(stop: com.example.locationmapapp.data.model.MbtaStop) {
    val tint = Color.parseColor("#00695C")  // Teal
    val m = Marker(binding.mapView).apply {
        position = stop.toGeoPoint()
        icon     = MarkerIconHelper.busStopIcon(this@addBusStopMarker, tint)
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

internal fun MainActivity.clearBusStopMarkers() {
    busStopMarkers.forEach { binding.mapView.overlays.remove(it) }
    busStopMarkers.clear()
}

// ── Arrival Board Dialog ─────────────────────────────────────────────────

internal var arrivalRefreshJob: Job? = null

@SuppressLint("SetTextI18n")
internal fun MainActivity.showArrivalBoardDialog(stop: com.example.locationmapapp.data.model.MbtaStop) {
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
        addView(TextView(this@showArrivalBoardDialog).apply {
            text = "Line"; textSize = 12f; setTextColor(Color.parseColor("#999999"))
            layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        addView(TextView(this@showArrivalBoardDialog).apply {
            text = "Destination"; textSize = 12f; setTextColor(Color.parseColor("#999999"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(this@showArrivalBoardDialog).apply {
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
            val predictions = transitViewModel.fetchPredictionsDirectly(stop.id)
            if (!dialog.isShowing) return@launch
            listContainer.removeAllViews()
            // Update lines subtitle from actual prediction data
            val linesList = predictions.map { it.routeId }.distinct()
                .joinToString(", ") { routeAbbrev(it) }
            if (linesList.isNotEmpty()) linesText.text = "Lines: $linesList"

            if (predictions.isEmpty()) {
                listContainer.addView(TextView(this@showArrivalBoardDialog).apply {
                    text = "No upcoming arrivals"
                    textSize = 14f
                    setTextColor(Color.parseColor("#999999"))
                    setPadding(dp(0), dp(24), dp(0), dp(24))
                    gravity = android.view.Gravity.CENTER
                })
            } else {
                predictions.forEach { pred ->
                    val row = LinearLayout(this@showArrivalBoardDialog).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, dp(8), 0, dp(8))
                    }

                    // Route dot + abbreviation
                    val routeLabel = LinearLayout(this@showArrivalBoardDialog).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
                    }
                    val dotView = View(this@showArrivalBoardDialog).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                            setMargins(0, 0, dp(4), 0)
                        }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(routeColor(pred.routeId))
                        }
                    }
                    routeLabel.addView(dotView)
                    routeLabel.addView(TextView(this@showArrivalBoardDialog).apply {
                        text = routeAbbrev(pred.routeId)
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    row.addView(routeLabel)

                    // Headsign / destination
                    row.addView(TextView(this@showArrivalBoardDialog).apply {
                        text = pred.headsign ?: pred.routeName
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    // Arrival time
                    row.addView(TextView(this@showArrivalBoardDialog).apply {
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
                    listContainer.addView(View(this@showArrivalBoardDialog).apply {
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
internal fun MainActivity.formatArrivalTime(isoTime: String?): String {
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
internal fun MainActivity.showTripScheduleDialog(pred: com.example.locationmapapp.data.model.MbtaPrediction) {
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
        val entries = transitViewModel.fetchTripScheduleDirectly(tripId)
        if (!dialog.isShowing) return@launch
        listContainer.removeAllViews()
        if (entries.isEmpty()) {
            listContainer.addView(TextView(this@showTripScheduleDialog).apply {
                text = "No schedule data available"
                textSize = 14f
                setTextColor(Color.parseColor("#999999"))
                setPadding(0, dp(24), 0, dp(24))
                gravity = android.view.Gravity.CENTER
            })
        } else {
            val lineColor = routeColor(pred.routeId)
            entries.forEach { entry ->
                val row = LinearLayout(this@showTripScheduleDialog).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }

                // Colored dot
                row.addView(View(this@showTripScheduleDialog).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        setMargins(0, 0, dp(8), 0)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(lineColor)
                    }
                })

                // Stop name
                row.addView(TextView(this@showTripScheduleDialog).apply {
                    text = entry.stopName
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                // Time (12h format)
                val timeStr = formatScheduleTime(entry.arrivalTime ?: entry.departureTime)
                row.addView(TextView(this@showTripScheduleDialog).apply {
                    text = timeStr
                    textSize = 13f
                    setTextColor(Color.parseColor("#B0BEC5"))
                    layoutParams = LinearLayout.LayoutParams(dp(75), LinearLayout.LayoutParams.WRAP_CONTENT)
                    gravity = android.view.Gravity.END
                })

                // Track number (commuter rail)
                if (entry.platformCode != null) {
                    row.addView(TextView(this@showTripScheduleDialog).apply {
                        text = "Trk ${entry.platformCode}"
                        textSize = 12f
                        setTextColor(Color.parseColor("#78909C"))
                        layoutParams = LinearLayout.LayoutParams(dp(45), LinearLayout.LayoutParams.WRAP_CONTENT)
                        gravity = android.view.Gravity.END
                    })
                }

                listContainer.addView(row)
                // Divider
                listContainer.addView(View(this@showTripScheduleDialog).apply {
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
internal fun MainActivity.formatScheduleTime(isoTime: String?): String {
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

internal fun MainActivity.onVehicleMarkerTapped(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
    // Stop populate scanner, idle scanner, and silent fill — user is interacting with an object
    if (populateJob != null) stopPopulatePois()
    stopIdlePopulate()
    stopSilentFill()
    showVehicleDetailDialog(vehicle)
}

// ── Vehicle Detail Dialog ────────────────────────────────────────────────

@SuppressLint("SetTextI18n")
internal fun MainActivity.showVehicleDetailDialog(vehicle: com.example.locationmapapp.data.model.MbtaVehicle) {
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

