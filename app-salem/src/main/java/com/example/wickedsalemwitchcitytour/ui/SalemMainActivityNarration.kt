/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Phase 9T: Salem Walking Tour Restructure — Narration system wiring
 */

package com.example.wickedsalemwitchcitytour.ui

import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.model.NarrationPoint
import com.example.wickedsalemwitchcitytour.tour.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/** Narration queue — auto-advances through nearby POIs */
private val narrationQueue = ArrayDeque<NarrationPoint>()
private var currentNarration: NarrationPoint? = null
private var narrationAutoPlay = true

/**
 * Initialize the Phase 9T narration system:
 * - Load all narration points from Room DB
 * - Set up NarrationGeofenceManager with auto-queuing
 * - Set up CorridorGeofenceManager
 * - Set up ProximityDock (positioned above controls)
 * - Observe geofence events for narration dialog triggers
 */
internal fun SalemMainActivity.initNarrationSystem() {
    DebugLogger.i("SalemMainActivity", "initNarrationSystem() — Phase 9T setup")

    // narrationGeofenceManager is Hilt-injected (S110) — already constructed.
    // Don't replace it here; the singleton lift is what preserves dedup state
    // across Activity recreation.
    corridorManager = CorridorGeofenceManager()

    // Initialize proximity dock
    proximityDock = ProximityDock(this, binding.root).apply {
        onPoiTapped = { point ->
            enqueueNarration(point, jumpToFront = true)
        }
    }

    // Load corridors
    corridorManager?.loadCorridors(SalemCorridors.all())

    // Load narration points from database — ALWAYS ON, no tour selection needed
    lifecycleScope.launch {
        try {
            val points = tourViewModel.loadNarrationPoints()
            if (points.isNotEmpty()) {
                narrationGeofenceManager.loadPoints(points)
                // Show proximity dock immediately
                proximityDock?.show()
                // Also load narration point markers on the map
                loadNarrationPointMarkers()
                DebugLogger.i("SalemMainActivity", "Ambient narration ACTIVE: ${points.size} points, ${SalemCorridors.all().size} corridors — no tour selection required")
            } else {
                DebugLogger.w("SalemMainActivity", "No narration points in database")
            }
        } catch (e: Exception) {
            DebugLogger.e("SalemMainActivity", "Failed to load narration points", e)
        }
    }

    // Observe narration geofence events — auto-queue on entry
    lifecycleScope.launch {
        narrationGeofenceManager.events.collectLatest { event ->
            when (event.type) {
                NarrationEventType.APPROACH -> {
                    DebugLogger.i("SalemMainActivity",
                        "APPROACH: ${event.point.name} (${event.distanceM.toInt()}m)")
                }
                NarrationEventType.ENTRY -> {
                    DebugLogger.i("SalemMainActivity",
                        "ENTRY: ${event.point.name} (${event.distanceM.toInt()}m)")
                    enqueueNarration(event.point, jumpToFront = false)
                }
            }
        }
    }

    // Observe TTS completion to auto-advance queue, with silence reach-out
    lifecycleScope.launch {
        tourViewModel.narrationState.collectLatest { state ->
            DebugLogger.i("NARR-STATE", "narrationState → $state (autoPlay=$narrationAutoPlay, queueSize=${narrationQueue.size})")
            if (state is com.example.wickedsalemwitchcitytour.tour.NarrationState.Idle && narrationAutoPlay) {
                // Clear currentNarration — TTS is done, allow next entry to play immediately
                currentNarration = null
                DebugLogger.i("NARR-STATE", "  Idle → currentNarration cleared, queueSize=${narrationQueue.size}")

                if (narrationQueue.isNotEmpty()) {
                    // TTS finished — play next in queue after brief pause
                    val gapMs = if (walkSimRunning) 500L else 2000L
                    DebugLogger.i("NARR-STATE", "  Queue has ${narrationQueue.size} → advancing after ${gapMs}ms")
                    kotlinx.coroutines.delay(gapMs)
                    playNextNarration()
                } else {
                    // Queue empty — wait, then either play queued items or reach out
                    val silenceMs = if (walkSimRunning) 500L else 5000L
                    kotlinx.coroutines.delay(silenceMs)
                    if (narrationQueue.isNotEmpty()) {
                        // Items queued during the silence delay — play them
                        DebugLogger.i("NARR-STATE", "  Queue filled during silence (${narrationQueue.size}) → playing")
                        playNextNarration()
                    } else {
                        // ── S110 staleness gate ──
                        //    Suppress reach-out entirely when GPS is stale. Without
                        //    this, the reach-out logic happily searches for the
                        //    "nearest unnarrated POI" against a frozen position
                        //    and cycles through every POI in the cluster — the
                        //    bug that ran the user through downtown Salem while
                        //    they were sitting at home.
                        val now = System.currentTimeMillis()
                        val ageMs = if (lastFixAtMs == 0L) Long.MAX_VALUE else (now - lastFixAtMs)
                        if (ageMs > GPS_STALE_THRESHOLD_MS) {
                            DebugLogger.w("NARR-STATE",
                                "  REACH-OUT SUPPRESSED: GPS stale " +
                                "(age=${if (ageMs == Long.MAX_VALUE) "never" else "${ageMs / 1000}s"}, " +
                                "threshold=${GPS_STALE_THRESHOLD_MS / 1000}s)")
                            return@collectLatest
                        }

                        // Still empty — reach out to nearest un-narrated POI
                        val nearest = narrationGeofenceManager.findNearestUnnarrated()
                        if (nearest != null) {
                            DebugLogger.i("NARR-STATE",
                                "  REACH-OUT: ${nearest.point.name} (${nearest.distanceM.toInt()}m) gpsAge=${ageMs}ms")
                            narrationGeofenceManager.triggerReachOut(nearest)
                        } else {
                            DebugLogger.i("NARR-STATE",
                                "  Nothing within 15m reach-out radius (gpsAge=${ageMs}ms)")
                        }
                    }
                }
            }
        }
    }

    DebugLogger.i("SalemMainActivity", "initNarrationSystem() complete")
}

