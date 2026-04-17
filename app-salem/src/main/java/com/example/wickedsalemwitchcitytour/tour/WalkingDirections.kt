/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.FeatureFlags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WalkingDirections.kt"

/**
 * Walking directions engine using OSRM (Open Source Routing Machine).
 *
 * Uses OSMBonusPack's OSRMRoadManager to call the public OSRM API
 * for walking routes. Returns Road objects with polyline geometry,
 * distance, duration, and turn-by-turn instructions.
 *
 * Cache: Routes are cached by start+end hash for 1 hour to avoid
 * redundant API calls during a walking session.
 */
@Singleton
class WalkingDirections @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WalkingDir"

    /** Route cache: hash(start,end) → (Road, timestamp). */
    private val routeCache = mutableMapOf<String, CachedRoute>()
    private val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour

    private data class CachedRoute(val road: Road, val timestamp: Long)

    /**
     * Calculate a walking route between two points.
     *
     * @param from start point (typically user's GPS location)
     * @param to destination point (POI location)
     * @return WalkingRoute with polyline, distance, duration, and turn instructions
     */
    suspend fun getRoute(from: GeoPoint, to: GeoPoint): WalkingRoute? = withContext(Dispatchers.IO) {
        // V1 offline: no OSRM. Return a straight-line WalkingRoute — the map
        // draws a polyline from user → destination, distance is great-circle,
        // duration assumes 1.4 m/s walking pace. No turn-by-turn.
        if (FeatureFlags.V1_OFFLINE_ONLY) {
            return@withContext straightLineRoute(from, to)
        }

        val cacheKey = "${from.latitude.hashCode()}_${from.longitude.hashCode()}_" +
                       "${to.latitude.hashCode()}_${to.longitude.hashCode()}"

        // Check cache
        routeCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                DebugLogger.d(TAG, "Cache hit for route")
                return@withContext roadToWalkingRoute(cached.road)
            }
        }

        try {
            val roadManager = OSRMRoadManager(context, "WickedSalemWitchCityTour/1.0")
            roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)

            val waypoints = arrayListOf(from, to)
            val road = roadManager.getRoad(waypoints)

            if (road.mStatus != Road.STATUS_OK) {
                DebugLogger.w(TAG, "OSRM route failed: status=${road.mStatus}")
                return@withContext null
            }

            routeCache[cacheKey] = CachedRoute(road, System.currentTimeMillis())
            DebugLogger.i(TAG, "Route: %.0fm, %.0fs, ${road.mNodes.size} nodes".format(
                road.mLength * 1000, road.mDuration
            ))

            roadToWalkingRoute(road)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Route calculation failed: ${e.message}", e)
            null
        }
    }

    /**
     * Calculate a multi-stop route (for tour preview).
     * Connects all waypoints in order.
     */
    suspend fun getMultiStopRoute(waypoints: List<GeoPoint>): WalkingRoute? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null

        // V1 offline: stitch straight-line segments between waypoints. Callers
        // wanting the richer pre-computed tour polyline should use
        // [getBundledTourRoute] with a tour_id.
        if (FeatureFlags.V1_OFFLINE_ONLY) {
            return@withContext straightLineMultiRoute(waypoints)
        }

        try {
            val roadManager = OSRMRoadManager(context, "WickedSalemWitchCityTour/1.0")
            roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)

            val road = roadManager.getRoad(ArrayList(waypoints))

            if (road.mStatus != Road.STATUS_OK) {
                DebugLogger.w(TAG, "OSRM multi-stop route failed: status=${road.mStatus}")
                return@withContext null
            }

            DebugLogger.i(TAG, "Multi-stop route: ${waypoints.size} stops, " +
                    "%.1fkm, %dmin".format(road.mLength, (road.mDuration / 60).toInt()))

            roadToWalkingRoute(road)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Multi-stop route failed: ${e.message}", e)
            null
        }
    }

    /** Convert OSRM Road to our WalkingRoute model. */
    private fun roadToWalkingRoute(road: Road): WalkingRoute {
        val instructions = road.mNodes.map { node ->
            WalkingInstruction(
                text = node.mInstructions ?: "",
                distanceM = node.mLength * 1000.0,
                durationSec = node.mDuration,
                location = node.mLocation,
                maneuverType = node.mManeuverType
            )
        }

        return WalkingRoute(
            polyline = ArrayList(road.mRouteHigh),
            distanceKm = road.mLength,
            durationMinutes = (road.mDuration / 60.0).toInt(),
            instructions = instructions,
            road = road
        )
    }

    /** Clear the route cache. */
    fun clearCache() {
        routeCache.clear()
    }

    // ── V1 offline helpers ─────────────────────────────────────────────────

    /**
     * V1 offline: load the full pre-computed tour polyline from the bundled
     * assets/tours/{tourId}.json produced by `backfill-tour-routes.js`. Stops
     * are concatenated in order via their `routeToNext` polylines. Distance
     * and duration sum the per-stop `routeDistanceM`/`routeDurationS` stamps.
     *
     * Returns null if the tour asset is missing or has no route geometry.
     */
    suspend fun getBundledTourRoute(tourId: String): WalkingRoute? = withContext(Dispatchers.IO) {
        val segments = TourRouteLoader.loadRouteSegments(context, tourId)
        if (segments.isEmpty()) {
            DebugLogger.w(TAG, "Bundled tour route missing for $tourId")
            return@withContext null
        }

        // Concatenate segment polylines, dropping the first point of every
        // segment after the first to avoid duplicates at the stop joins.
        val polyline = ArrayList<GeoPoint>()
        for ((idx, seg) in segments.withIndex()) {
            if (idx == 0 || polyline.isEmpty()) polyline.addAll(seg.points)
            else if (seg.points.isNotEmpty()) polyline.addAll(seg.points.drop(1))
        }

        val totalDistanceM = segments.sumOf { it.distanceM }.toDouble()
        val totalDurationS = segments.sumOf { it.durationS }.toDouble()

        DebugLogger.i(TAG, "Bundled tour route $tourId: ${polyline.size} points, " +
            "%.1fkm, %dmin".format(totalDistanceM / 1000.0, (totalDurationS / 60.0).toInt()))

        WalkingRoute(
            polyline = polyline,
            distanceKm = totalDistanceM / 1000.0,
            durationMinutes = (totalDurationS / 60.0).toInt(),
            instructions = emptyList(),
            road = null
        )
    }

    /**
     * Great-circle distance in meters (Haversine). Used by the V1 straight-line
     * fallback when no pre-computed route exists.
     */
    private fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return R * 2 * Math.atan2(sqrt(h), sqrt(1 - h))
    }

    /** V1 offline: straight-line route between two points, 1.4 m/s pace. */
    private fun straightLineRoute(from: GeoPoint, to: GeoPoint): WalkingRoute {
        val dM = distanceM(from, to)
        return WalkingRoute(
            polyline = arrayListOf(from, to),
            distanceKm = dM / 1000.0,
            durationMinutes = ((dM / 1.4) / 60.0).toInt(),
            instructions = emptyList(),
            road = null
        )
    }

    /** V1 offline: multi-segment straight-line route through arbitrary waypoints. */
    private fun straightLineMultiRoute(waypoints: List<GeoPoint>): WalkingRoute {
        val polyline = ArrayList(waypoints)
        var totalM = 0.0
        for (i in 1 until waypoints.size) totalM += distanceM(waypoints[i - 1], waypoints[i])
        return WalkingRoute(
            polyline = polyline,
            distanceKm = totalM / 1000.0,
            durationMinutes = ((totalM / 1.4) / 60.0).toInt(),
            instructions = emptyList(),
            road = null
        )
    }
}

/**
 * A calculated walking route with geometry and instructions.
 */
data class WalkingRoute(
    /** Full-resolution route polyline points. */
    val polyline: ArrayList<GeoPoint>,
    /** Total route distance in kilometers. */
    val distanceKm: Double,
    /** Estimated walking duration in minutes. */
    val durationMinutes: Int,
    /** Turn-by-turn walking instructions. Empty when constructed from a bundled polyline. */
    val instructions: List<WalkingInstruction>,
    /**
     * Raw OSRM Road object. Null when the route was constructed offline from
     * a bundled polyline or a straight-line fallback — the Android UI draws
     * its own [org.osmdroid.views.overlay.Polyline] from [polyline] and does
     * not rely on the Road object directly.
     */
    val road: Road? = null
)

/**
 * A single turn-by-turn instruction.
 */
data class WalkingInstruction(
    /** Human-readable instruction text. */
    val text: String,
    /** Distance in meters for this segment. */
    val distanceM: Double,
    /** Duration in seconds for this segment. */
    val durationSec: Double,
    /** Location where this instruction applies. */
    val location: GeoPoint,
    /** OSRM maneuver type code. */
    val maneuverType: Int
)
