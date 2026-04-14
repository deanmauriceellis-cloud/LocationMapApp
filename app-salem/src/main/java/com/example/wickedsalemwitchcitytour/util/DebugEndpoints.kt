/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.util

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.example.wickedsalemwitchcitytour.ui.SalemMainActivity
import com.example.wickedsalemwitchcitytour.ui.MainViewModel
import com.example.wickedsalemwitchcitytour.ui.AircraftViewModel
import com.example.wickedsalemwitchcitytour.ui.GeofenceViewModel
import com.example.wickedsalemwitchcitytour.ui.TransitViewModel
import com.example.wickedsalemwitchcitytour.ui.WeatherViewModel
import com.example.wickedsalemwitchcitytour.ui.debugBusStops
import com.example.wickedsalemwitchcitytour.ui.debugFollowAircraft
import com.example.wickedsalemwitchcitytour.ui.debugFollowVehicleByIndex
import com.example.wickedsalemwitchcitytour.ui.debugMapView
import com.example.wickedsalemwitchcitytour.ui.debugMarkers
import com.example.wickedsalemwitchcitytour.ui.debugRawMarkers
import com.example.wickedsalemwitchcitytour.ui.debugRefreshLayer
import com.example.wickedsalemwitchcitytour.ui.debugStations
import com.example.wickedsalemwitchcitytour.ui.debugState
import com.example.wickedsalemwitchcitytour.ui.debugStopFollow
import com.example.wickedsalemwitchcitytour.ui.debugTapMarker
import com.example.wickedsalemwitchcitytour.ui.debugTogglePref
import com.example.wickedsalemwitchcitytour.ui.debugVehicles
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.tour.TourRouteLoader
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.osmdroid.util.GeoPoint
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module DebugEndpoints.kt"

/** Response from an endpoint handler. */
data class EndpointResult(
    val status: Int = 200,
    val contentType: String = "application/json",
    val body: String = "",
    val bodyBytes: ByteArray? = null
)

/**
 * DebugEndpoints — all HTTP endpoint handlers for the debug server.
 * Holds references to SalemMainActivity and MainViewModel.
 */