/**
 * Add a narration point to the queue.
 * @param jumpToFront if true (user tapped), interrupt current and play immediately
 *
 * In dense areas with multiple triggers, the queue is ordered by:
 * 1. adPriority DESC (paying merchants first — revenue)
 * 2. priority ASC (historical importance second — 1=must-hear, 5=filler)
 */
internal fun SalemMainActivity.enqueueNarration(point: NarrationPoint, jumpToFront: Boolean) {
    DebugLogger.i("NARR-QUEUE", "enqueueNarration: ${point.name} jumpToFront=$jumpToFront currentNarration=${currentNarration?.name} queueSize=${narrationQueue.size}")
    // Don't add duplicates
    if (point.id == currentNarration?.id) {
        DebugLogger.i("NARR-QUEUE", "SKIP duplicate (current): ${point.name}")
        return
    }
    if (narrationQueue.any { it.id == point.id }) {
        DebugLogger.i("NARR-QUEUE", "SKIP duplicate (queued): ${point.name}")
        return
    }

    if (jumpToFront || currentNarration == null) {
        // Play immediately (user tapped or nothing playing)
        DebugLogger.i("NARR-QUEUE", "PLAY NOW: ${point.name} (jumpToFront=$jumpToFront, currentNull=${currentNarration == null})")
        narrationQueue.addFirst(point)
        playNextNarration()
    } else {
        // Insert by priority: merchants first, then historical importance
        val insertIdx = narrationQueue.indexOfFirst { queued ->
            // Insert before the first item that is less important
            point.adPriority > queued.adPriority ||
                (point.adPriority == queued.adPriority && point.priority < queued.priority)
        }
        if (insertIdx >= 0) {
            // ArrayDeque doesn't have positional insert — rebuild
            val list = narrationQueue.toMutableList()
            list.add(insertIdx, point)
            narrationQueue.clear()
            list.forEach { narrationQueue.addLast(it) }
        } else {
            narrationQueue.addLast(point)
        }
        updateQueueIndicator()
        DebugLogger.i("SalemMainActivity", "Queued: ${point.name} [ad=${point.adPriority} pri=${point.priority}] (${narrationQueue.size} in queue)")
    }
}

