/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.content.SharedPreferences
import com.example.wickedsalemwitchcitytour.content.model.NarrationPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.*

/**
 * Manages proximity-based geofence checks for all narration points in downtown Salem.
 * Unlike TourGeofenceManager (linear tour stops), this checks ALL loaded narration points
 * on every GPS update — the city narrates itself as you walk.
 *
 * Emits NarrationGeofenceEvent when the user approaches or enters a narration point's zone.
 * Also provides a sorted list of nearby points for the proximity dock.
 *
 * Phase 9T: Salem Walking Tour Restructure
 */
class NarrationGeofenceManager {

    private var points: List<NarrationPoint> = emptyList()

    /** Walk sim mode: expands entry radius and tightens reach-out for continuous narration */
    var walkSimMode: Boolean = false

    /** POI ID → timestamp when narrated (expires after 12 hours) */
    private val narratedAt = mutableMapOf<String, Long>()

    /** How long before a POI can repeat (ms) */
    private val REPEAT_WINDOW_MS = 12 * 60 * 60 * 1000L  // 12 hours

    /** Cooldown tracker: POI ID → last trigger timestamp */
    private val cooldowns = mutableMapOf<String, Long>()

    /** Persistent visit counts per POI (survives app restarts, drives multi-pass narration) */
    private var visitPrefs: SharedPreferences? = null

    /** Events emitted when a narration point is approached or entered */
    private val _events = MutableSharedFlow<NarrationGeofenceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NarrationGeofenceEvent> = _events

    /** Current nearby points sorted by distance (for proximity dock) */
    private var _nearby: List<NearbyPoint> = emptyList()
    val nearby: List<NearbyPoint> get() = _nearby

    /** Timestamp of the last ENTRY event emission (normal or reach-out) */
    private var lastEntryEmitTime = 0L

    /** Last known user position (for reach-out queries from outside checkPosition) */
    private var lastLat = 0.0
    private var lastLng = 0.0

    companion object {
        /** Minimum time between repeated triggers for the same POI (ms) */
        private const val COOLDOWN_MS = 120_000L  // 2 minutes

        /** Approach multiplier: trigger APPROACH at this multiple of geofence radius */
        private const val APPROACH_MULTIPLIER = 2.0

        /** Maximum narration points to include in nearby list */
        private const val MAX_NEARBY = 15

        /** Maximum distance for nearby list (meters) */
        private const val NEARBY_RADIUS_M = 300.0

        /** Minimum time inside geofence before triggering (prevents GPS drift false positives) */
        private const val DWELL_MS = 3_000L

        /** Silence threshold — reach out to nearest POI after this much quiet (ms) */
        private const val SILENCE_THRESHOLD_MS = 5_000L

        /** Maximum reach-out radius (meters) — don't narrate something far away */
        private const val REACH_RADIUS_M = 500.0
    }

    /** Initialize with context for persistent visit tracking */
    fun init(context: Context) {
        visitPrefs = context.getSharedPreferences("narration_visits", Context.MODE_PRIVATE)
    }

    /** Load narration points to monitor */
    fun loadPoints(narrationPoints: List<NarrationPoint>) {
        points = narrationPoints
        purgeExpired()
        cooldowns.clear()
    }

    /**
     * Get the narration text for a point based on how many times
     * the user has visited it on previous days.
     *
     * Pass 1 (first visit): short_narration — basic intro
     * Pass 2 (second visit): narration_pass_2 — historical deep-dive
     * Pass 3 (third visit): narration_pass_3 — primary source quotes
     * Pass 4+: cycles back to pass 1
     *
     * Falls through: if a pass has no content, uses the previous pass.
     */
    fun getNarrationForPass(point: NarrationPoint): String? {
        val visits = getVisitCount(point.id)
        val pass = (visits % 3) + 1  // 0→1, 1→2, 2→3, 3→1, ...

        return when (pass) {
            3 -> point.narrationPass3 ?: point.narrationPass2 ?: point.shortNarration
            2 -> point.narrationPass2 ?: point.shortNarration
            else -> point.shortNarration
        }
    }

