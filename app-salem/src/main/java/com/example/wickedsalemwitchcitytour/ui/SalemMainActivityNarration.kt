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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/** Narration queue — auto-advances through nearby POIs.
 *
 *  S112: This queue is now SOURCE-OF-TRUTH for both the audio play order and
 *  the proximity dock display. Insertion is order-agnostic (`addLast`); selection
 *  happens at dequeue time, where the queue is filtered by distance to the
 *  current user position (>50m → discarded) and sorted by tier (paid → historic
 *  → attraction → rest), then closest within tier wins. The dock renders the
 *  same filtered+sorted snapshot so dock order = play order. */
private val narrationQueue = ArrayDeque<NarrationPoint>()
private var currentNarration: NarrationPoint? = null
private var narrationAutoPlay = true

/**
 * S112: file-scope cache of the most recent user GPS position, fed by
 * SalemMainActivityTour.updateTourLocation(). Used by the dequeue logic to
 * compute distance for the 50m discard filter and the within-tier closest-wins
 * selection. (0.0, 0.0) means "no fix yet" — dequeue falls back to FIFO until
 * the first real fix arrives.
 */
private var lastUserLat: Double = 0.0
private var lastUserLng: Double = 0.0

/**
 * S112+: Three-level discard radius cascade. The dequeue starts at the
 * standard radius and tightens stepwise as density increases:
 *   - STANDARD (40m): sparse mode, broad reach
 *   - HIGH     (30m): 3+ POIs within 40m → tighten focus
 *   - SUPER    (20m): 3+ POIs within 30m → very tight, "right in front of you"
 *
 * Each level drops POIs farther than the radius before tier evaluation.
 */
private const val STANDARD_DISCARD_RADIUS_M = 40.0
private const val HIGH_DENSITY_DISCARD_RADIUS_M = 30.0
private const val SUPER_HIGH_DENSITY_DISCARD_RADIUS_M = 20.0

/** S112+: POI count within standard radius that triggers HD mode. */
private const val HIGH_DENSITY_THRESHOLD = 3

/** S112+: POI count within HD radius that triggers SUPER-HD mode. */
private const val SUPER_HIGH_DENSITY_THRESHOLD = 3

/**
 * S112+: Bearing-based "ahead" filter — track the user's movement direction
 * computed from the prev → current GPS positions. POIs whose bearing from
 * the user is more than this many degrees off the movement direction are
 * dropped at dequeue (they're behind the user). 90° half-angle = entire
 * front half-circle, which eliminates only POIs strictly behind.
 */
private const val AHEAD_CONE_HALF_ANGLE_DEG = 90.0

/** S112+: Minimum movement (meters) before we update the bearing. Below this
 *  the GPS jitter is too large to compute a reliable direction. */
private const val BEARING_UPDATE_MIN_MOVE_M = 1.0

/** S112+: Cached movement bearing in degrees (0=N, 90=E, 180=S, 270=W).
 *  Null until the first meaningful movement is observed. */
private var lastMovementBearing: Double? = null

/** S112+: Previous user position used to compute movement bearing. */
private var prevUserLat: Double = 0.0
private var prevUserLng: Double = 0.0

/**
 * S112: Glowing highlight ring drawn around the currently-narrated POI on the
 * map. The inner Polygon is the bright ring; the outer Polygon pulses around
 * it. Both are sized to hug the POI icon visually rather than mark a geofence.
 */
private var narrationHighlightInner: Polygon? = null
private var narrationHighlightOuter: Polygon? = null
private var narrationHighlightAnimJob: Job? = null

/** S112+: visual radius of the inner highlight ring — small, hugs the POI icon. */
private const val HIGHLIGHT_INNER_RADIUS_M = 5.0

/** S112+: visual radius of the outer (pulsing) highlight ring — small halo. */
private const val HIGHLIGHT_OUTER_RADIUS_M = 9.0

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
                            clearNarrationHighlight()  // S112+: nothing more coming → no ring
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
                            clearNarrationHighlight()  // S112+: nothing more coming → no ring
                        }
                    }
                }
            }
        }
    }

    DebugLogger.i("SalemMainActivity", "initNarrationSystem() complete")
}

