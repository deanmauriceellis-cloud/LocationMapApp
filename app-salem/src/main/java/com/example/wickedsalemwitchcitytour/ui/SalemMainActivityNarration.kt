/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Phase 9T: Salem Walking Tour Restructure — Narration system wiring
 */

package com.example.wickedsalemwitchcitytour.ui

import android.view.View
import android.widget.TextView
import android.widget.Toast
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

    // Initialize managers
    narrationGeofenceManager = NarrationGeofenceManager().apply { init(this@initNarrationSystem) }
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
                narrationGeofenceManager?.loadPoints(points)
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
        narrationGeofenceManager?.events?.collectLatest { event ->
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
            if (state is com.example.wickedsalemwitchcitytour.tour.NarrationState.Idle && narrationAutoPlay) {
                if (narrationQueue.isNotEmpty()) {
                    // TTS finished — play next in queue after brief pause
                    kotlinx.coroutines.delay(2000) // 2s gap between narrations
                    playNextNarration()
                } else {
                    // Queue empty — wait 5s then reach out to nearest un-narrated POI
                    // This keeps narration flowing even between geofence zones
                    kotlinx.coroutines.delay(5000)
                    // Re-check: still idle and queue still empty?
                    if (narrationQueue.isEmpty()) {
                        val mgr = narrationGeofenceManager ?: return@collectLatest
                        val nearest = mgr.findNearestUnnarrated()
                        if (nearest != null) {
                            DebugLogger.i("SalemMainActivity",
                                "REACH-OUT: ${nearest.point.name} (${nearest.distanceM.toInt()}m away)")
                            mgr.triggerReachOut(nearest)
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
    // Don't add duplicates
    if (point.id == currentNarration?.id) return
    if (narrationQueue.any { it.id == point.id }) return

    if (jumpToFront || currentNarration == null) {
        // Play immediately (user tapped or nothing playing)
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
    val point = narrationQueue.pollFirst() ?: return
    currentNarration = point
    showNarrationSheet(point)

    // Auto-play TTS — select narration pass based on lifetime visit count
    val mgr = narrationGeofenceManager
    val text = mgr?.getNarrationForPass(point) ?: point.shortNarration ?: point.description
    if (text != null && narrationAutoPlay) {
        tourViewModel.speakNarration(text, point.name)
    }
    // Record this visit for next-pass selection on future days
    mgr?.recordVisit(point.id)
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

    val narrationText = narrationGeofenceManager?.getNarrationForPass(point)
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
