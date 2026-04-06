/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

import kotlin.math.*

/**
 * A street corridor geofence — a polyline with a buffer distance.
 * Triggers when the user walks within [bufferM] meters of any segment.
 * Unlike circular geofences, corridors follow the street centerline.
 *
 * Phase 9T Step 5: Street Corridor Geofences
 */
data class CorridorGeofence(
    val id: String,
    val name: String,
    val narrationPointId: String,
    /** Ordered list of [lat, lng] pairs defining the street centerline */
    val points: List<Pair<Double, Double>>,
    /** Buffer distance in meters (each side of centerline) */
    val bufferM: Double = 20.0
) {
    /**
     * Check if a position is within the corridor buffer.
     * @return distance to nearest segment in meters, or null if outside buffer
     */
    fun distanceTo(lat: Double, lng: Double): Double? {
        if (points.size < 2) return null
        var minDist = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val (lat1, lng1) = points[i]
            val (lat2, lng2) = points[i + 1]
            val dist = pointToSegmentDistance(lat, lng, lat1, lng1, lat2, lng2)
            if (dist < minDist) minDist = dist
        }
        return if (minDist <= bufferM) minDist else null
    }

    /**
     * Distance from a point to a line segment (in meters).
     * Projects the point onto the segment and computes haversine distance.
     */
    private fun pointToSegmentDistance(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Double {
        // Convert to meters relative to segment start for projection
        val dxAB = (bLng - aLng) * cos(Math.toRadians((aLat + bLat) / 2)) * 111_320.0
        val dyAB = (bLat - aLat) * 111_320.0
        val dxAP = (pLng - aLng) * cos(Math.toRadians((aLat + pLat) / 2)) * 111_320.0
        val dyAP = (pLat - aLat) * 111_320.0

        val segLenSq = dxAB * dxAB + dyAB * dyAB
        if (segLenSq < 0.001) {
            // Degenerate segment — just distance to point A
            return haversine(pLat, pLng, aLat, aLng)
        }

        // Project point onto segment, clamped to [0,1]
        val t = ((dxAP * dxAB + dyAP * dyAB) / segLenSq).coerceIn(0.0, 1.0)

        // Closest point on segment
        val closestLat = aLat + t * (bLat - aLat)
        val closestLng = aLng + t * (bLng - aLng)

        return haversine(pLat, pLng, closestLat, closestLng)
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

/**
 * Manages corridor geofences for historically significant streets.
 * Corridors fire once per walk session — not every time you cross the street.
 */
class CorridorGeofenceManager {

    private var corridors: List<CorridorGeofence> = emptyList()
    private val triggeredThisSession = mutableSetOf<String>()

    fun loadCorridors(list: List<CorridorGeofence>) {
        corridors = list
        triggeredThisSession.clear()
    }

    /**
     * Check if the user is within any corridor.
     * @return list of triggered corridor events (may be empty)
     */
    fun checkPosition(lat: Double, lng: Double): List<CorridorTrigger> {
        val triggers = mutableListOf<CorridorTrigger>()
        for (corridor in corridors) {
            if (corridor.id in triggeredThisSession) continue
            val dist = corridor.distanceTo(lat, lng)
            if (dist != null) {
                triggeredThisSession.add(corridor.id)
                triggers.add(CorridorTrigger(corridor, dist))
            }
        }
        return triggers
    }

    fun resetSession() {
        triggeredThisSession.clear()
    }

    fun isTriggered(corridorId: String): Boolean = corridorId in triggeredThisSession
}

data class CorridorTrigger(
    val corridor: CorridorGeofence,
    val distanceM: Double
)