/**
 * S112: Push the latest GPS position into the narration queue subsystem.
 * Called from SalemMainActivityTour.updateTourLocation() on every GPS fix.
 *
 * S112+: Also computes the movement bearing from the prev → current
 * positions when the move is large enough to be meaningful (>= 1m). The
 * bearing drives the "ahead-only" filter at dequeue time.
 */
internal fun SalemMainActivity.updateNarrationUserPosition(lat: Double, lng: Double) {
    // Update movement bearing if we have a meaningful step
    if (lastUserLat != 0.0 || lastUserLng != 0.0) {
        val moved = haversineM(lastUserLat, lastUserLng, lat, lng)
        if (moved >= BEARING_UPDATE_MIN_MOVE_M) {
            lastMovementBearing = bearingDeg(lastUserLat, lastUserLng, lat, lng)
            prevUserLat = lastUserLat
            prevUserLng = lastUserLng
        }
    }
    lastUserLat = lat
    lastUserLng = lng
    refreshProximityDockFromQueue()
}

/**
 * S112+: Compute compass bearing from (lat1,lng1) → (lat2,lng2) in degrees.
 * 0 = North, 90 = East, 180 = South, 270 = West. Result normalized to [0, 360).
 */
private fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLng = Math.toRadians(lng2 - lng1)
    val y = kotlin.math.sin(dLng) * kotlin.math.cos(phi2)
    val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) -
            kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLng)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

/**
 * S112+: Smallest absolute difference between two compass bearings, in degrees.
 * Result is in [0, 180]. Used to test "is POI ahead of me" — if angleDiff
 * (bearingToPoi, movementBearing) <= AHEAD_CONE_HALF_ANGLE_DEG, the POI is
 * within the front half-circle.
 */
private fun angleDiffDeg(a: Double, b: Double): Double {
    var diff = (a - b + 540.0) % 360.0 - 180.0
    if (diff < 0) diff = -diff
    return diff
}

/**
 * S112+: Returns true if the POI is "ahead" of the user — meaning the bearing
 * from user → POI is within the AHEAD_CONE_HALF_ANGLE_DEG cone of the user's
 * current movement direction. If we don't have a movement bearing yet (cold
 * start, stationary forever), all POIs pass.
 */
private fun isPoiAhead(poiLat: Double, poiLng: Double): Boolean {
    val moveBearing = lastMovementBearing ?: return true
    val poiBearing = bearingDeg(lastUserLat, lastUserLng, poiLat, poiLng)
    return angleDiffDeg(poiBearing, moveBearing) <= AHEAD_CONE_HALF_ANGLE_DEG
}

/**
 * Add a narration point to the queue.
 * @param jumpToFront if true (user tapped), interrupt current and play immediately
 *
 * S112: Insertion is now order-agnostic — selection happens at dequeue time
 * where the queue is filtered by distance to the user (>50m → drop) and sorted
 * by tier (paid > historic > attraction > rest), with closest-within-tier as
 * the tiebreaker. The previous adPriority/priority insertion sort is removed
 * because the new tier system supersedes it.
 *
 * jumpToFront still works: user tap on the dock plays immediately, bypassing
 * the tier filter (explicit user intent overrides auto-prioritization).
 */