/** Play the next narration point from the queue */
internal fun SalemMainActivity.playNextNarration() {
    val point = narrationQueue.pollFirst()
    if (point == null) {
        DebugLogger.i("NARR-PLAY", "playNextNarration: queue empty, nothing to play")
        return
    }
    currentNarration = point
    DebugLogger.i("NARR-PLAY", "playNextNarration: ${point.name} (${narrationQueue.size} remaining in queue)")
    showNarrationSheet(point)

    // Auto-play TTS — select narration pass based on lifetime visit count
    val text = narrationGeofenceManager.getNarrationForPass(point)
        ?: point.shortNarration ?: point.description

    // Resolve voice: POI override > category default
    val voiceId = point.voiceOverride
        ?: com.example.wickedsalemwitchcitytour.tour.CategoryVoiceMap.voiceForCategory(point.type)
    DebugLogger.i("NARR-PLAY", "  text=${if (text != null) "${text.take(60)}..." else "NULL"} voice=$voiceId narrationAutoPlay=$narrationAutoPlay")
    if (text != null && narrationAutoPlay) {
        DebugLogger.i("NARR-PLAY", "  → calling tourViewModel.speakNarration() voice=$voiceId")
        tourViewModel.speakNarration(text, point.name, voiceId)
    } else {
        DebugLogger.w("NARR-PLAY", "  → NOT speaking: text=${text != null} autoPlay=$narrationAutoPlay")
    }
    // Record this visit for next-pass selection on future days
    narrationGeofenceManager.recordVisit(point.id)
}

/**
 * Show the narration bottom sheet for a narration point.
 */
internal fun SalemMainActivity.showNarrationSheet(point: NarrationPoint) {
    val sheet = binding.root.findViewById<View>(R.id.narrationSheet) ?: return

    // Populate content
    sheet.findViewById<TextView>(R.id.narrationTitle)?.text = point.name
    sheet.findViewById<TextView>(R.id.categoryLabel)?.text =
        point.type.replace('_', ' ').split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    val narrationText = narrationGeofenceManager.getNarrationForPass(point)
        ?: point.shortNarration ?: point.description ?: "Narration coming soon."
    sheet.findViewById<TextView>(R.id.narrationText)?.text = narrationText

    // Distance
    val loc = viewModel.currentLocation.value?.point
    if (loc != null) {
        val dist = haversineM(loc.latitude, loc.longitude, point.lat, point.lng)
        sheet.findViewById<TextView>(R.id.distanceLabel)?.text = formatDistanceM(dist)
    }

    // Queue indicator
    updateQueueIndicator()

    // Show the sheet
    sheet.visibility = View.VISIBLE

    // Action buttons
    sheet.findViewById<View>(R.id.btnSkip)?.setOnClickListener {
        tourViewModel.speakNarration("", "")  // stop current TTS
        if (narrationQueue.isNotEmpty()) {
            playNextNarration()
        } else {
            sheet.visibility = View.GONE
            currentNarration = null
        }
    }
    sheet.findViewById<View>(R.id.btnPlayPause)?.setOnClickListener {
        val text = point.longNarration ?: point.shortNarration ?: point.description
        if (text != null) {
            tourViewModel.speakNarration(text, point.name)
        }
    }
    sheet.findViewById<View>(R.id.btnMoreInfo)?.setOnClickListener {
        val longText = point.longNarration ?: point.description ?: "No additional information."
        sheet.findViewById<TextView>(R.id.narrationText)?.text = longText
    }
    sheet.findViewById<View>(R.id.btnDirections)?.setOnClickListener {
        Toast.makeText(this, "Directions to ${point.name}", Toast.LENGTH_SHORT).show()
    }
    sheet.findViewById<View>(R.id.btnGeofence)?.setOnClickListener {
        toggleGeofenceOverlay(point)
    }
}

