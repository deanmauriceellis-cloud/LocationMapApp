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
import com.example.locationmapapp.core.routing.RouteResult
import com.example.locationmapapp.core.routing.RoutingLatLng
import com.example.locationmapapp.core.routing.TurnByTurn
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.FeatureFlags
import com.example.wickedsalemwitchcitytour.routing.SalemRouterProvider
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
    @ApplicationContext private val context: Context,
    private val salemRouterProvider: SalemRouterProvider,
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
        // V1 offline: route on the bundled Salem graph (S175). Falls back to a
        // straight-line route only if the bundle didn't load — otherwise we
        // get a real road-following polyline plus turn-by-turn.
        if (FeatureFlags.V1_OFFLINE_ONLY) {
            return@withContext bundleRoute(from, to) ?: straightLineRoute(from, to)
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

        // V1 offline: route each consecutive pair on the bundled Salem graph
        // (S175) and concatenate. Falls back to straight-line stitching if
        // the bundle didn't load. S179: this is now the sole tour-route
        // engine — pre-baked tour-leg geometry is no longer consulted.
        if (FeatureFlags.V1_OFFLINE_ONLY) {
            return@withContext bundleMultiRoute(waypoints) ?: straightLineMultiRoute(waypoints)
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

    /**
     * V1 offline: route on the bundled Salem graph via [SalemRouterProvider].
     * Returns null if the bundle isn't loaded or the router can't snap either
     * endpoint to a walkable node — caller is expected to fall back to a
     * straight-line route in that case.
     *
     * The router snaps both endpoints to the nearest walkable TigerLine node,
     * which can sit dozens or hundreds of metres from the user's actual GPS
     * or from a destination POI on a pier / set back from the road / inside
     * a park. We stitch a straight-line **first-approach** from [from] to the
     * route's first polyline point and a **last-approach** to [to] so the
     * drawn line visibly reaches both endpoints; distance and duration
     * include those approach segments.
     */
    private fun bundleRoute(from: GeoPoint, to: GeoPoint): WalkingRoute? {
        val router = salemRouterProvider.routerOrNull() ?: return null
        val result = router.route(from.latitude, from.longitude, to.latitude, to.longitude)
            ?: return null
        if (result.geometry.isEmpty()) return null
        return routeResultToWalkingRoute(result, leadingApproachFrom = from, trailingApproachTo = to)
    }

    private fun bundleMultiRoute(waypoints: List<GeoPoint>): WalkingRoute? {
        val router = salemRouterProvider.routerOrNull() ?: return null
        val stops = waypoints.map { RoutingLatLng(it.latitude, it.longitude) }
        val result = router.routeMulti(stops) ?: return null
        if (result.geometry.isEmpty()) return null
        return routeResultToWalkingRoute(
            result,
            leadingApproachFrom = waypoints.first(),
            trailingApproachTo = waypoints.last(),
        )
    }

    /**
     * Below this many metres we treat the snap gap as GPS noise and don't add
     * an approach segment — drawing a 3-metre line to the marker just adds
     * visual clutter.
     */
    private val APPROACH_MIN_M = 8.0

    /**
     * Above this many metres we refuse to draw a straight-line approach, because
     * it would almost certainly cross unmappable terrain (harbor, private
     * grounds, buildings) — Salem peninsula POIs like Seven Gables or Derby
     * Wharf are 200–400 m from the nearest TigerLine walkable node, and a
     * straight line from the snap point goes over open water. The polyline
     * ends at the snap node; the POI marker shows the user the remaining
     * distance honestly. A future improvement would consult the MassGIS
     * coastline polygon at bake time and stitch a piece-wise water-avoiding
     * approach when the straight line crosses ocean.
     */
    private val APPROACH_MAX_M = 60.0

    private fun routeResultToWalkingRoute(
        result: RouteResult,
        leadingApproachFrom: GeoPoint? = null,
        trailingApproachTo: GeoPoint? = null,
    ): WalkingRoute {
        val polyline = ArrayList<GeoPoint>(result.geometry.size + 2)
        for (p in result.geometry) polyline.add(GeoPoint(p.lat, p.lng))

        val paceMps = salemRouterProvider.routerOrNull()?.bundlePaceMps() ?: 1.4
        var approachLeadM = 0.0
        var approachTailM = 0.0

        if (leadingApproachFrom != null && polyline.isNotEmpty()) {
            val gapM = distanceM(leadingApproachFrom, polyline.first())
            when {
                gapM < APPROACH_MIN_M -> { /* GPS noise — ignore */ }
                gapM > APPROACH_MAX_M -> {
                    DebugLogger.w(TAG, "Lead approach skipped: ${gapM.toInt()}m gap likely crosses non-walkable terrain")
                }
                else -> {
                    polyline.add(0, leadingApproachFrom)
                    approachLeadM = gapM
                }
            }
        }
        if (trailingApproachTo != null && polyline.isNotEmpty()) {
            val gapM = distanceM(polyline.last(), trailingApproachTo)
            when {
                gapM < APPROACH_MIN_M -> { /* GPS noise — ignore */ }
                gapM > APPROACH_MAX_M -> {
                    DebugLogger.w(TAG, "Tail approach skipped: ${gapM.toInt()}m gap likely crosses non-walkable terrain")
                }
                else -> {
                    polyline.add(trailingApproachTo)
                    approachTailM = gapM
                }
            }
        }

        val totalDistanceM = result.distanceM + approachLeadM + approachTailM
        val totalDurationS = result.durationS + (approachLeadM + approachTailM) / paceMps

        val turnSteps = TurnByTurn.synthesize(result)
        val instructions = turnSteps.map { step ->
            val anchor = step.polyline.firstOrNull() ?: result.geometry.first()
            WalkingInstruction(
                text = step.instruction,
                distanceM = step.lengthM,
                durationSec = step.lengthM / paceMps,
                location = GeoPoint(anchor.lat, anchor.lng),
                maneuverType = maneuverCode(step.maneuver),
            )
        }
        DebugLogger.i(
            TAG,
            "Bundled route: %.0fm bundle + %.0fm lead + %.0fm tail = %.0fm, %.0fs, ${polyline.size} pts, ${instructions.size} turns".format(
                result.distanceM, approachLeadM, approachTailM, totalDistanceM, totalDurationS
            )
        )
        return WalkingRoute(
            polyline = polyline,
            distanceKm = totalDistanceM / 1000.0,
            durationMinutes = (totalDurationS / 60.0).toInt(),
            instructions = instructions,
            road = null,
        )
    }

    /** Map [com.example.locationmapapp.core.routing.Maneuver] to OSRM-style codes
     *  for compatibility with the existing turn-by-turn dialog. The dialog
     *  doesn't switch behavior on these — they're informational. */
    private fun maneuverCode(m: com.example.locationmapapp.core.routing.Maneuver): Int = when (m) {
        com.example.locationmapapp.core.routing.Maneuver.DEPART -> 24
        com.example.locationmapapp.core.routing.Maneuver.CONTINUE -> 1
        com.example.locationmapapp.core.routing.Maneuver.SLIGHT_LEFT -> 5
        com.example.locationmapapp.core.routing.Maneuver.LEFT -> 6
        com.example.locationmapapp.core.routing.Maneuver.SHARP_LEFT -> 7
        com.example.locationmapapp.core.routing.Maneuver.SLIGHT_RIGHT -> 2
        com.example.locationmapapp.core.routing.Maneuver.RIGHT -> 3
        com.example.locationmapapp.core.routing.Maneuver.SHARP_RIGHT -> 4
        com.example.locationmapapp.core.routing.Maneuver.U_TURN -> 8
        com.example.locationmapapp.core.routing.Maneuver.ARRIVE -> 25
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
