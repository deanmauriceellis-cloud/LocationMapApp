package com.example.locationmapapp.util

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.example.locationmapapp.ui.MainActivity
import com.example.locationmapapp.ui.MainViewModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Response from an endpoint handler. */
data class EndpointResult(
    val status: Int = 200,
    val contentType: String = "application/json",
    val body: String = "",
    val bodyBytes: ByteArray? = null
)

/**
 * DebugEndpoints — all HTTP endpoint handlers for the debug server.
 * Holds references to MainActivity and MainViewModel.
 */
class DebugEndpoints(
    private val activity: MainActivity,
    private val viewModel: MainViewModel
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

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
                "mbtaTrains" to (viewModel.mbtaTrains.value?.size ?: 0),
                "mbtaSubway" to (viewModel.mbtaSubway.value?.size ?: 0),
                "mbtaBuses" to (viewModel.mbtaBuses.value?.size ?: 0),
                "mbtaStations" to (viewModel.mbtaStations.value?.size ?: 0),
                "aircraft" to (viewModel.aircraft.value?.size ?: 0),
                "followedAircraft" to viewModel.followedAircraft.value?.let {
                    mapOf("icao24" to it.icao24, "callsign" to it.callsign, "lat" to it.lat, "lon" to it.lon)
                },
                "webcams" to (viewModel.webcams.value?.size ?: 0),
                "metars" to (viewModel.metars.value?.size ?: 0),
                "weatherAlerts" to (viewModel.weatherAlerts.value?.size ?: 0),
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
            "routeType" to v.routeType
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
