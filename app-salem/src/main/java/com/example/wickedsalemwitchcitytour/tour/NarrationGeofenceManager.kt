/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

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

    /** Set of POI IDs that have been triggered this session (no repeats) */
    private val triggeredThisSession = mutableSetOf<String>()

    /** Cooldown tracker: POI ID → last trigger timestamp */
    private val cooldowns = mutableMapOf<String, Long>()

    /** Events emitted when a narration point is approached or entered */
    private val _events = MutableSharedFlow<NarrationGeofenceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NarrationGeofenceEvent> = _events

    /** Current nearby points sorted by distance (for proximity dock) */
    private var _nearby: List<NearbyPoint> = emptyList()
    val nearby: List<NearbyPoint> get() = _nearby

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
    }

    /** Load narration points to monitor */
    fun loadPoints(narrationPoints: List<NarrationPoint>) {
        points = narrationPoints
        triggeredThisSession.clear()
        cooldowns.clear()
    }

    /** Call on every GPS update. Returns updated nearby list for the dock. */
    fun checkPosition(lat: Double, lng: Double): List<NearbyPoint> {
        val now = System.currentTimeMillis()
        val nearbyList = mutableListOf<NearbyPoint>()

        for (point in points) {
            val distanceM = haversine(lat, lng, point.lat, point.lng)

            // Build nearby list (for proximity dock)
            if (distanceM <= NEARBY_RADIUS_M) {
                nearbyList.add(NearbyPoint(point, distanceM))
            }

            // Check geofence zones
            val entryRadius = point.geofenceRadiusM.toDouble()
            val approachRadius = entryRadius * APPROACH_MULTIPLIER

            when {
                // ENTRY: within geofence radius
                distanceM <= entryRadius -> {
                    if (point.id !in triggeredThisSession && !isOnCooldown(point.id, now)) {
                        triggeredThisSession.add(point.id)
                        cooldowns[point.id] = now
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.ENTRY,
                            point = point,
                            distanceM = distanceM
                        ))
                    }
                }
                // APPROACH: within 2x geofence radius
                distanceM <= approachRadius -> {
                    if (point.id !in triggeredThisSession && !isOnCooldown(point.id, now)) {
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

    /** Reset session tracking (e.g., when user starts a new walk) */
    fun resetSession() {
        triggeredThisSession.clear()
        cooldowns.clear()
    }

    /** Mark a specific point as triggered (e.g., user tapped it in dock) */
    fun markTriggered(pointId: String) {
        triggeredThisSession.add(pointId)
    }

    /** Check if a point has been triggered this session */
    fun isTriggered(pointId: String): Boolean = pointId in triggeredThisSession

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
