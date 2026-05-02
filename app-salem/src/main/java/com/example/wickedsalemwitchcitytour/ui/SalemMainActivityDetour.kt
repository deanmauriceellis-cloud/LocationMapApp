/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * S221 — Tour detour UI: persistent floating banner, manage-dialog
 * (Return / Stop / Cancel), and the orange out-route + rejoin polylines.
 * Pure Activity-side rendering; engine-side state lives in TourEngine.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.tour.TourState
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "Detour"

private const val DETOUR_OUT_COLOR = "#FF8C00"   // bright orange
private const val DETOUR_REJOIN_COLOR = "#E91E63" // fuchsia (matches banner)

private fun SalemMainActivity.detourBannerWrapper(): View =
    binding.root.findViewById(R.id.detourBannerWrapper)

/** Wire the banner click once during onCreate. */
internal fun SalemMainActivity.wireDetourBanner() {
    val text = detourBannerWrapper().findViewById<TextView>(R.id.detourBannerText)
    text.setOnClickListener { openDetourManageDialog() }
    detourBannerWrapper().setOnClickListener { openDetourManageDialog() }
}

/** Apply the visual state for [TourState.Detour] — banner + out-route. */
internal fun SalemMainActivity.applyDetourState(state: TourState.Detour) {
    showDetourBanner(state.detourPoiName)
    drawDetourOutRoute(state.detourPoiId)
}

/** Tear down all detour visuals — called on transition off Detour. */
internal fun SalemMainActivity.clearDetourVisuals() {
    hideDetourBanner()
    clearDetourOverlays()
}

private fun SalemMainActivity.showDetourBanner(poiName: String) {
    val text = detourBannerWrapper().findViewById<TextView>(R.id.detourBannerText)
    text.text = "On detour to $poiName · tap to manage"
    detourBannerWrapper().visibility = View.VISIBLE
}

private fun SalemMainActivity.hideDetourBanner() {
    detourBannerWrapper().visibility = View.GONE
}

private fun SalemMainActivity.clearDetourOverlays() {
    detourOutPolyline?.let { binding.mapView.overlays.remove(it) }
    detourOutPolyline = null
    detourRejoinPolyline?.let { binding.mapView.overlays.remove(it) }
    detourRejoinPolyline = null
    binding.mapView.invalidate()
}

private fun SalemMainActivity.drawDetourOutRoute(detourPoiId: String) {
    val from = lastGpsPoint ?: run {
        DebugLogger.i(TAG, "drawDetourOutRoute: no GPS fix yet — skipping out-route")
        return
    }
    lifecycleScope.launch {
        val poi = tourViewModel.getSalemPoiById(detourPoiId) ?: run {
            DebugLogger.w(TAG, "drawDetourOutRoute: poi not found id=$detourPoiId")
            return@launch
        }
        val to = GeoPoint(poi.lat, poi.lng)
        val route = walkingDirections.getRoute(from, to)
        if (route == null || route.polyline.isEmpty()) {
            DebugLogger.i(TAG, "drawDetourOutRoute: no route id=$detourPoiId — drawing direct line")
            paintDetourOutPolyline(listOf(from, to))
            return@launch
        }
        paintDetourOutPolyline(route.polyline)
    }
}