class DebugEndpoints(
    private val activity: SalemMainActivity,
    private val viewModel: MainViewModel,
    private val transitViewModel: TransitViewModel,
    private val aircraftViewModel: AircraftViewModel,
    private val weatherViewModel: WeatherViewModel,
    private val geofenceViewModel: GeofenceViewModel
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private var walkJob: Job? = null
    private val walkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun handle(method: String, path: String, params: Map<String, String>): EndpointResult {
        return when (path) {
            "/"                 -> handleIndex()
            "/state"            -> handleState()
            "/logs"             -> handleLogs(params)
            "/logs/clear"       -> handleLogsClear()
            "/map"              -> handleMap(params)
            "/markers"          -> handleMarkers(params)
            "/markers/tap"      -> handleMarkerTap(params)
            "/markers/nearest"  -> handleMarkersNearest(params)
            "/markers/search"   -> handleMarkersSearch(params)
            "/vehicles"         -> handleVehicles(params)
            "/stations"         -> handleStations(params)
            "/bus-stops"        -> handleBusStops(params)
            "/screenshot"       -> handleScreenshot()
            "/livedata"         -> handleLiveData()
            "/prefs"            -> handlePrefs()
            "/toggle"           -> handleToggle(params)
            "/search"           -> handleSearch(params)
            "/refresh"          -> handleRefresh(params)
            "/follow"           -> handleFollow(params)
            "/stop-follow"      -> handleStopFollow()
            "/perf"             -> handlePerf()
            "/overlays"         -> handleOverlays()
            "/geofences"        -> handleGeofences()
            "/geofences/alerts" -> handleGeofenceAlerts()
            "/walk-tour"        -> handleWalkTour(params)
            "/walk-stop"        -> handleWalkStop()
            else                -> EndpointResult(404, body = gson.toJson(mapOf("error" to "Not found: $path")))
        }
    }

    // ── / — endpoint listing ────────────────────────────────────────────────

    private fun handleIndex(): EndpointResult {
        val endpoints = listOf(
            mapOf("path" to "/",                "method" to "GET", "description" to "List all endpoints"),
            mapOf("path" to "/state",           "method" to "GET", "description" to "Map center, zoom, viewport bounds, marker counts, follow state"),
            mapOf("path" to "/logs",            "method" to "GET", "description" to "Debug log entries. Params: tail=N, filter=X, level=E"),
            mapOf("path" to "/logs/clear",      "method" to "GET", "description" to "Clear log buffer"),
            mapOf("path" to "/map",             "method" to "GET", "description" to "Set map center+zoom. Params: lat=X, lon=Y, zoom=Z (no params = read-only)"),
            mapOf("path" to "/markers",         "method" to "GET", "description" to "List markers. Params: type=X, limit=N. Types: poi, stations, trains, subway, buses, aircraft, webcams, metar, gps"),
            mapOf("path" to "/markers/tap",     "method" to "GET", "description" to "Trigger marker click. Params: type=X, index=N"),
            mapOf("path" to "/markers/nearest", "method" to "GET", "description" to "Find nearest marker(s). Params: lat=X, lon=Y, type=X, limit=N"),
            mapOf("path" to "/markers/search",  "method" to "GET", "description" to "Search markers by title/snippet. Params: q=X, type=X, limit=N"),
            mapOf("path" to "/vehicles",        "method" to "GET", "description" to "Raw vehicle data from LiveData. Params: type=trains|subway|buses, limit=N, index=N"),
            mapOf("path" to "/stations",        "method" to "GET", "description" to "Raw station data from LiveData. Params: limit=N, q=X (search by name)"),
            mapOf("path" to "/bus-stops",       "method" to "GET", "description" to "All cached bus stops. Params: limit=N, q=X (search by name)"),
            mapOf("path" to "/screenshot",      "method" to "GET", "description" to "Returns PNG of root view"),
            mapOf("path" to "/livedata",        "method" to "GET", "description" to "Current values of all ViewModel LiveData"),
            mapOf("path" to "/prefs",           "method" to "GET", "description" to "Dump SharedPreferences"),
            mapOf("path" to "/toggle",          "method" to "GET", "description" to "Toggle a pref. Params: pref=X, value=true|false"),
            mapOf("path" to "/search",          "method" to "GET", "description" to "Trigger POI search at a point. Params: lat=X, lon=Y"),
            mapOf("path" to "/refresh",         "method" to "GET", "description" to "Force refresh a layer. Params: layer=trains|subway|buses|stations|aircraft|metar|webcams|pois|radar"),
            mapOf("path" to "/follow",          "method" to "GET", "description" to "Follow a marker. Params: type=aircraft&icao=X, or type=stations|trains|subway|buses&index=N"),
            mapOf("path" to "/stop-follow",     "method" to "GET", "description" to "Stop following any vehicle or aircraft"),
            mapOf("path" to "/perf",            "method" to "GET", "description" to "Performance stats: memory, threads, GC info"),
            mapOf("path" to "/overlays",        "method" to "GET", "description" to "List all map overlays with types and counts"),
            mapOf("path" to "/geofences",       "method" to "GET", "description" to "List all loaded geofence zones (TFR, cameras, schools, floods, crossings)"),
            mapOf("path" to "/geofences/alerts", "method" to "GET", "description" to "Active geofence alerts with zone type"),
            mapOf("path" to "/walk-tour",        "method" to "GET", "description" to "Simulate walking a tour route. Params: tour=tour_witch_trials, speed=1.4 (m/s)"),
            mapOf("path" to "/walk-stop",        "method" to "GET", "description" to "Stop the walking simulation"),
        )
        return EndpointResult(body = gson.toJson(mapOf("endpoints" to endpoints)))
    }

    // ── Fuzzy search helper ─────────────────────────────────────────────────

    /** Common abbreviation → expansion map for transit stop names. */
    private val ABBREVIATIONS = mapOf(
        "mass" to "massachusetts", "ave" to "avenue", "st" to "street",
        "sq" to "square", "ctr" to "center", "ctr." to "center",
        "dr" to "drive", "rd" to "road", "blvd" to "boulevard",
        "hwy" to "highway", "pkwy" to "parkway", "pl" to "place",
        "ln" to "lane", "ct" to "court", "mt" to "mount",
        "jfk" to "kennedy", "govt" to "government"
    )

    /**
     * Fuzzy name match: splits query into words, expands abbreviations,
     * and checks that every word appears somewhere in the target (case-insensitive).
     */
    private fun fuzzyMatch(target: String, query: String): Boolean {
        val tLower = target.lowercase()
        return query.lowercase().split("\\s+".toRegex()).all { word ->
            val expanded = ABBREVIATIONS[word]
            tLower.contains(word) || (expanded != null && tLower.contains(expanded))
        }
    }

    // ── /state ──────────────────────────────────────────────────────────────

    private suspend fun handleState(): EndpointResult {
        val state = runOnMain { activity.debugState() }
        return EndpointResult(body = gson.toJson(state))
    }

    // ── /logs ───────────────────────────────────────────────────────────────

    private fun handleLogs(params: Map<String, String>): EndpointResult {
        var logs = DebugLogger.getAll()
        val filter = params["filter"]
        if (filter != null) {
            logs = logs.filter { it.contains(filter, ignoreCase = true) }
        }
        val level = params["level"]
        if (level != null) {
            val prefix = "$level/"
            logs = logs.filter { it.contains(prefix) }
        }
        val tail = params["tail"]?.toIntOrNull()
        if (tail != null && tail < logs.size) {
            logs = logs.takeLast(tail)
        }
        return EndpointResult(body = gson.toJson(mapOf("count" to logs.size, "logs" to logs)))
    }

    // ── /logs/clear ─────────────────────────────────────────────────────────

    private fun handleLogsClear(): EndpointResult {
        DebugLogger.clear()
        return EndpointResult(body = gson.toJson(mapOf("status" to "cleared")))
    }

    // ── /map ────────────────────────────────────────────────────────────────

    private suspend fun handleMap(params: Map<String, String>): EndpointResult {
        val lat = params["lat"]?.toDoubleOrNull()
        val lon = params["lon"]?.toDoubleOrNull()
        val zoom = params["zoom"]?.toDoubleOrNull()

        if (lat == null && lon == null && zoom == null) {
            // Read-only: return current map state
            val state = runOnMain { activity.debugState() }
            return EndpointResult(body = gson.toJson(state))
        }

        val result = runOnMain {
            val mapView = activity.debugMapView()
            if (lat != null && lon != null) {
                mapView.controller.animateTo(org.osmdroid.util.GeoPoint(lat, lon))
            }
            if (zoom != null) {
                mapView.controller.setZoom(zoom.coerceIn(mapView.minZoomLevel, mapView.maxZoomLevel))
            }
            mapOf(
                "status" to "ok",
                "center" to mapOf(
                    "lat" to (lat ?: mapView.mapCenter.latitude),
                    "lon" to (lon ?: mapView.mapCenter.longitude)
                ),
                "zoom" to (zoom ?: mapView.zoomLevelDouble)
            )
        }
        return EndpointResult(body = gson.toJson(result))
    }

    // ── /markers ────────────────────────────────────────────────────────────

    private suspend fun handleMarkers(params: Map<String, String>): EndpointResult {
        val type = params["type"]
        val limit = params["limit"]?.toIntOrNull() ?: 100

        val markers = runOnMain { activity.debugMarkers(type) }
        val truncated = markers.take(limit)
        return EndpointResult(body = gson.toJson(mapOf(
            "type" to (type ?: "all"),
            "total" to markers.size,
            "returned" to truncated.size,
            "markers" to truncated
        )))
    }

    // ── /markers/tap ────────────────────────────────────────────────────────

    private suspend fun handleMarkerTap(params: Map<String, String>): EndpointResult {
        val type = params["type"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'type' param")))
        val index = params["index"]?.toIntOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'index' param")))

        val result = runOnMain {
            val rawMarkers = activity.debugRawMarkers(type)
            if (index < 0 || index >= rawMarkers.size) {
                mapOf("error" to "Index $index out of range (0..${rawMarkers.size - 1})")
            } else {
                val marker = rawMarkers[index]
                activity.debugTapMarker(marker)
                mapOf(
                    "status" to "tapped",
                    "type" to type,
                    "index" to index,
                    "title" to (marker.title ?: ""),
                    "position" to mapOf("lat" to marker.position.latitude, "lon" to marker.position.longitude)
                )
            }
        }
        return EndpointResult(body = gson.toJson(result))
    }

    // ── /markers/nearest ────────────────────────────────────────────────────

    private suspend fun handleMarkersNearest(params: Map<String, String>): EndpointResult {
        val lat = params["lat"]?.toDoubleOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'lat'")))
        val lon = params["lon"]?.toDoubleOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'lon'")))
        val type = params["type"]
        val target = org.osmdroid.util.GeoPoint(lat, lon)

        val markers = runOnMain { activity.debugMarkers(type) }
        val sorted = markers.map { m ->
            val dist = target.distanceToAsDouble(org.osmdroid.util.GeoPoint(
                (m["lat"] as? Number)?.toDouble() ?: 0.0,
                (m["lon"] as? Number)?.toDouble() ?: 0.0
            ))
            m.toMutableMap().also { it["distance_m"] = dist }
        }.sortedBy { (it["distance_m"] as? Number)?.toDouble() ?: Double.MAX_VALUE }

        val limit = params["limit"]?.toIntOrNull() ?: 5
        return EndpointResult(body = gson.toJson(mapOf(
            "target" to mapOf("lat" to lat, "lon" to lon),
            "type" to (type ?: "all"),
            "nearest" to sorted.take(limit)
        )))
    }

    // ── /screenshot ─────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private suspend fun handleScreenshot(): EndpointResult {
        val pngBytes = runOnMain {
            val rootView = activity.window.decorView.rootView
            rootView.isDrawingCacheEnabled = true
            rootView.buildDrawingCache()
            val bitmap = Bitmap.createBitmap(rootView.drawingCache)
            rootView.isDrawingCacheEnabled = false
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            bitmap.recycle()
            baos.toByteArray()
        }
        return EndpointResult(contentType = "image/png", bodyBytes = pngBytes)
    }

    // ── /livedata ───────────────────────────────────────────────────────────

    private suspend fun handleLiveData(): EndpointResult {
        val data = runOnMain {
            mapOf(
                "currentLocation" to viewModel.currentLocation.value?.let {
                    mapOf("lat" to it.point.latitude, "lon" to it.point.longitude,
                        "speedMps" to it.speedMps, "bearing" to it.bearing, "accuracy" to it.accuracy)
                },
                "locationMode" to viewModel.locationMode.value?.name,
                "places" to viewModel.places.value?.let {
                    mapOf("layerId" to it.first, "count" to it.second.size)
                },
                "mbtaTrains" to (transitViewModel.mbtaTrains.value?.size ?: 0),
                "mbtaSubway" to (transitViewModel.mbtaSubway.value?.size ?: 0),
                "mbtaBuses" to (transitViewModel.mbtaBuses.value?.size ?: 0),
                "mbtaStations" to (transitViewModel.mbtaStations.value?.size ?: 0),
                "aircraft" to (aircraftViewModel.aircraft.value?.size ?: 0),
                "followedAircraft" to aircraftViewModel.followedAircraft.value?.let {
                    mapOf("icao24" to it.icao24, "callsign" to it.callsign, "lat" to it.lat, "lon" to it.lon)
                },
                "webcams" to (weatherViewModel.webcams.value?.size ?: 0),
                "metars" to (weatherViewModel.metars.value?.size ?: 0),
                "weatherAlerts" to (weatherViewModel.weatherAlerts.value?.size ?: 0),
                "error" to viewModel.error.value
            )
        }
        return EndpointResult(body = gson.toJson(data))
    }

    // ── /prefs ──────────────────────────────────────────────────────────────

    private suspend fun handlePrefs(): EndpointResult {
        val allPrefs = runOnMain {
            val prefs = activity.getSharedPreferences("app_bar_menu_prefs", android.content.Context.MODE_PRIVATE)
            val map = mutableMapOf<String, Any?>()
            prefs.all.forEach { (k, v) -> map[k] = v }
            map
        }
        return EndpointResult(body = gson.toJson(allPrefs))
    }

    // ── /markers/search ─────────────────────────────────────────────────────

    private suspend fun handleMarkersSearch(params: Map<String, String>): EndpointResult {
        val query = params["q"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'q' param")))
        val type = params["type"]
        val limit = params["limit"]?.toIntOrNull() ?: 50

        val markers = runOnMain { activity.debugMarkers(type) }
        val matched = markers.filter { m ->
            val title = (m["title"] as? String) ?: ""
            val snippet = (m["snippet"] as? String) ?: ""
            fuzzyMatch(title, query) || fuzzyMatch(snippet, query)
        }
        return EndpointResult(body = gson.toJson(mapOf(
            "query" to query,
            "type" to (type ?: "all"),
            "total" to matched.size,
            "returned" to minOf(matched.size, limit),
            "markers" to matched.take(limit)
        )))
    }

    // ── /vehicles ─────────────────────────────────────────────────────────────

    private suspend fun handleVehicles(params: Map<String, String>): EndpointResult {
        val type = params["type"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'type' param (trains|subway|buses)")))
        val limit = params["limit"]?.toIntOrNull() ?: 500
        val index = params["index"]?.toIntOrNull()

        val vehicles = runOnMain { activity.debugVehicles(type) }

        if (index != null) {
            if (index < 0 || index >= vehicles.size) {
                return EndpointResult(400, body = gson.toJson(mapOf("error" to "Index $index out of range (0..${vehicles.size - 1})")))
            }
            return EndpointResult(body = gson.toJson(serializeVehicle(vehicles[index], index)))
        }

        val truncated = vehicles.take(limit)
        return EndpointResult(body = gson.toJson(mapOf(
            "type" to type,
            "total" to vehicles.size,
            "returned" to truncated.size,
            "vehicles" to truncated.mapIndexed { i, v -> serializeVehicle(v, i) }
        )))
    }

    private fun serializeVehicle(v: com.example.locationmapapp.data.model.MbtaVehicle, index: Int): Map<String, Any?> {
        return mapOf(
            "index" to index,
            "id" to v.id,
            "label" to v.label,
            "routeId" to v.routeId,
            "routeName" to v.routeName,
            "tripId" to v.tripId,
            "headsign" to v.headsign,
            "stopId" to v.stopId,
            "stopName" to v.stopName,
            "lat" to v.lat,
            "lon" to v.lon,
            "bearing" to v.bearing,
            "speedMps" to v.speedMps,
            "speedMph" to v.speedMph,
            "currentStatus" to v.currentStatus.display,
            "updatedAt" to v.updatedAt,
            "routeType" to v.routeType,
            "nextStopMinutes" to v.nextStopMinutes
        )
    }

    // ── /stations ────────────────────────────────────────────────────────────

    private suspend fun handleStations(params: Map<String, String>): EndpointResult {
        val limit = params["limit"]?.toIntOrNull() ?: 500
        val query = params["q"]

        var stations = runOnMain { activity.debugStations() }
        if (query != null) {
            stations = stations.filter { fuzzyMatch(it.name, query) }
        }

        val truncated = stations.take(limit)
        return EndpointResult(body = gson.toJson(mapOf(
            "total" to stations.size,
            "returned" to truncated.size,
            "stations" to truncated.map { s ->
                mapOf(
                    "id" to s.id,
                    "name" to s.name,
                    "lat" to s.lat,
                    "lon" to s.lon,
                    "routeIds" to s.routeIds
                )
            }
        )))
    }

    // ── /bus-stops ───────────────────────────────────────────────────────────

    private suspend fun handleBusStops(params: Map<String, String>): EndpointResult {
        val limit = params["limit"]?.toIntOrNull() ?: 500
        val query = params["q"]

        var stops = runOnMain { activity.debugBusStops() }
        if (query != null) {
            stops = stops.filter { fuzzyMatch(it.name, query) }
        }

        val truncated = stops.take(limit)
        return EndpointResult(body = gson.toJson(mapOf(
            "total" to stops.size,
            "returned" to truncated.size,
            "busStops" to truncated.map { s ->
                mapOf(
                    "id" to s.id,
                    "name" to s.name,
                    "lat" to s.lat,
                    "lon" to s.lon,
                    "routeIds" to s.routeIds
                )
            }
        )))
    }

    // ── /toggle ─────────────────────────────────────────────────────────────

    private suspend fun handleToggle(params: Map<String, String>): EndpointResult {
        val pref = params["pref"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'pref' param")))
        val value = params["value"]?.toBooleanStrictOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing or invalid 'value' param (true/false)")))

        runOnMain { activity.debugTogglePref(pref, value) }
        return EndpointResult(body = gson.toJson(mapOf(
            "status" to "ok",
            "pref" to pref,
            "value" to value
        )))
    }

    // ── /search ─────────────────────────────────────────────────────────────

    private suspend fun handleSearch(params: Map<String, String>): EndpointResult {
        val lat = params["lat"]?.toDoubleOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'lat'")))
        val lon = params["lon"]?.toDoubleOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'lon'")))

        runOnMain {
            val point = org.osmdroid.util.GeoPoint(lat, lon)
            viewModel.searchPoisAt(point)
        }
        return EndpointResult(body = gson.toJson(mapOf(
            "status" to "ok",
            "message" to "POI search triggered",
            "lat" to lat,
            "lon" to lon
        )))
    }

    // ── /refresh ────────────────────────────────────────────────────────────

    private suspend fun handleRefresh(params: Map<String, String>): EndpointResult {
        val layer = params["layer"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'layer' param")))

        runOnMain { activity.debugRefreshLayer(layer) }
        return EndpointResult(body = gson.toJson(mapOf(
            "status" to "ok",
            "layer" to layer,
            "message" to "Refresh triggered for $layer"
        )))
    }

    // ── /follow ─────────────────────────────────────────────────────────────

    private suspend fun handleFollow(params: Map<String, String>): EndpointResult {
        val type = params["type"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'type' param")))

        return when (type) {
            "aircraft" -> {
                val icao = params["icao"] ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'icao' param for aircraft follow")))
                runOnMain { activity.debugFollowAircraft(icao) }
                EndpointResult(body = gson.toJson(mapOf("status" to "ok", "following" to "aircraft", "icao" to icao)))
            }
            "trains", "subway", "buses" -> {
                val index = params["index"]?.toIntOrNull() ?: return EndpointResult(400, body = gson.toJson(mapOf("error" to "Missing 'index' param")))
                val result = runOnMain { activity.debugFollowVehicleByIndex(type, index) }
                EndpointResult(body = gson.toJson(result))
            }
            else -> EndpointResult(400, body = gson.toJson(mapOf("error" to "Unknown follow type '$type'. Use: aircraft, trains, subway, buses")))
        }
    }

    // ── /stop-follow ────────────────────────────────────────────────────────

    private suspend fun handleStopFollow(): EndpointResult {
        runOnMain { activity.debugStopFollow() }
        return EndpointResult(body = gson.toJson(mapOf("status" to "ok", "message" to "Stopped following")))
    }

    // ── /perf ───────────────────────────────────────────────────────────────

    private fun handlePerf(): EndpointResult {
        val runtime = Runtime.getRuntime()
        val data = mapOf(
            "memory" to mapOf(
                "max_mb" to runtime.maxMemory() / (1024 * 1024),
                "total_mb" to runtime.totalMemory() / (1024 * 1024),
                "free_mb" to runtime.freeMemory() / (1024 * 1024),
                "used_mb" to (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            ),
            "threads" to mapOf(
                "active" to Thread.activeCount(),
                "all" to Thread.getAllStackTraces().size
            ),
            "processors" to runtime.availableProcessors(),
            "uptime_ms" to android.os.SystemClock.elapsedRealtime(),
            "uptime_human" to formatUptime(android.os.SystemClock.elapsedRealtime())
        )
        return EndpointResult(body = gson.toJson(data))
    }

    // ── /overlays ───────────────────────────────────────────────────────────

    private suspend fun handleOverlays(): EndpointResult {
        val data = runOnMain {
            val overlays = activity.debugMapView().overlays
            val byType = mutableMapOf<String, Int>()
            overlays.forEach { overlay ->
                val name = overlay.javaClass.simpleName
                byType[name] = (byType[name] ?: 0) + 1
            }
            mapOf(
                "total" to overlays.size,
                "byType" to byType,
                "list" to overlays.mapIndexed { i, o ->
                    val info = mutableMapOf<String, Any?>(
                        "index" to i,
                        "type" to o.javaClass.simpleName
                    )
                    if (o is org.osmdroid.views.overlay.Marker) {
                        info["title"] = o.title ?: ""
                        info["lat"] = o.position.latitude
                        info["lon"] = o.position.longitude
                    }
                    info
                }.take(200)
            )
        }
        return EndpointResult(body = gson.toJson(data))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun formatUptime(ms: Long): String {
        val secs = ms / 1000
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return "${h}h ${m}m ${s}s"
    }

    // ── /geofences ──────────────────────────────────────────────────────────

    private suspend fun handleGeofences(): EndpointResult {
        val data = runOnMain {
            fun serializeZones(zones: List<com.example.locationmapapp.data.model.TfrZone>): List<Map<String, Any?>> {
                return zones.map { z ->
                    mapOf(
                        "id" to z.id,
                        "notam" to z.notam,
                        "type" to z.type,
                        "zoneType" to z.zoneType.name,
                        "description" to z.description.take(200),
                        "effectiveDate" to z.effectiveDate,
                        "expireDate" to z.expireDate,
                        "facility" to z.facility,
                        "state" to z.state,
                        "metadata" to z.metadata,
                        "shapeCount" to z.shapes.size,
                        "shapes" to z.shapes.map { s ->
                            mapOf("type" to s.type, "floorAltFt" to s.floorAltFt, "ceilingAltFt" to s.ceilingAltFt, "pointCount" to s.points.size, "radiusNm" to s.radiusNm)
                        }
                    )
                }
            }
            val tfrZones = geofenceViewModel.tfrZones.value ?: emptyList()
            val cameraZones = geofenceViewModel.cameraZones.value ?: emptyList()
            val schoolZones = geofenceViewModel.schoolZones.value ?: emptyList()
            val floodZones = geofenceViewModel.floodZones.value ?: emptyList()
            val crossingZones = geofenceViewModel.crossingZones.value ?: emptyList()
            val totalCount = tfrZones.size + cameraZones.size + schoolZones.size + floodZones.size + crossingZones.size
            mapOf(
                "totalCount" to totalCount,
                "counts" to mapOf(
                    "tfr" to tfrZones.size,
                    "camera" to cameraZones.size,
                    "school" to schoolZones.size,
                    "flood" to floodZones.size,
                    "crossing" to crossingZones.size
                ),
                "loadedZoneShapes" to geofenceViewModel.geofenceEngine.getLoadedZoneCount(),
                "zoneCountByType" to geofenceViewModel.geofenceEngine.getZoneCountByType().map { (k, v) -> k.name to v }.toMap(),
                "activeZones" to geofenceViewModel.geofenceEngine.getActiveZones(),
                "proximityThresholdNm" to geofenceViewModel.geofenceEngine.proximityThresholdNm,
                "tfrs" to serializeZones(tfrZones),
                "cameras" to serializeZones(cameraZones),
                "schools" to serializeZones(schoolZones),
                "floodZones" to serializeZones(floodZones),
                "crossings" to serializeZones(crossingZones)
            )
        }
        return EndpointResult(body = gson.toJson(data))
    }

    // ── /geofences/alerts ────────────────────────────────────────────────────

    private suspend fun handleGeofenceAlerts(): EndpointResult {
        val data = runOnMain {
            val alerts = geofenceViewModel.geofenceAlerts.value ?: emptyList()
            mapOf(
                "count" to alerts.size,
                "alerts" to alerts.map { a ->
                    mapOf(
                        "zoneId" to a.zoneId,
                        "zoneName" to a.zoneName,
                        "alertType" to a.alertType,
                        "severity" to a.severity.name,
                        "zoneType" to a.zoneType.name,
                        "distanceNm" to a.distanceNm,
                        "timestamp" to a.timestamp,
                        "description" to a.description
                    )
                }
            )
        }
        return EndpointResult(body = gson.toJson(data))
    }

    // ── Walk Tour Simulator ──────────────────────────────────────────────────

    /**
     * S124 Phase 9R.0: public API so the Activity's startWalkSim() can
     * cancel any DebugEndpoints-initiated walk before starting its own.
     * Prevents two concurrent walks emitting setManualLocation to the same
     * ViewModel (the "map bouncing between two positions" bug).
     *
     * S125: fire-and-forget; use [cancelWalkAndJoin] when the caller
     * needs to wait for the old coroutine to fully exit before starting
     * a new walk.
     */
    fun cancelAnyWalk() {
        walkJob?.cancel()
        walkJob = null
    }

    /**
     * S125: cancel the HTTP-triggered walk AND wait for it to terminate.
     * Kotlin's cancel() is cooperative — without this the old walkJob keeps
     * emitting setManualLocation for up to ~1 s after cancel, which
     * overlaps with the new walk's emissions (map bounce).
     */
    suspend fun cancelWalkAndJoin() {
        val j = walkJob
        walkJob = null
        j?.cancelAndJoin()
    }

    private fun handleWalkTour(params: Map<String, String>): EndpointResult {
        val tourId = params["tour"] ?: "tour_witch_trials"
        val speedMps = params["speed"]?.toFloatOrNull() ?: 1.4f // avg walking speed
        // Phase 9R.0: when `start_tour=true` (default), activate the tour in
        // TourEngine before walking. This is what flips HERITAGE_TRAIL tours
        // into Historical Mode — without starting the tour the Mode stays
        // Idle and the narration filter never engages.
        val startTour = params["start_tour"]?.equals("false", ignoreCase = true) != true

        val routePoints = TourRouteLoader.loadAllRoutePoints(activity, tourId)
        if (routePoints.isEmpty()) {
            return EndpointResult(400, body = gson.toJson(mapOf("error" to "No route data for $tourId")))
        }

        // Interpolate the route into evenly-spaced points at 1-second intervals
        val interpolated = interpolateRoute(routePoints, speedMps)

        DebugLogger.i("DebugEndpoints", "Walk simulator: $tourId, ${interpolated.size} steps, speed=${speedMps}m/s, ETA=${interpolated.size}s, startTour=$startTour")

        // S125: cancel-and-join both prior walks (HTTP + UI) atomically under
        // the shared walkMutex before we start the new walk. Without join(),
        // the prior walkJob keeps emitting setManualLocation for up to ~1 s
        // after cancel() and overlaps with the new emissions (map bounce
        // observed multiple times overnight 2026-04-14). The long-running
        // step loop runs AFTER the mutex releases so subsequent walk-start
        // requests aren't blocked for the full duration.
        val prevWalk = walkJob
        walkJob = walkScope.launch {
            activity.walkMutex.withLock {
                prevWalk?.cancelAndJoin()
                activity.stopWalkSimExternalAndJoin()
                if (startTour) {
                    try {
                        withContext(Dispatchers.Main) {
                            activity.tourViewModel.startTour(tourId)
                        }
                    } catch (e: Exception) {
                        DebugLogger.w("DebugEndpoints", "startTour($tourId) failed (continuing walk): ${e.message}")
                    }
                }
                // S125: acquire wake lock while we still hold the mutex so
                // the next caller can't race us during lock acquisition.
                activity.acquireWalkWakeLock("WickedSalem:walkSimHttp")
            }
            // Step loop runs outside the mutex so the next /walk-tour or UI
            // tap doesn't have to wait for us to finish the whole route.
            for ((i, point) in interpolated.withIndex()) {
                if (!isActive) break
                withContext(Dispatchers.Main) {
                    viewModel.setManualLocation(point)
                }
                delay(1000L) // 1 GPS fix per second
            }
            DebugLogger.i("DebugEndpoints", "Walk simulator complete")
        }
        // S125: release the wake lock when the walk ends — normal completion,
        // cancel-and-join from another path, or an uncaught exception.
        walkJob?.invokeOnCompletion { activity.releaseWalkWakeLock() }

        val etaMin = interpolated.size / 60.0
        return EndpointResult(body = gson.toJson(mapOf(
            "status" to "walking",
            "tour" to tourId,
            "startTour" to startTour,
            "routePoints" to routePoints.size,
            "interpolatedSteps" to interpolated.size,
            "speedMps" to speedMps,
            "etaMinutes" to "%.1f".format(etaMin)
        )))
    }

    private fun handleWalkStop(): EndpointResult {
        val wasActive = walkJob?.isActive == true
        walkJob?.cancel()
        walkJob = null
        return EndpointResult(body = gson.toJson(mapOf(
            "status" to "stopped",
            "wasActive" to wasActive
        )))
    }

    /**
     * Interpolate route points into evenly-spaced positions at the given speed.
     * Returns one GeoPoint per second of simulated walking.
     */
    private fun interpolateRoute(points: List<GeoPoint>, speedMps: Float): List<GeoPoint> {
        if (points.size < 2) return points
        val result = mutableListOf(points[0])
        var residualM = 0.0 // distance left over from previous segment

        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val segDistM = haversineM(from.latitude, from.longitude, to.latitude, to.longitude)
            if (segDistM < 0.1) continue

            var covered = residualM
            while (covered < segDistM) {
                covered += speedMps
                if (covered >= segDistM) {
                    residualM = covered - segDistM
                    break
                }
                val frac = covered / segDistM
                val lat = from.latitude + (to.latitude - from.latitude) * frac
                val lng = from.longitude + (to.longitude - from.longitude) * frac
                result.add(GeoPoint(lat, lng))
            }
        }
        result.add(points.last())
        return result
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Run a block on the main thread and suspend until it completes. */
    private suspend fun <T> runOnMain(block: () -> T): T {
        return suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                try {
                    cont.resume(block())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        }
    }
}