private fun SalemMainActivity.updateQueueIndicator() {
    val indicator = binding.root.findViewById<TextView>(R.id.queueIndicator)
    if (narrationQueue.isNotEmpty()) {
        indicator?.text = "${narrationQueue.size} more nearby"
        indicator?.visibility = View.VISIBLE
    } else {
        indicator?.visibility = View.GONE
    }
}

private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).let { it * it }
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

private fun formatDistanceM(meters: Double): String = when {
    meters < 100 -> "${meters.toInt()}m"
    meters < 1000 -> "${(meters / 10).toInt() * 10}m"
    else -> "${"%.1f".format(meters / 1000)}km"
}

// ── Geofence overlay ────────────────────────────────────────────────────

/** Currently displayed geofence overlay (null = hidden) */
private var geofenceOverlay: Polygon? = null
private var geofenceOverlayPointId: String? = null

/**
 * Toggle the geofence circle for a narration point on the map.
 * Shows entry radius (solid) and approach radius (dashed outline).
 * Tapping again or tapping for a different POI replaces it.
 */
internal fun SalemMainActivity.toggleGeofenceOverlay(point: NarrationPoint) {
    // Remove existing overlay
    geofenceOverlay?.let { binding.mapView.overlays.remove(it) }

    // If same POI tapped again, just hide
    if (geofenceOverlayPointId == point.id) {
        geofenceOverlay = null
        geofenceOverlayPointId = null
        binding.mapView.invalidate()
        Toast.makeText(this, "Geofence hidden", Toast.LENGTH_SHORT).show()
        return
    }

    val center = GeoPoint(point.lat, point.lng)
    val walkSim = narrationGeofenceManager.walkSimMode
    val baseRadius = point.geofenceRadiusM.toDouble()
    val entryRadius = if (walkSim) baseRadius * 3.0 else baseRadius
    val approachRadius = entryRadius * 2.0

    // Entry zone — filled circle
    val entryCircle = Polygon().apply {
        points = Polygon.pointsAsCircle(center, entryRadius)
        fillPaint.color = Color.argb(50, 76, 175, 80)   // green fill 20%
        outlinePaint.color = Color.argb(180, 76, 175, 80) // green outline
        outlinePaint.strokeWidth = 3f
        title = "${point.name} — entry ${entryRadius.toInt()}m${if (walkSim) " (walk sim 3x)" else ""}"
    }

    // Approach zone — outline only
    val approachCircle = Polygon().apply {
        points = Polygon.pointsAsCircle(center, approachRadius)
        fillPaint.color = Color.argb(20, 255, 193, 7)    // amber fill 8%
        outlinePaint.color = Color.argb(120, 255, 193, 7) // amber outline
        outlinePaint.strokeWidth = 2f
        title = "${point.name} — approach ${approachRadius.toInt()}m"
    }

    // Add approach first (behind), then entry (on top)
    binding.mapView.overlays.add(approachCircle)
    binding.mapView.overlays.add(entryCircle)

    // Track for removal — store entry circle as the "main" overlay, remove both later
    geofenceOverlay = entryCircle
    geofenceOverlayPointId = point.id

    // Center map on the POI
    binding.mapView.controller.animateTo(center)

    binding.mapView.invalidate()
    Toast.makeText(this, "Geofence: entry=${entryRadius.toInt()}m approach=${approachRadius.toInt()}m", Toast.LENGTH_SHORT).show()

    // Store approach circle for cleanup too
    entryCircle.relatedObject = approachCircle
}

/** Remove any active geofence overlay (call when narration sheet hides) */
internal fun SalemMainActivity.clearGeofenceOverlay() {
    geofenceOverlay?.let { entry ->
        binding.mapView.overlays.remove(entry)
        (entry.relatedObject as? Polygon)?.let { binding.mapView.overlays.remove(it) }
    }
    geofenceOverlay = null
    geofenceOverlayPointId = null
    binding.mapView.invalidate()
}