internal fun SalemMainActivity.enqueueNarration(point: NarrationPoint, jumpToFront: Boolean) {
    val tier = NarrationTierClassifier.classify(point)
    DebugLogger.i("NARR-QUEUE", "enqueueNarration: ${point.name} tier=$tier ad=${point.adPriority} jumpToFront=$jumpToFront currentNarration=${currentNarration?.name} queueSize=${narrationQueue.size}")
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
        // Play immediately (user tapped or nothing playing).
        // jumpToFront bypasses the tier sort — user tap is explicit intent.
        DebugLogger.i("NARR-QUEUE", "PLAY NOW: ${point.name} (jumpToFront=$jumpToFront, currentNull=${currentNarration == null})")
        narrationQueue.addFirst(point)
        playNextNarration()
    } else {
        // Just append. The dequeue logic will pick the correct order at play time.
        narrationQueue.addLast(point)
        updateQueueIndicator()
        refreshProximityDockFromQueue()
        DebugLogger.i("SalemMainActivity", "Queued: ${point.name} [tier=$tier ad=${point.adPriority}] (${narrationQueue.size} in queue)")
    }
}

/**
 * S112: Pick the next NarrationPoint to play, applying the dequeue rules:
 *   1. Drop everything farther than 50m from the current user position.
 *   2. Find the highest non-empty tier (PAID → HISTORIC → ATTRACTION → REST).
 *   3. Within that tier, return the closest POI to the user.
 *
 * The picked entry is REMOVED from the queue. Returns null if the queue is
 * empty after filtering.
 *
 * Falls back to pure FIFO if no GPS fix has arrived yet (lat=lng=0.0) — this
 * preserves cold-start behavior where the queue might receive a jumpToFront
 * tap before any GPS is available.
 */
private fun SalemMainActivity.pickNextFromQueue(): NarrationPoint? {
    if (narrationQueue.isEmpty()) return null

    // Cold start fallback — no GPS yet, return FIFO order
    if (lastUserLat == 0.0 && lastUserLng == 0.0) {
        return narrationQueue.pollFirst()  // pollFirst returns null if empty
    }

    // S112+: Three-level density cascade. Count POIs within each radius and
    // pick the tightest level whose threshold is exceeded.
    val nearbyStandard = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= STANDARD_DISCARD_RADIUS_M
    }
    val nearbyHd = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= HIGH_DENSITY_DISCARD_RADIUS_M
    }
    val discardRadius = when {
        nearbyHd >= SUPER_HIGH_DENSITY_THRESHOLD -> SUPER_HIGH_DENSITY_DISCARD_RADIUS_M
        nearbyStandard >= HIGH_DENSITY_THRESHOLD -> HIGH_DENSITY_DISCARD_RADIUS_M
        else -> STANDARD_DISCARD_RADIUS_M
    }
    val mode = when (discardRadius) {
        SUPER_HIGH_DENSITY_DISCARD_RADIUS_M -> "SUPER-HD"
        HIGH_DENSITY_DISCARD_RADIUS_M -> "HD"
        else -> "STANDARD"
    }
    if (discardRadius < STANDARD_DISCARD_RADIUS_M) {
        DebugLogger.i("NARR-QUEUE",
            "$mode MODE: $nearbyStandard POIs in ${STANDARD_DISCARD_RADIUS_M.toInt()}m, $nearbyHd in ${HIGH_DENSITY_DISCARD_RADIUS_M.toInt()}m → discard=${discardRadius.toInt()}m")
    }

    // Step 1: drop anything > discardRadius (out of range) OR behind the user
    val survivors = mutableListOf<NarrationPoint>()
    val droppedNames = mutableListOf<String>()
    val droppedBehind = mutableListOf<String>()
    for (point in narrationQueue) {
        val dist = haversineM(lastUserLat, lastUserLng, point.lat, point.lng)
        if (dist > discardRadius) {
            droppedNames.add("${point.name}(${dist.toInt()}m)")
            continue
        }
        if (!isPoiAhead(point.lat, point.lng)) {
            droppedBehind.add("${point.name}(${dist.toInt()}m)")
            continue
        }
        survivors.add(point)
    }
    if (droppedNames.isNotEmpty()) {
        DebugLogger.i("NARR-QUEUE",
            "Dequeue dropped ${droppedNames.size} stale (>${discardRadius.toInt()}m): " +
            droppedNames.take(5).joinToString(", ") +
            if (droppedNames.size > 5) " +${droppedNames.size - 5} more" else ""
        )
    }
    if (droppedBehind.isNotEmpty()) {
        DebugLogger.i("NARR-QUEUE",
            "Dequeue dropped ${droppedBehind.size} behind-user: " +
            droppedBehind.take(5).joinToString(", ") +
            if (droppedBehind.size > 5) " +${droppedBehind.size - 5} more" else ""
        )
    }

    // Rebuild the queue with only the survivors (keeps the queue clean for next iteration)
    narrationQueue.clear()
    narrationQueue.addAll(survivors)

    if (survivors.isEmpty()) return null

    // Step 2: find highest non-empty tier; Step 3: closest within that tier
    for (tier in NarrationTier.values()) {
        val candidates = survivors.filter { NarrationTierClassifier.classify(it) == tier }
        if (candidates.isEmpty()) continue
        val winner = candidates.minByOrNull { haversineM(lastUserLat, lastUserLng, it.lat, it.lng) }
            ?: continue
        narrationQueue.remove(winner)
        DebugLogger.i("NARR-QUEUE",
            "Picked ${winner.name} tier=$tier dist=${haversineM(lastUserLat, lastUserLng, winner.lat, winner.lng).toInt()}m " +
            "(${candidates.size} in tier, ${survivors.size} total survivors)")
        return winner
    }
    return null
}