    /** Record that this POI was narrated today (increments persistent visit count) */
    fun recordVisit(pointId: String) {
        val prefs = visitPrefs ?: return
        val count = prefs.getInt(pointId, 0)
        prefs.edit().putInt(pointId, count + 1).apply()
    }

    /** Get lifetime visit count for a POI */
    fun getVisitCount(pointId: String): Int {
        return visitPrefs?.getInt(pointId, 0) ?: 0
    }

    /** GPS check counter for periodic summary logging */
    private var checkCount = 0L
    private var lastSummaryTime = 0L

    /** Call on every GPS update. Returns updated nearby list for the dock. */
    fun checkPosition(lat: Double, lng: Double): List<NearbyPoint> {
        val now = System.currentTimeMillis()
        purgeExpired()
        lastLat = lat
        lastLng = lng
        checkCount++
        val nearbyList = mutableListOf<NearbyPoint>()
        var entryCount = 0
        var approachCount = 0
        var skippedNarrated = 0
        var skippedCooldown = 0

        for (point in points) {
            val distanceM = haversine(lat, lng, point.lat, point.lng)

            // Build nearby list (for proximity dock)
            if (distanceM <= NEARBY_RADIUS_M) {
                nearbyList.add(NearbyPoint(point, distanceM))
            }

            // Skip if narrated within 12-hour window
            if (isNarrated(point.id, now)) {
                if (distanceM <= point.geofenceRadiusM * (if (walkSimMode) 3.0 else 1.0)) skippedNarrated++
                continue
            }

            // Check geofence zones — walk sim mode expands radius for broader coverage
            val baseRadius = point.geofenceRadiusM.toDouble()
            val entryRadius = if (walkSimMode) baseRadius * 3.0 else baseRadius
            val approachRadius = entryRadius * APPROACH_MULTIPLIER

            when {
                // ENTRY: within geofence radius
                distanceM <= entryRadius -> {
                    if (!isOnCooldown(point.id, now)) {
                        narratedAt[point.id] = now
                        cooldowns[point.id] = now
                        lastEntryEmitTime = now
                        entryCount++
                        com.example.locationmapapp.util.DebugLogger.i("NARR-GEO",
                            "ENTRY: ${point.name} dist=${distanceM.toInt()}m radius=${entryRadius.toInt()}m walkSim=$walkSimMode")
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.ENTRY,
                            point = point,
                            distanceM = distanceM
                        ))
                    } else {
                        skippedCooldown++
                    }
                }
                // APPROACH: within 2x geofence radius
                distanceM <= approachRadius -> {
                    if (!isOnCooldown(point.id, now)) {
                        cooldowns[point.id] = now
                        approachCount++
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.APPROACH,
                            point = point,
                            distanceM = distanceM
                        ))
                    }
                }
            }
        }

        // Periodic summary every 60 seconds
        if (now - lastSummaryTime >= 60_000L) {
            lastSummaryTime = now
            val activeNarrated = narratedAt.count { now - it.value < REPEAT_WINDOW_MS }
            com.example.locationmapapp.util.DebugLogger.i("NARR-GEO",
                "SUMMARY: check#$checkCount pos=${lat.format(5)},${lng.format(5)} " +
                "nearby=${nearbyList.size} narrated=$activeNarrated/${points.size} " +
                "entries=$entryCount approaches=$approachCount " +
                "skippedNarrated=$skippedNarrated skippedCooldown=$skippedCooldown " +
                "walkSim=$walkSimMode")
        }

        // Sort by distance, limit
        _nearby = nearbyList.sortedBy { it.distanceM }.take(MAX_NEARBY)
        return _nearby
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    /**
     * Find the best un-narrated point within reach-out radius.
     * Priority order: paying merchants first, then historical importance, then distance.
     * Called when silence exceeds threshold — keeps the narration flowing.
     * Returns null if nothing available nearby.
     */
    fun findNearestUnnarrated(): NearbyPoint? {
        if (lastLat == 0.0 && lastLng == 0.0) return null

        // Keep reach-out tight — narrate what's actually nearby, not blocks away
        // Walk sim: 200m (route is predictable), Real GPS: 150m (about 1.5 blocks)
        val radius = if (walkSimMode) 200.0 else 150.0
        val now = System.currentTimeMillis()

        val candidates = mutableListOf<NearbyPoint>()
        for (point in points) {
            if (isNarrated(point.id, now)) continue
            val dist = haversine(lastLat, lastLng, point.lat, point.lng)
            if (dist <= radius) {
                candidates.add(NearbyPoint(point, dist))
            }
        }
        if (candidates.isEmpty()) return null

        // Distance-first: narrate what the user is actually near, not some distant merchant.
        // Merchant priority only breaks ties at similar distance (within 50m of each other).
        return candidates.minWithOrNull(compareBy<NearbyPoint>(
            { (it.distanceM / 50).toInt() },  // bucket by ~50m bands — nearby always wins
            { -it.point.adPriority },          // within same band: merchants first
            { it.point.priority },              // then historical importance
            { it.distanceM }                    // exact distance tiebreaker
        ))
    }

    /**
     * Trigger a reach-out narration for the given point.
     * Marks it as narrated and emits an ENTRY event.
     */
    fun triggerReachOut(nearbyPoint: NearbyPoint) {
        val now = System.currentTimeMillis()
        narratedAt[nearbyPoint.point.id] = now
        cooldowns[nearbyPoint.point.id] = now
        lastEntryEmitTime = now
        _events.tryEmit(NarrationGeofenceEvent(
            type = NarrationEventType.ENTRY,
            point = nearbyPoint.point,
            distanceM = nearbyPoint.distanceM
        ))
    }

    /** Time since last narration entry event (ms). Used by activity to detect silence. */
    fun msSinceLastEntry(): Long {
        if (lastEntryEmitTime == 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - lastEntryEmitTime
    }

    /** Reset tracking (e.g., when user starts a new walk sim) */
    fun resetSession() {
        narratedAt.clear()
        cooldowns.clear()
    }

    /** Mark a specific point as narrated (e.g., user tapped it in dock) */
    fun markTriggered(pointId: String) {
        narratedAt[pointId] = System.currentTimeMillis()
    }

    /** Check if a point has been narrated within the repeat window */
    fun isTriggered(pointId: String): Boolean = isNarrated(pointId, System.currentTimeMillis())

    /** Returns how many POIs have been narrated in the current window */
    fun narratedCount(): Int {
        val now = System.currentTimeMillis()
        return narratedAt.count { now - it.value < REPEAT_WINDOW_MS }
    }

    /** Check if a POI was narrated within the 12-hour repeat window */
    private fun isNarrated(pointId: String, now: Long): Boolean {
        val ts = narratedAt[pointId] ?: return false
        return (now - ts) < REPEAT_WINDOW_MS
    }

    /** Remove expired entries older than the repeat window */
    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        narratedAt.entries.removeIf { now - it.value >= REPEAT_WINDOW_MS }
    }

    private fun isOnCooldown(pointId: String, now: Long): Boolean {
        val lastTrigger = cooldowns[pointId] ?: return false
        return (now - lastTrigger) < COOLDOWN_MS
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Event types for narration geofence triggers */
enum class NarrationEventType {
    APPROACH,   // Within 2x geofence radius — show in dock, preview
    ENTRY       // Within geofence radius — trigger narration
}

/** Geofence event emitted when user is near a narration point */
data class NarrationGeofenceEvent(
    val type: NarrationEventType,
    val point: NarrationPoint,
    val distanceM: Double
)

/** A narration point with its current distance from the user (for proximity dock) */
data class NearbyPoint(
    val point: NarrationPoint,
    val distanceM: Double
)
