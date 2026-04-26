/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.routing

import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module DirectionsSession.kt"

/**
 * State for an active "Walk Here" session (S176 P3b).
 *
 * Owned by `TourViewModel`. Immutable: a new instance is created each time
 * the route is recomputed. The session inspects each GPS fix and tells the
 * caller what to do — keep going, recompute, or arrive.
 *
 * Drift logic (per S175 carry-forward):
 *   - User must be more than [DRIFT_THRESHOLD_M] from the polyline,
 *   - For at least [DRIFT_FIXES] consecutive fixes,
 *   - Before a reroute is triggered.
 *
 * Arrival fires when the user is within [ARRIVAL_M] of the destination.
 * Arrival is checked before drift so the natural last-segment shortcut
 * across a sidewalk → driveway → door does not produce a reroute loop.
 */
class DirectionsSession(
    val destination: GeoPoint,
    val polyline: List<GeoPoint>,
) {

    private var driftCount: Int = 0

    /** Last computed cross-track distance to polyline, in meters. */
    var lastDriftM: Double = 0.0
        private set

    /**
     * Feed a new GPS fix.
     *
     * @return action the caller should take
     */
    fun onLocation(point: GeoPoint): SessionAction {
        if (polyline.isEmpty()) return SessionAction.OnPath

        val toDestM = haversineM(point, destination)
        if (toDestM <= ARRIVAL_M) return SessionAction.Arrived

        val drift = distanceToPolyline(point, polyline)
        lastDriftM = drift

        if (drift > DRIFT_THRESHOLD_M) {
            driftCount++
            if (driftCount >= DRIFT_FIXES) {
                driftCount = 0
                return SessionAction.Reroute(point, drift)
            }
        } else {
            driftCount = 0
        }
        return SessionAction.OnPath
    }

    sealed interface SessionAction {
        object OnPath : SessionAction
        data class Reroute(val from: GeoPoint, val driftM: Double) : SessionAction
        object Arrived : SessionAction
    }

    companion object {
        const val DRIFT_THRESHOLD_M: Double = 25.0
        const val DRIFT_FIXES: Int = 2
        const val ARRIVAL_M: Double = 15.0
    }
}

// ── geometry helpers (internal so unit tests can hit them) ───────────────────

/** Minimum distance from [p] to any segment of [poly], in meters. */
internal fun distanceToPolyline(p: GeoPoint, poly: List<GeoPoint>): Double {
    if (poly.isEmpty()) return Double.POSITIVE_INFINITY
    if (poly.size == 1) return haversineM(p, poly[0])
    var min = Double.POSITIVE_INFINITY
    for (i in 0 until poly.size - 1) {
        val d = pointToSegmentM(p, poly[i], poly[i + 1])
        if (d < min) min = d
    }
    return min
}

/**
 * Point-to-segment distance using a local equirectangular projection around
 * the segment midpoint. Fine for distances well under a kilometer — at Salem
 * latitude the per-degree error vs. true geodesic is below 0.1% for the
 * scales we deal with (segment length ≪ 1 km).
 */
internal fun pointToSegmentM(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    val midLatRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
    val mPerDegLat = 111_320.0
    val mPerDegLng = 111_320.0 * cos(midLatRad)

    val px = (p.longitude - a.longitude) * mPerDegLng
    val py = (p.latitude - a.latitude) * mPerDegLat
    val bx = (b.longitude - a.longitude) * mPerDegLng
    val by = (b.latitude - a.latitude) * mPerDegLat

    val segLenSq = bx * bx + by * by
    if (segLenSq == 0.0) return sqrt(px * px + py * py)
    val t = ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
    val cx = t * bx
    val cy = t * by
    val dx = px - cx
    val dy = py - cy
    return sqrt(dx * dx + dy * dy)
}

/** Great-circle distance in meters. */
internal fun haversineM(a: GeoPoint, b: GeoPoint): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
            sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(h), sqrt(1 - h))
}
