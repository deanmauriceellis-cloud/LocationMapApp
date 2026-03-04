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
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.R
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import android.content.Context
import android.os.Handler
import android.os.Looper

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityPopulate.kt"

/** Mutable state for idle populate — persists across stop/resume cycles. */
internal class IdlePopulateState(
    val centerLat: Double,
    val centerLon: Double,
    var settledRadius: Int = 0,
    var stepLat: Double = 0.0,
    var stepLon: Double = 0.0,
    var ring: Int = 0,
    var pointIdx: Int = 0,
    val stats: PopulateStats = PopulateStats(),
    var calibrated: Boolean = false,
    var consecutiveErrors: Int = 0
)



// =========================================================================
// SILENT BACKGROUND POI FILL
// =========================================================================

/**
 * Starts a single background Overpass search at [center] to fill POI cache
 * for the current viewport. Cancels any previous silent fill in progress.
 * Shows a debug banner if PREF_SILENT_FILL_DEBUG is enabled.
 */
internal fun MainActivity.startSilentFill(center: org.osmdroid.util.GeoPoint) {
    silentFillJob?.cancel()
    // Don't run if full populate scanner is active
    if (populateJob != null) return
    // Don't run if following something (follow has its own POI prefetch)
    if (followedVehicleId != null || followedAircraftIcao != null) return

    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
    val showDebug = prefs.getBoolean(MenuPrefs.PREF_SILENT_FILL_DEBUG, true)

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
internal fun MainActivity.scheduleSilentFill(center: org.osmdroid.util.GeoPoint, delayMs: Long) {
    silentFillRunnable?.let { binding.mapView.removeCallbacks(it) }
    val runnable = Runnable { startSilentFill(center) }
    silentFillRunnable = runnable
    binding.mapView.postDelayed(runnable, delayMs)
}

internal fun MainActivity.stopSilentFill() {
    silentFillRunnable?.let { binding.mapView.removeCallbacks(it) }
    silentFillRunnable = null
    silentFillJob?.cancel()
    silentFillJob = null
    hideSilentFillBanner()
}

internal fun MainActivity.showSilentFillBanner(text: String) {
    runOnUiThread {
        statusLineManager.set(StatusLineManager.Priority.SILENT_FILL, "\uD83D\uDD0D $text") { stopSilentFill() }
    }
}

internal fun MainActivity.hideSilentFillBanner() {
    runOnUiThread { statusLineManager.clear(StatusLineManager.Priority.SILENT_FILL) }
}

// =========================================================================
// IDLE AUTO-POPULATE — full scanner triggered after 5 min of GPS stationarity
// =========================================================================

internal fun MainActivity.startIdlePopulate(center: GeoPoint) {
    if (idlePopulateJob?.isActive == true) return
    // Cancel silent fill — idle scanner takes over
    stopSilentFill()

    val centerLat = center.latitude
    val centerLon = center.longitude

    // Check for resumable saved state — within 200m of the same center
    val saved = idlePopulateState
    if (saved != null && saved.calibrated
        && distanceBetween(GeoPoint(saved.centerLat, saved.centerLon), center) < 200f) {
        DebugLogger.i("MainActivity", "startIdlePopulate() RESUMING from R${saved.ring} pt${saved.pointIdx} (${saved.stats.cells} cells done)")
        resumeIdlePopulate(saved)
        return
    }
    idlePopulateState = null  // discard stale state

    DebugLogger.i("MainActivity", "startIdlePopulate() FRESH center=$centerLat,$centerLon")

    val state = IdlePopulateState(centerLat, centerLon)
    idlePopulateState = state

    idlePopulateJob = lifecycleScope.launch {
        // ── Phase 1: Probe center to discover settled radius ──
        val probePoint = GeoPoint(centerLat, centerLon)
        placeScanningMarker(probePoint, panMap = false)
        state.stats.status = "Probing center to calibrate grid…"
        showIdlePopulateBanner(0, state.stats, 0)

        var probeResult: com.example.locationmapapp.data.model.PopulateSearchResult? = null
        for (probeAttempt in 1..3) {
            probeResult = viewModel.populateSearchAt(probePoint)
            if (probeResult != null) break
            DebugLogger.w("MainActivity", "Idle probe attempt $probeAttempt failed — retrying in 45s")
            state.stats.status = "Probe attempt $probeAttempt failed — retrying…"
            for (sec in 45 downTo 1) {
                showIdlePopulateBanner(0, state.stats, sec)
                delay(1_000L)
            }
        }
        if (probeResult == null) {
            DebugLogger.w("MainActivity", "Idle probe failed after 3 attempts")
            idlePopulateState = null  // can't resume without calibration
            stopIdlePopulate()
            return@launch
        }

        state.settledRadius = probeResult.radiusM
        state.stats.pois += probeResult.results.size
        state.stats.newPois += probeResult.poiNew
        state.stats.knownPois += probeResult.poiKnown
        state.stats.searches++
        state.stats.cells++
        loadCachedPoisForVisibleArea()

        // ── Phase 2: Calculate grid from settled radius ──
        state.stepLat = 0.8 * 2 * state.settledRadius.toDouble() / 111320.0
        state.stepLon = state.stepLat / Math.cos(Math.toRadians(centerLat))
        state.calibrated = true
        DebugLogger.i("MainActivity",
            "Idle populate grid calibrated: settledRadius=${state.settledRadius}m, stepLat=${"%.5f".format(state.stepLat)}°, stepLon=${"%.5f".format(state.stepLon)}°")
        state.stats.gridRadius = state.settledRadius
        state.stats.currentRadius = state.settledRadius
        state.stats.status = "Grid calibrated at ${state.settledRadius}m — ${probeResult.results.size} POIs (${probeResult.poiNew} new)"

        // Countdown after probe (45s — gentler than manual scanner)
        for (sec in 45 downTo 1) {
            showIdlePopulateBanner(0, state.stats, sec)
            delay(1_000L)
        }

        // ── Phase 3: Spiral outward using calibrated grid ──
        runIdlePopulateSpiral(state)
    }
}

/** Resume idle populate from saved state — skips probe/calibration, jumps straight to spiral. */
internal fun MainActivity.resumeIdlePopulate(state: IdlePopulateState) {
    idlePopulateState = state
    state.consecutiveErrors = 0  // reset error counter for fresh run

    idlePopulateJob = lifecycleScope.launch {
        state.stats.status = "Resuming from R${state.ring} pt${state.pointIdx}…"
        showIdlePopulateBanner(state.ring, state.stats, 0)

        // Brief countdown before resuming (15s)
        for (sec in 15 downTo 1) {
            showIdlePopulateBanner(state.ring, state.stats, sec)
            delay(1_000L)
        }

        runIdlePopulateSpiral(state)
    }
}

/** Phase 3 spiral loop — shared between fresh start and resume. */
internal suspend fun MainActivity.runIdlePopulateSpiral(state: IdlePopulateState) {
    val startRing = state.ring.coerceAtLeast(1)
    val startPointIdx = state.pointIdx

    for (ring in startRing..15) {
        val points = generateRingPoints(ring, state.centerLat, state.centerLon, state.stepLat, state.stepLon)
        val firstIdx = if (ring == startRing) startPointIdx else 0
        var pointIdx = firstIdx
        while (pointIdx < points.size) {
            kotlinx.coroutines.yield()
            state.ring = ring
            state.pointIdx = pointIdx

            val (gridLat, gridLon) = points[pointIdx]
            val point = GeoPoint(gridLat, gridLon)

            state.stats.depth = 0
            state.stats.currentRadius = state.settledRadius
            state.stats.status = "Searching cell ${pointIdx + 1}/${points.size} at ${state.settledRadius}m…"
            placeScanningMarker(point, panMap = false)
            showIdlePopulateBanner(ring, state.stats, 0)

            val result = viewModel.populateSearchAt(point)

            if (result != null) {
                state.consecutiveErrors = 0
                state.stats.pois += result.results.size
                state.stats.newPois += result.poiNew
                state.stats.knownPois += result.poiKnown
                state.stats.searches++
                state.stats.cells++
                state.stats.currentRadius = result.radiusM
                pointIdx++
                state.pointIdx = pointIdx
                loadCachedPoisForVisibleArea()

                val newStr = if (result.poiNew > 0) "${result.poiNew} new" else "all known"
                state.stats.status = "Found ${result.results.size} POIs ($newStr) at ${result.radiusM}m"

                // Recursive subdivision if settled smaller than grid radius
                if (result.radiusM < state.settledRadius) {
                    state.stats.status = "Dense area! ${state.settledRadius}m→${result.radiusM}m — filling 8 gaps"
                    showIdlePopulateBanner(ring, state.stats, 0)
                    searchCellSubdivisions(point, state.settledRadius, result.radiusM, 0, ring, state.stats)
                    state.stats.depth = 0
                    state.stats.currentRadius = state.settledRadius
                    state.stats.status = "Subdivision done — back to main grid"
                }
            } else {
                state.stats.fails++
                state.stats.status = "Search failed — retrying…"
                state.consecutiveErrors++
                if (state.consecutiveErrors >= 5) {
                    DebugLogger.w("MainActivity", "Idle populate auto-stopped after 5 consecutive errors")
                    break
                }
            }

            // Countdown 45s between main grid cells (gentler than manual 30s)
            for (sec in 45 downTo 1) {
                showIdlePopulateBanner(ring, state.stats, sec)
                delay(1_000L)
            }
        }
        if (state.consecutiveErrors >= 5) break
    }

    DebugLogger.i("MainActivity",
        "Idle populate complete: ${state.stats.cells} cells, ${state.stats.pois} POIs, ${state.stats.searches} searches, ${state.stats.fails} fail, ${state.stats.subdivs} subdivisions")
    idlePopulateState = null  // completed — no need to resume
    stopIdlePopulate()
}

internal fun MainActivity.stopIdlePopulate(clearState: Boolean = false) {
    idlePopulateJob?.cancel()
    idlePopulateJob = null
    if (clearState) idlePopulateState = null
    removeScanningMarker()
    statusLineManager.clear(StatusLineManager.Priority.IDLE_POPULATE)
    val resumeInfo = idlePopulateState?.let { s ->
        if (s.calibrated) " (saved R${s.ring} pt${s.pointIdx}, ${s.stats.cells} cells)" else ""
    } ?: ""
    DebugLogger.i("MainActivity", "stopIdlePopulate()$resumeInfo")
}

internal fun MainActivity.showIdlePopulateBanner(ring: Int, stats: PopulateStats, countdown: Int) {
    val countdownStr = if (countdown > 0) " ${countdown}s" else ""
    val failStr = if (stats.fails > 0) " \u26A0${stats.fails}err" else ""
    val gridStr = if (stats.gridRadius > 0) "${stats.gridRadius}m" else "probe"
    val text = "Idle: R$ring | ${stats.cells}cells | ${stats.pois}POIs(${stats.newPois}new)$failStr | $gridStr | ${stats.status}$countdownStr"
    runOnUiThread {
        statusLineManager.set(StatusLineManager.Priority.IDLE_POPULATE, text) { stopIdlePopulate() }
    }
}

// =========================================================================
// POPULATE POIs — SYSTEMATIC GRID SCANNER
// =========================================================================

/** Mutable counters shared across the populate coroutine and recursive subdivision. */
internal class PopulateStats {
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

// =========================================================================
// 10km PROBE POPULATE — expanding spiral of 10km probes for POI discovery
// =========================================================================

internal var probe10kmJob: Job? = null

/** Estimate a reasonable fill radius from a 10km probe result.
 *  Target: ~200 POIs per search. Scale radius proportionally to density. */
internal fun MainActivity.estimateFillRadius(poiCount: Int, probeRadiusM: Int): Int {
    if (poiCount == 0) return probeRadiusM  // no data — keep wide
    val targetPois = 200.0
    // Area scales with radius², so ideal radius = probeRadius * sqrt(target/actual)
    val ideal = probeRadiusM * Math.sqrt(targetPois / poiCount)
    return ideal.toInt().coerceIn(100, 10_000)
}

internal fun MainActivity.startProbe10km() {
    if (probe10kmJob?.isActive == true) {
        stopProbe10km()
        return
    }
    if (populateJob != null) {
        toast("Populate already running — stop it first")
        return
    }
    if (followedVehicleId != null || followedAircraftIcao != null) {
        toast("Stop following first")
        return
    }
    stopIdlePopulate()
    stopSilentFill()

    val center = binding.mapView.mapCenter
    val centerLat = center.latitude
    val centerLon = center.longitude
    // 10km grid spacing with 0.8 overlap factor to prevent diagonal gaps
    val stepLat = 0.8 * 2 * 10000.0 / 111320.0  // ~0.1438°
    val stepLon = stepLat / Math.cos(Math.toRadians(centerLat))
    DebugLogger.i("MainActivity", "startProbe10km() center=$centerLat,$centerLon stepLat=${"%.5f".format(stepLat)} stepLon=${"%.5f".format(stepLon)}")

    var totalPois = 0
    var totalNew = 0
    var probes = 0
    var fails = 0
    var lastCount = 0
    var lastFillRadius = 0

    probe10kmJob = lifecycleScope.launch {
        // ── Probe center first ──
        val centerPt = GeoPoint(centerLat, centerLon)
        placeScanningMarker(centerPt, panMap = false, panOnly = true)
        showProbe10kmBanner(0, 0, probes, totalPois, totalNew, fails, lastCount, lastFillRadius, "Probing center…", 0)

        val centerResult = viewModel.populateSearchAt(centerPt, radiusOverride = 10_000)
        if (centerResult != null) {
            probes++
            totalPois += centerResult.results.size
            totalNew += centerResult.poiNew
            lastCount = centerResult.results.size
            lastFillRadius = if (centerResult.capped) centerResult.radiusM
                else estimateFillRadius(centerResult.results.size, 10_000)
            loadCachedPoisForVisibleArea()
            DebugLogger.i("MainActivity",
                "10km Probe center: ${lastCount} POIs, settled=${centerResult.radiusM}m, fillRadius=${lastFillRadius}m")
            showProbe10kmBanner(0, 0, probes, totalPois, totalNew, fails, lastCount, lastFillRadius,
                "${lastCount} POIs (${centerResult.poiNew} new)", 0)
        } else {
            fails++
            showProbe10kmBanner(0, 0, probes, totalPois, totalNew, fails, lastCount, lastFillRadius, "Center FAILED", 0)
        }

        // Countdown between probes
        for (sec in 30 downTo 1) {
            showProbe10kmBanner(0, 0, probes, totalPois, totalNew, fails, lastCount, lastFillRadius, "Next ring in", sec)
            delay(1_000L)
        }

        // ── Expanding rings forever ──
        var ring = 1
        while (true) {
            val points = generateRingPoints(ring, centerLat, centerLon, stepLat, stepLon)
            for ((idx, pair) in points.withIndex()) {
                kotlinx.coroutines.yield()
                val (gridLat, gridLon) = pair
                val point = GeoPoint(gridLat, gridLon)

                placeScanningMarker(point, panMap = false, panOnly = true)
                showProbe10kmBanner(ring, idx + 1, probes, totalPois, totalNew, fails, lastCount, lastFillRadius,
                    "Probing ${idx + 1}/${points.size}…", 0)

                val result = viewModel.populateSearchAt(point, radiusOverride = 10_000)
                if (result != null) {
                    probes++
                    totalPois += result.results.size
                    totalNew += result.poiNew
                    lastCount = result.results.size
                    lastFillRadius = if (result.capped) result.radiusM
                        else estimateFillRadius(result.results.size, 10_000)
                    loadCachedPoisForVisibleArea()
                    DebugLogger.i("MainActivity",
                        "10km Probe R$ring pt${idx + 1}/${points.size}: ${lastCount} POIs, settled=${result.radiusM}m, fillRadius=${lastFillRadius}m")
                    showProbe10kmBanner(ring, idx + 1, probes, totalPois, totalNew, fails, lastCount, lastFillRadius,
                        "${lastCount} POIs (${result.poiNew} new)", 0)
                } else {
                    fails++
                    lastCount = 0
                    showProbe10kmBanner(ring, idx + 1, probes, totalPois, totalNew, fails, lastCount, lastFillRadius, "FAILED", 0)
                }

                // Countdown between probes (30s)
                for (sec in 30 downTo 1) {
                    showProbe10kmBanner(ring, idx + 1, probes, totalPois, totalNew, fails, lastCount, lastFillRadius, "Next in", sec)
                    delay(1_000L)
                }
            }
            ring++
        }
    }
}

internal fun MainActivity.stopProbe10km() {
    probe10kmJob?.cancel()
    probe10kmJob = null
    removeScanningMarker()
    statusLineManager.clear(StatusLineManager.Priority.POPULATE)
    DebugLogger.i("MainActivity", "stopProbe10km()")
}

internal fun MainActivity.showProbe10kmBanner(ring: Int, ptInRing: Int, probes: Int, pois: Int, newPois: Int,
                                 fails: Int, lastCount: Int, fillRadius: Int,
                                 status: String, countdown: Int) {
    val failStr = if (fails > 0) " \u26A0${fails}" else ""
    val countdownStr = if (countdown > 0) " ${countdown}s" else ""
    val ringStr = if (ptInRing > 0) "R$ring:$ptInRing" else "R$ring"
    val fillStr = if (fillRadius > 0) " fill:${fillRadius}m" else ""
    val text = "10km: $ringStr | ${probes}pr | ${pois}POIs(${newPois}new)$failStr | last:$lastCount$fillStr | $status$countdownStr"
    runOnUiThread {
        statusLineManager.set(StatusLineManager.Priority.POPULATE, text) { stopProbe10km() }
    }
}

internal fun Double.format(digits: Int) = "%.${digits}f".format(this)

// =========================================================================
// POPULATE POIs — SYSTEMATIC GRID SCANNER
// =========================================================================

internal fun MainActivity.startPopulatePois() {
    // Guard: don't allow while following something
    if (followedVehicleId != null || followedAircraftIcao != null) {
        toast("Stop following first")
        // Reset pref back to OFF
        val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(MenuPrefs.PREF_POPULATE_POIS, false).apply()
        return
    }
    // Cancel idle populate and silent fill — manual scanner takes over
    stopIdlePopulate(clearState = true)
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

        // Start wide at 10km — cap-retry halves down to find the right radius
        var probeResult: com.example.locationmapapp.data.model.PopulateSearchResult? = null
        for (probeAttempt in 1..3) {
            probeResult = viewModel.populateSearchAt(probePoint, radiusOverride = 10_000)
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
internal suspend fun MainActivity.searchCellSubdivisions(
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

internal fun MainActivity.stopPopulatePois() {
    populateJob?.cancel()
    populateJob = null
    removeScanningMarker()
    statusLineManager.clear(StatusLineManager.Priority.POPULATE)
    loadCachedPoisForVisibleArea()
    // Reset menu pref
    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(MenuPrefs.PREF_POPULATE_POIS, false).apply()
    DebugLogger.i("MainActivity", "stopPopulatePois()")
}

/**
 * Generate the ordered perimeter points for a square spiral ring.
 * Ring 0 = center (1 point), Ring N = perimeter of (2N+1)² grid (8N points).
 */
internal fun MainActivity.generateRingPoints(
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

/** Place crosshair marker. [panMap]=true pans+zooms, [panOnly]=true pans without zoom, both false = no map movement. */
internal fun MainActivity.placeScanningMarker(point: GeoPoint, panMap: Boolean = true, panOnly: Boolean = false) {
    runOnUiThread {
        if (scanningMarker == null) {
            scanningMarker = Marker(binding.mapView).apply {
                icon = MarkerIconHelper.forCategory(this@placeScanningMarker, "crosshair", 32)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Scanning…"
            }
            binding.mapView.overlays.add(scanningMarker)
        }
        scanningMarker?.position = point
        if (panMap) {
            if (binding.mapView.zoomLevelDouble < 18.0) {
                binding.mapView.controller.setZoom(18.0)
            }
            binding.mapView.controller.animateTo(point)
        } else if (panOnly) {
            binding.mapView.controller.animateTo(point)
        }
        binding.mapView.invalidate()
    }
}

internal fun MainActivity.removeScanningMarker() {
    scanningMarker?.let { binding.mapView.overlays.remove(it) }
    scanningMarker = null
    binding.mapView.invalidate()
}

internal fun MainActivity.showPopulateBanner(ring: Int, stats: PopulateStats, countdown: Int) {
    val countdownStr = if (countdown > 0) " ${countdown}s" else ""
    val failStr = if (stats.fails > 0) " \u26A0${stats.fails}err" else ""
    val gridStr = if (stats.gridRadius > 0) "${stats.gridRadius}m" else "probe"
    val text = "\u2316 R$ring | ${stats.cells}cells | ${stats.pois}POIs(${stats.newPois}new)$failStr | $gridStr | ${stats.status}$countdownStr"
    runOnUiThread {
        statusLineManager.set(StatusLineManager.Priority.POPULATE, text) { stopPopulatePois() }
    }
}