/** Play the next narration point from the queue (S112: tier+distance dequeue) */
internal fun SalemMainActivity.playNextNarration() {
    val point = pickNextFromQueue()
    if (point == null) {
        DebugLogger.i("NARR-PLAY", "playNextNarration: queue empty after filter, nothing to play")
        clearNarrationHighlight()  // S112+: nothing playing → no ring
        refreshProximityDockFromQueue()
        return
    }
    currentNarration = point
    DebugLogger.i("NARR-PLAY", "playNextNarration: ${point.name} (${narrationQueue.size} remaining in queue)")
    showNarrationSheet(point)
    showNarrationHighlight(point)  // S112+: glowing ring on the POI being announced

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
    refreshProximityDockFromQueue()
}

/**
 * S112: Refresh the proximity dock from the current queue snapshot.
 * Computes the same filter+sort the dequeue logic uses, then hands the result
 * to the dock so the visible dock order matches what the user is about to hear.
 *
 * Called from: every enqueue, every dequeue, every GPS position update.
 */
internal fun SalemMainActivity.refreshProximityDockFromQueue() {
    val dock = proximityDock ?: return

    if (narrationQueue.isEmpty()) {
        dock.updateFromQueue(emptyList(), lastUserLat, lastUserLng)
        return
    }

    if (lastUserLat == 0.0 && lastUserLng == 0.0) {
        // No GPS yet — show queue in insertion order, no filter
        dock.updateFromQueue(narrationQueue.toList(), 0.0, 0.0)
        return
    }

    // S112+: Same three-level density cascade the dequeue uses, plus the
    // bearing-based "ahead" filter, so the dock shows exactly the set of POIs
    // the dequeue is actually choosing from.
    val nearbyStandard = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= STANDARD_DISCARD_RADIUS_M
    }
    val nearbyHd = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= HIGH_DENSITY_DISCARD_RADIUS_M
    }
    val discardRadius = when {
        nearbyHd >= SUPER_HIGH_DENSITY_THRESHOLD -> SUPER_HIGH_DENSITY_DISCARD_RADIUS_M
        nearbyStandard >= HIGH_DENSITY_THRESHOLD -> HIGH_DENSITY_DISCARD_RADIUS_M
        else -> STANDARD_DISCARD_RADIUS_M
    }

    // Same filter+sort the dequeue would apply: in-range, ahead, tier order, closest-in-tier
    val sorted = narrationQueue
        .filter { haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= discardRadius }
        .filter { isPoiAhead(it.lat, it.lng) }
        .sortedWith(compareBy(
            { NarrationTierClassifier.classify(it).ordinal },
            { haversineM(lastUserLat, lastUserLng, it.lat, it.lng) }
        ))

    dock.updateFromQueue(sorted, lastUserLat, lastUserLng)
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
    val baseRadius = point.geofenceRadiusM.toDouble()
    // S112+: walk-sim no longer inflates the radius — same numbers as real GPS.
    val entryRadius = baseRadius
    val approachRadius = entryRadius * 2.0

    // Entry zone — filled circle
    val entryCircle = Polygon().apply {
        points = Polygon.pointsAsCircle(center, entryRadius)
        fillPaint.color = Color.argb(50, 76, 175, 80)   // green fill 20%
        outlinePaint.color = Color.argb(180, 76, 175, 80) // green outline
        outlinePaint.strokeWidth = 3f
        title = "${point.name} — entry ${entryRadius.toInt()}m"
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

// ─── S112+: Glowing highlight ring around the currently-narrated POI ─────────

/**
 * Draw a glowing gold ring at the POI being narrated. Two concentric circles
 * (inner solid, outer faded) plus a coroutine that pulses the outer ring's
 * alpha so it visibly "breathes" while the narration plays.
 *
 * Auto-replaces any prior highlight (so successive narrations move the ring
 * cleanly from one POI to the next without manual cleanup at the call site).
 */
internal fun SalemMainActivity.showNarrationHighlight(point: NarrationPoint) {
    // Tear down any existing highlight first
    clearNarrationHighlight()

    val center = GeoPoint(point.lat, point.lng)

    // Inner ring — bright solid gold, marks the POI center
    val inner = Polygon().apply {
        points = Polygon.pointsAsCircle(center, HIGHLIGHT_INNER_RADIUS_M)
        fillPaint.color = Color.argb(80, 255, 215, 0)     // gold @ ~31% alpha
        outlinePaint.color = Color.argb(255, 255, 215, 0) // gold solid stroke
        outlinePaint.strokeWidth = 6f
        title = "Now narrating: ${point.name}"
    }

    // Outer ring — faded gold, will pulse via the animation job
    val outer = Polygon().apply {
        points = Polygon.pointsAsCircle(center, HIGHLIGHT_OUTER_RADIUS_M)
        fillPaint.color = Color.argb(40, 255, 215, 0)     // gold @ ~16% alpha (animated)
        outlinePaint.color = Color.argb(180, 255, 215, 0) // gold faded stroke
        outlinePaint.strokeWidth = 3f
    }

    // Add outer first (behind), then inner (on top)
    binding.mapView.overlays.add(outer)
    binding.mapView.overlays.add(inner)
    narrationHighlightInner = inner
    narrationHighlightOuter = outer
    binding.mapView.invalidate()

    // Pulse the outer ring's alpha while narration is active.
    // 6-step cycle = 1.8 sec per pulse, alpha cycles 30 → 110 → 30.
    narrationHighlightAnimJob = lifecycleScope.launch {
        var phase = 0
        while (isActive) {
            val targetAlpha = when (phase) {
                0 -> 30
                1 -> 55
                2 -> 80
                3 -> 110
                4 -> 80
                else -> 55  // phase 5
            }
            val outerNow = narrationHighlightOuter ?: break
            outerNow.fillPaint.alpha = targetAlpha
            binding.mapView.postInvalidate()
            delay(300L)
            phase = (phase + 1) % 6
        }
    }
}

/**
 * Remove the narration highlight ring (both circles + cancel pulse animation).
 * Safe to call when no highlight is active.
 */
internal fun SalemMainActivity.clearNarrationHighlight() {
    narrationHighlightAnimJob?.cancel()
    narrationHighlightAnimJob = null
    narrationHighlightInner?.let { binding.mapView.overlays.remove(it) }
    narrationHighlightOuter?.let { binding.mapView.overlays.remove(it) }
    narrationHighlightInner = null
    narrationHighlightOuter = null
    binding.mapView.invalidate()
}
