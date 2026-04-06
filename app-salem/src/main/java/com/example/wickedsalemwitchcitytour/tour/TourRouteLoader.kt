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
import com.google.gson.JsonParser
import org.osmdroid.util.GeoPoint

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TourRouteLoader.kt"

/**
 * Reads pre-computed OSRM route geometry from tour JSON asset files.
 * Returns a list of polyline segments (one per stop→next pair, including loop-back).
 */
object TourRouteLoader {

    private const val TAG = "TourRouteLoader"

    /** A single route segment from one stop to the next. */
    data class RouteSegment(
        val fromStopOrder: Int,
        val toStopOrder: Int,
        val points: List<GeoPoint>,
        val distanceM: Int,
        val durationS: Int
    )

    /**
     * Load all route segments for a tour from its JSON asset file.
     * Returns empty list if the tour has no pre-computed routes.
     */
    fun loadRouteSegments(context: Context, tourId: String): List<RouteSegment> {
        val filename = "tours/$tourId.json"
        return try {
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            parseRouteSegments(json)
        } catch (e: Exception) {
            DebugLogger.w(TAG, "No route data for $tourId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Load all route points as a single flat list (for walk simulation).
     * If the tour has pre-computed OSRM geometry, points flow through each segment.
     * Otherwise, falls back to the stop lat/lng coordinates directly.
     */
    fun loadAllRoutePoints(context: Context, tourId: String): List<GeoPoint> {
        val segments = loadRouteSegments(context, tourId)
        if (segments.isNotEmpty()) {
            val allPoints = mutableListOf<GeoPoint>()
            for (seg in segments) {
                if (allPoints.isEmpty()) {
                    allPoints.addAll(seg.points)
                } else if (seg.points.isNotEmpty()) {
                    allPoints.addAll(seg.points.drop(1))
                }
            }
            return allPoints
        }
        // Fallback: use stop coordinates as waypoints
        return loadStopCoordinates(context, tourId)
    }

    /**
     * Load stop lat/lng coordinates from the tour JSON (no route geometry needed).
     */
    private fun loadStopCoordinates(context: Context, tourId: String): List<GeoPoint> {
        val filename = "tours/$tourId.json"
        return try {
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(json).asJsonObject
            val stops = root.getAsJsonArray("stops") ?: return emptyList()
            val points = stops.map { s ->
                val obj = s.asJsonObject
                GeoPoint(obj.get("lat").asDouble, obj.get("lng").asDouble)
            }
            DebugLogger.i(TAG, "Loaded ${points.size} stop coordinates for $tourId (no route geometry)")
            points
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to load stop coordinates for $tourId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Load the downtown Salem street-level walking route (OSRM-generated).
     * This is the "Walking Through Salem" route — starts at PEM, loops through
     * Essex St, Salem Common, Liberty, Charter, Derby waterfront, and back.
     */
    fun loadDowntownRoute(context: Context): List<GeoPoint> {
        val filename = "tours/downtown_salem_route.json"
        return try {
            val json = context.assets.open(filename).bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(json).asJsonObject
            val pointsArray = root.getAsJsonArray("points") ?: return emptyList()
            val points = pointsArray.map { coord ->
                val arr = coord.asJsonArray
                GeoPoint(arr[0].asDouble, arr[1].asDouble)
            }
            DebugLogger.i(TAG, "Loaded downtown Salem route: ${points.size} points")
            points
        } catch (e: Exception) {
            DebugLogger.w(TAG, "No downtown route: ${e.message}")
            emptyList()
        }
    }

    private fun parseRouteSegments(json: String): List<RouteSegment> {
        val root = JsonParser.parseString(json).asJsonObject
        val stops = root.getAsJsonArray("stops") ?: return emptyList()
        val segments = mutableListOf<RouteSegment>()

        for (i in 0 until stops.size()) {
            val stop = stops[i].asJsonObject
            val routeArray = stop.getAsJsonArray("routeToNext") ?: continue
            val order = stop.get("order").asInt
            val nextOrder = if (i < stops.size() - 1) stops[i + 1].asJsonObject.get("order").asInt else 1

            val points = routeArray.map { coord ->
                val arr = coord.asJsonArray
                GeoPoint(arr[0].asDouble, arr[1].asDouble)
            }

            val distanceM = stop.get("routeDistanceM")?.asInt ?: 0
            val durationS = stop.get("routeDurationS")?.asInt ?: 0

            segments.add(RouteSegment(order, nextOrder, points, distanceM, durationS))
        }

        DebugLogger.i(TAG, "Loaded ${segments.size} route segments, ${segments.sumOf { it.points.size }} total points")
        return segments
    }
}
