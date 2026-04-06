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

    /** Set of POI IDs that have been narrated today (resets at midnight) */
    private val narratedToday = mutableSetOf<String>()

    /** The calendar day (day-of-year * 1000 + year) when narratedToday was last valid */
    private var narratedDay: Int = 0

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
        checkMidnightReset()
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

    /** Call on every GPS update. Returns updated nearby list for the dock. */
    fun checkPosition(lat: Double, lng: Double): List<NearbyPoint> {
        val now = System.currentTimeMillis()
        checkMidnightReset()
        lastLat = lat
        lastLng = lng
        val nearbyList = mutableListOf<NearbyPoint>()
        var emittedEntry = false

        for (point in points) {
            val distanceM = haversine(lat, lng, point.lat, point.lng)

            // Build nearby list (for proximity dock)
            if (distanceM <= NEARBY_RADIUS_M) {
                nearbyList.add(NearbyPoint(point, distanceM))
            }

            // Skip if already narrated today
            if (point.id in narratedToday) continue

            // Check geofence zones
            val entryRadius = point.geofenceRadiusM.toDouble()
            val approachRadius = entryRadius * APPROACH_MULTIPLIER

            when {
                // ENTRY: within geofence radius
                distanceM <= entryRadius -> {
                    if (!isOnCooldown(point.id, now)) {
                        narratedToday.add(point.id)
                        cooldowns[point.id] = now
                        lastEntryEmitTime = now
                        emittedEntry = true
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.ENTRY,
                            point = point,
                            distanceM = distanceM
                        ))
                    }
                }
                // APPROACH: within 2x geofence radius
                distanceM <= approachRadius -> {
                    if (!isOnCooldown(point.id, now)) {
                        cooldowns[point.id] = now
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.APPROACH,
                            point = point,
                            distanceM = distanceM
                        ))
                    }
                }
            }
        }

        // Sort by distance, limit
        _nearby = nearbyList.sortedBy { it.distanceM }.take(MAX_NEARBY)
        return _nearby
    }

    /**
     * Find the best un-narrated point within reach-out radius.
     * Priority order: paying merchants first, then historical importance, then distance.
     * Called when silence exceeds threshold — keeps the narration flowing.
     * Returns null if nothing available nearby.
     */
    fun findNearestUnnarrated(): NearbyPoint? {
        if (lastLat == 0.0 && lastLng == 0.0) return null

        val candidates = mutableListOf<NearbyPoint>()
        for (point in points) {
            if (point.id in narratedToday) continue
            val dist = haversine(lastLat, lastLng, point.lat, point.lng)
            if (dist <= REACH_RADIUS_M) {
                candidates.add(NearbyPoint(point, dist))
            }
        }
        if (candidates.isEmpty()) return null

        // Sort: adPriority DESC (merchants first), priority ASC (historical value), distance ASC
        return candidates.minWithOrNull(compareBy<NearbyPoint>(
            { -it.point.adPriority },    // higher ad priority = more important business
            { it.point.priority },         // lower priority number = more important historically
            { it.distanceM }               // closer is better as tiebreaker
        ))
    }

    /**
     * Trigger a reach-out narration for the given point.
     * Marks it as narrated and emits an ENTRY event.
     */
    fun triggerReachOut(nearbyPoint: NearbyPoint) {
        val now = System.currentTimeMillis()
        narratedToday.add(nearbyPoint.point.id)
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

    /** Reset daily tracking (e.g., when user starts a new walk) */
    fun resetSession() {
        narratedToday.clear()
        cooldowns.clear()
        narratedDay = todayKey()
    }

    /** Mark a specific point as narrated today (e.g., user tapped it in dock) */
    fun markTriggered(pointId: String) {
        narratedToday.add(pointId)
    }

    /** Check if a point has been narrated today */
    fun isTriggered(pointId: String): Boolean = pointId in narratedToday

    /** Returns how many POIs have been narrated today */
    fun narratedCount(): Int = narratedToday.size

    /** Reset narrated set if the calendar day has changed (midnight rollover) */
    private fun checkMidnightReset() {
        val today = todayKey()
        if (today != narratedDay) {
            narratedToday.clear()
            cooldowns.clear()
            narratedDay = today
        }
    }

    private fun todayKey(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
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