private fun SalemMainActivity.paintDetourOutPolyline(points: List<GeoPoint>) {
    detourOutPolyline?.let { binding.mapView.overlays.remove(it) }
    val line = Polyline().apply {
        setPoints(points)
        outlinePaint.apply {
            color = Color.parseColor(DETOUR_OUT_COLOR)
            strokeWidth = 8f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        title = "Detour route"
    }
    binding.mapView.overlays.add(line)
    detourOutPolyline = line
    binding.mapView.invalidate()
}

private fun SalemMainActivity.paintDetourRejoinPolyline(points: List<GeoPoint>) {
    detourRejoinPolyline?.let { binding.mapView.overlays.remove(it) }
    val line = Polyline().apply {
        setPoints(points)
        outlinePaint.apply {
            color = Color.parseColor(DETOUR_REJOIN_COLOR)
            strokeWidth = 8f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        title = "Return to tour"
    }
    binding.mapView.overlays.add(line)
    detourRejoinPolyline = line
    binding.mapView.invalidate()
}

private fun SalemMainActivity.openDetourManageDialog() {
    val state = tourViewModel.tourState.value as? TourState.Detour ?: return
    AlertDialog.Builder(this)
        .setTitle("On detour to ${state.detourPoiName}")
        .setItems(arrayOf("Return to tour", "Stop tour, continue later", "Cancel")) { dlg, which ->
            dlg.dismiss()
            when (which) {
                0 -> chooseReturnTargetThenRejoin(state)
                1 -> confirmStopTourContinueLater()
                else -> Unit
            }
        }
        .show()
}

private fun SalemMainActivity.chooseReturnTargetThenRejoin(state: TourState.Detour) {
    AlertDialog.Builder(this)
        .setTitle("Where to rejoin?")
        .setItems(arrayOf("Nearest point on path", "Back to last stop", "Cancel")) { dlg, which ->
            dlg.dismiss()
            when (which) {
                0 -> rejoinAt(state, TourViewModel.ReturnTarget.NEAREST_POINT)
                1 -> rejoinAt(state, TourViewModel.ReturnTarget.LAST_STOP)
                else -> Unit
            }
        }
        .show()
}

private fun SalemMainActivity.rejoinAt(
    state: TourState.Detour,
    target: TourViewModel.ReturnTarget,
) {
    val from = lastGpsPoint ?: run {
        DebugLogger.w(TAG, "rejoinAt: no GPS fix — flipping engine state without rendering rejoin polyline")
        tourViewModel.returnToTourFromDetour(target)
        return
    }
    val tourPoints = tourViewModel.getAllStopLocations()
    if (tourPoints.isEmpty()) {
        DebugLogger.w(TAG, "rejoinAt: empty tour polyline — flipping engine state only")
        tourViewModel.returnToTourFromDetour(target)
        return
    }
    val rejoinTarget: GeoPoint = when (target) {
        TourViewModel.ReturnTarget.NEAREST_POINT -> nearestPointOnPath(from, tourPoints)
        TourViewModel.ReturnTarget.LAST_STOP -> {
            val activeTour = state.activeTour
            val idx = activeTour.progress.currentStopIndex.coerceIn(0, tourPoints.size - 1)
            tourPoints[idx]
        }
    }
    lifecycleScope.launch {
        val route = walkingDirections.getRoute(from, rejoinTarget)
        val pts = route?.polyline?.takeIf { it.isNotEmpty() } ?: listOf(from, rejoinTarget)
        paintDetourRejoinPolyline(pts)
        // Flip engine state — observer drops the banner + out-line. The
        // rejoin polyline survives until the next state transition (e.g.
        // tour Idle / Completed) clears overlays.
        tourViewModel.returnToTourFromDetour(target)
    }
}

private fun SalemMainActivity.confirmStopTourContinueLater() {
    AlertDialog.Builder(this)
        .setTitle("Stop tour, continue later?")
        .setMessage("Your progress will be saved. Next time you open the app, you can resume from where you left off.")
        .setPositiveButton("Stop") { dlg, _ ->
            dlg.dismiss()
            tourViewModel.stopTourFromDetour()
        }
        .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
        .show()
}

/**
 * S221 — gateway from a subtopic adjacent_poi card to that POI's detail.
 * Flies the map to the linked POI then opens its [PoiDetailSheet]. Shared by
 * both the narration banner and the POI Detail Sheet's own subtopic strip.
 */
internal fun SalemMainActivity.openPoiDetailFromSubtopic(poiId: String) {
    lifecycleScope.launch {
        val poi = tourViewModel.getSalemPoiById(poiId) ?: run {
            DebugLogger.w(TAG, "openPoiDetailFromSubtopic: poi not found id=$poiId")
            return@launch
        }
        binding.mapView.controller.animateTo(GeoPoint(poi.lat, poi.lng), 19.0, 600L)
        PoiDetailSheet.show(poi, supportFragmentManager)
    }
}

// ── Geometry helper ──────────────────────────────────────────────────────────

private fun nearestPointOnPath(from: GeoPoint, points: List<GeoPoint>): GeoPoint {
    var best = points.first()
    var bestM = haversineM(from, best)
    for (p in points.drop(1)) {
        val d = haversineM(from, p)
        if (d < bestM) {
            bestM = d
            best = p
        }
    }
    return best
}

private fun haversineM(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
        sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(h), sqrt(1 - h))
}
