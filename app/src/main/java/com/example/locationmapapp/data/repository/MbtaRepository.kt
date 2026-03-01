package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.MbtaPrediction
import com.example.locationmapapp.data.model.MbtaStop
import com.example.locationmapapp.data.model.MbtaTripScheduleEntry
import com.example.locationmapapp.data.model.MbtaVehicle
import com.example.locationmapapp.data.model.MbtaVehicleStatus
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MbtaRepository — fetches live vehicle positions from MBTA API v3.
 *
 * Endpoint: GET /vehicles?filter[route_type]=<type>&include=stop,route,trip
 * Auth: api_key query param
 *
 * Stop names are resolved from the included `stop` objects in the same response
 * (JSON:API compound document pattern) — no second request needed.
 *
 * Route type filter values:
 *   0 = Light Rail (Green Line branches)
 *   1 = Heavy Rail (Red, Orange, Blue)
 *   2 = Commuter Rail
 *   3 = Bus
 */
@Singleton
class MbtaRepository @Inject constructor() {

    private val TAG = "MbtaRepository"
    private val API_KEY = "d2dbf0064a5a4e80b9384fea24c43c9b"
    private val BASE_URL = "https://api-v3.mbta.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // MBTA requires Accept header for JSON:API
            val req = chain.request().newBuilder()
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("x-api-key", API_KEY)
                .build()
            chain.proceed(req)
        }
        .build()

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Fetch all active Commuter Rail vehicles (route_type=2) */
    suspend fun fetchCommuterRailVehicles(): List<MbtaVehicle> =
        fetchVehicles(routeType = 2)

    /** Fetch all active Subway vehicles (route_type=0 Light Rail + route_type=1 Heavy Rail) */
    suspend fun fetchSubwayVehicles(): List<MbtaVehicle> =
        fetchVehicles(routeType = 0) + fetchVehicles(routeType = 1)

    /** Fetch all active Bus vehicles (route_type=3) */
    suspend fun fetchBusVehicles(): List<MbtaVehicle> =
        fetchVehicles(routeType = 3)

    // ── Stations ──────────────────────────────────────────────────────────────

    /** Subway routes to query individually for route→stop mapping */
    private val SUBWAY_ROUTES_LIST = listOf(
        "Red", "Orange", "Blue", "Green-B", "Green-C", "Green-D", "Green-E", "Mattapan"
    )

    /**
     * Fetch all subway + commuter rail stations.
     * Subway: query each route separately so we know which routes serve each stop
     *   (the MBTA API returns null route relationships on parent station objects).
     * CR: filter by route_type=2 (returns platforms grouped by parent_station).
     * Merges by ID so shared stations (South Station, etc.) combine routeIds.
     */
    suspend fun fetchStations(): List<MbtaStop> = withContext(Dispatchers.IO) {
        val mergedStops = mutableMapOf<String, MbtaStop>()

        // 1) Subway stops — query per route to tag each stop with its route ID
        for (route in SUBWAY_ROUTES_LIST) {
            val url = "$BASE_URL/stops" +
                    "?filter%5Broute%5D=$route" +
                    "&filter%5Blocation_type%5D=1" +
                    "&api_key=$API_KEY"
            val json = executeGet(url, "stops route=$route")
            parseStopsWithRoute(json, route).forEach { stop ->
                mergedStops.merge(stop.id, stop) { existing, new ->
                    existing.copy(routeIds = (existing.routeIds + new.routeIds).distinct())
                }
            }
        }
        DebugLogger.i(TAG, "Subway: ${mergedStops.size} parent stations")

        // 2) Commuter Rail stops (type=0 platforms — no route relationships)
        //    Group by parent_station ID so we get 143 unique stations instead of 227 platforms.
        //    Parent IDs are "place-xxx" format, same as subway — shared stations merge by ID.
        val crUrl = "$BASE_URL/stops" +
                "?filter%5Broute_type%5D=2" +
                "&api_key=$API_KEY"
        val crJson = executeGet(crUrl, "CR stops")
        parseCrStops(crJson).forEach { stop ->
            mergedStops.merge(stop.id, stop) { existing, new ->
                existing.copy(routeIds = (existing.routeIds + new.routeIds).distinct())
            }
        }

        DebugLogger.i(TAG, "Fetched ${mergedStops.size} unique stations (subway + CR)")
        mergedStops.values.toList()
    }

    /**
     * Fetch real-time predictions for a given stop.
     * Returns upcoming arrivals sorted by departure time.
     */
    suspend fun fetchPredictions(stopId: String): List<MbtaPrediction> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/predictions" +
                "?filter%5Bstop%5D=$stopId" +
                "&include=trip%2Croute" +
                "&sort=departure_time" +
                "&api_key=$API_KEY"
        val json = executeGet(url, "predictions stop=$stopId")
        parsePredictions(json)
    }

    /**
     * Fetch the full schedule for a given trip.
     * Returns all stops in order with times.
     */
    suspend fun fetchTripSchedule(tripId: String): List<MbtaTripScheduleEntry> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/schedules" +
                "?filter%5Btrip%5D=$tripId" +
                "&include=stop" +
                "&sort=stop_sequence" +
                "&api_key=$API_KEY"
        val json = executeGet(url, "schedule trip=$tripId")
        parseTripSchedule(json)
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    /** Shared GET helper — returns response body as string. */
    private fun executeGet(url: String, label: String): String {
        DebugLogger.d(TAG, "GET $label")
        val t0 = System.currentTimeMillis()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "MBTA $label code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val err = response.body?.string()?.take(200) ?: "(empty)"
            DebugLogger.e(TAG, "MBTA $label HTTP ${response.code}: $err")
            throw RuntimeException("MBTA HTTP ${response.code}")
        }
        return response.body!!.string()
    }

    private suspend fun fetchVehicles(routeType: Int): List<MbtaVehicle> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/vehicles" +
                    "?filter%5Broute_type%5D=$routeType" +
                    "&include=stop%2Croute%2Ctrip" +
                    "&api_key=$API_KEY"

            val bodyStr = executeGet(url, "vehicles routeType=$routeType")
            parseVehicleResponse(bodyStr, routeType)
        }

    /**
     * Parse JSON:API compound document.
     *
     * Top-level structure:
     * {
     *   "data": [ { vehicle objects } ],
     *   "included": [ { stop objects }, { route objects } ]
     * }
     *
     * We build lookup maps from included objects first, then parse each vehicle.
     */
    private fun parseVehicleResponse(json: String, routeType: Int): List<MbtaVehicle> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        // Build lookup maps from included objects (stops, routes, trips)
        val stopNames = mutableMapOf<String, String>()
        val routeNames = mutableMapOf<String, String>()
        val tripHeadsigns = mutableMapOf<String, String>()

        root.getAsJsonArray("included")?.forEach { element ->
            val obj = element.asJsonObject
            val type = obj.get("type")?.asString ?: return@forEach
            val id   = obj.get("id")?.asString   ?: return@forEach
            val attrs = obj.getAsJsonObject("attributes") ?: return@forEach

            when (type) {
                "stop"  -> stopNames[id]  = attrs.get("name")?.asString ?: id
                "route" -> routeNames[id] = attrs.get("long_name")?.asString
                    ?: attrs.get("short_name")?.asString
                    ?: id
                "trip"  -> attrs.get("headsign")?.takeIf { !it.isJsonNull }?.asString?.let {
                    tripHeadsigns[id] = it
                }
            }
        }

        val vehicles = mutableListOf<MbtaVehicle>()

        dataArray.forEach { element ->
            try {
                val obj   = element.asJsonObject
                val id    = obj.get("id")?.asString ?: return@forEach
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val rels  = obj.getAsJsonObject("relationships")

                val lat = attrs.get("latitude")?.takeIf { !it.isJsonNull }?.asDouble
                val lon = attrs.get("longitude")?.takeIf { !it.isJsonNull }?.asDouble
                if (lat == null || lon == null) return@forEach  // skip vehicles without position

                val label   = attrs.get("label")?.asString ?: id
                val bearing = attrs.get("bearing")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val speedMps = attrs.get("speed")?.takeIf { !it.isJsonNull }?.asDouble
                val updatedAt = attrs.get("updated_at")?.asString ?: ""

                val status = when (attrs.get("current_status")?.asString) {
                    "INCOMING_AT"  -> MbtaVehicleStatus.INCOMING_AT
                    "STOPPED_AT"   -> MbtaVehicleStatus.STOPPED_AT
                    "IN_TRANSIT_TO"-> MbtaVehicleStatus.IN_TRANSIT_TO
                    else           -> MbtaVehicleStatus.UNKNOWN
                }

                // Resolve stop and route from relationships
                // Note: "data" can be JsonNull when a vehicle has no current stop/trip
                val stopId  = rels?.getAsJsonObject("stop")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString
                val routeId = rels?.getAsJsonObject("route")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString ?: ""
                val tripId  = rels?.getAsJsonObject("trip")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString

                val stopName  = stopId?.let { stopNames[it] }
                val routeName = routeNames[routeId] ?: formatRouteId(routeId)
                val headsign  = tripId?.let { tripHeadsigns[it] }

                vehicles.add(
                    MbtaVehicle(
                        id            = id,
                        label         = label,
                        routeId       = routeId,
                        routeName     = routeName,
                        tripId        = tripId,
                        headsign      = headsign,
                        stopId        = stopId,
                        stopName      = stopName,
                        lat           = lat,
                        lon           = lon,
                        bearing       = bearing,
                        speedMps      = speedMps,
                        currentStatus = status,
                        updatedAt     = updatedAt,
                        routeType     = routeType
                    )
                )
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse vehicle: ${e.message}")
            }
        }

        DebugLogger.i(TAG, "Parsed ${vehicles.size} vehicles routeType=$routeType (${stopNames.size} stops, ${routeNames.size} routes resolved)")
        return vehicles
    }

    /** Convert "CR-Fitchburg" → "Fitchburg Line" for display when API name unavailable */
    private fun formatRouteId(routeId: String): String {
        return routeId
            .removePrefix("CR-")
            .replace("-", " ")
            .let { if (it != routeId) "$it Line" else it }
    }

    // ── Station parsers ─────────────────────────────────────────────────────

    /** Parse stops JSON and tag each with the known route ID (since the API doesn't provide it). */
    private fun parseStopsWithRoute(json: String, routeId: String): List<MbtaStop> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        val stops = mutableListOf<MbtaStop>()
        dataArray.forEach { element ->
            try {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@forEach
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val lat = attrs.get("latitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val lon = attrs.get("longitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val name = attrs.get("name")?.asString ?: id

                stops.add(MbtaStop(id = id, name = name, lat = lat, lon = lon, routeIds = listOf(routeId)))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse stop: ${e.message}")
            }
        }
        return stops
    }

    /**
     * Parse CR stops (location_type=0 platforms) and group by parent_station ID.
     * CR stops don't have route relationships, so we tag them all with "CR".
     * Uses parent_station "place-xxx" ID as the stop ID so shared stations
     * (South Station, Back Bay, etc.) automatically merge with subway entries.
     */
    private fun parseCrStops(json: String): List<MbtaStop> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        // Group platforms by parent station — use first platform's coords per parent
        val parentStops = mutableMapOf<String, MbtaStop>()
        dataArray.forEach { element ->
            try {
                val obj = element.asJsonObject
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val lat = attrs.get("latitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val lon = attrs.get("longitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val name = attrs.get("name")?.asString ?: return@forEach

                // Use parent_station ID as our stop ID (matches subway "place-xxx" format)
                val parentId = obj.getAsJsonObject("relationships")
                    ?.getAsJsonObject("parent_station")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject
                    ?.get("id")?.asString ?: return@forEach

                if (parentId !in parentStops) {
                    parentStops[parentId] = MbtaStop(
                        id = parentId, name = name, lat = lat, lon = lon,
                        routeIds = listOf("CR")
                    )
                }
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse CR stop: ${e.message}")
            }
        }
        DebugLogger.i(TAG, "Parsed ${parentStops.size} CR parent stations from ${dataArray.size()} platforms")
        return parentStops.values.toList()
    }

    private fun parsePredictions(json: String): List<MbtaPrediction> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        // Build lookups from included
        val tripHeadsigns = mutableMapOf<String, String>()
        val routeNames = mutableMapOf<String, String>()
        root.getAsJsonArray("included")?.forEach { element ->
            val obj = element.asJsonObject
            val type = obj.get("type")?.asString ?: return@forEach
            val id = obj.get("id")?.asString ?: return@forEach
            val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
            when (type) {
                "trip" -> tripHeadsigns[id] = attrs.get("headsign")?.asString ?: ""
                "route" -> routeNames[id] = attrs.get("long_name")?.asString
                    ?: attrs.get("short_name")?.asString ?: id
            }
        }

        val predictions = mutableListOf<MbtaPrediction>()
        dataArray.forEach { element ->
            try {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@forEach
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val rels = obj.getAsJsonObject("relationships")

                val arrivalTime = attrs.get("arrival_time")?.takeIf { !it.isJsonNull }?.asString
                val departureTime = attrs.get("departure_time")?.takeIf { !it.isJsonNull }?.asString
                val directionId = attrs.get("direction_id")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val status = attrs.get("status")?.takeIf { !it.isJsonNull }?.asString

                val routeId = rels?.getAsJsonObject("route")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString ?: ""
                val tripId = rels?.getAsJsonObject("trip")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString
                val vehicleId = rels?.getAsJsonObject("vehicle")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString

                val headsign = tripId?.let { tripHeadsigns[it] }
                val routeName = routeNames[routeId] ?: formatRouteId(routeId)

                predictions.add(MbtaPrediction(
                    id = id, routeId = routeId, routeName = routeName,
                    tripId = tripId, headsign = headsign,
                    arrivalTime = arrivalTime, departureTime = departureTime,
                    directionId = directionId, status = status, vehicleId = vehicleId
                ))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse prediction: ${e.message}")
            }
        }
        DebugLogger.i(TAG, "Parsed ${predictions.size} predictions")
        return predictions
    }

    // ── Bus Stops ─────────────────────────────────────────────────────────

    /**
     * Fetch all bus stops (route_type=3) from MBTA API.
     * Returns ~7,900 stops as flat MbtaStop objects (no parent grouping).
     * Uses page[limit]=10000 to get all in one request (~500KB).
     */
    suspend fun fetchBusStops(): List<MbtaStop> = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.4:3000/mbta/bus-stops"
        val json = executeGet(url, "bus stops")
        parseBusStops(json)
    }

    private fun parseBusStops(json: String): List<MbtaStop> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        val stops = mutableListOf<MbtaStop>()
        dataArray.forEach { element ->
            try {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@forEach
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val lat = attrs.get("latitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val lon = attrs.get("longitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val name = attrs.get("name")?.asString ?: id

                stops.add(MbtaStop(id = id, name = name, lat = lat, lon = lon, routeIds = listOf("Bus")))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse bus stop: ${e.message}")
            }
        }
        DebugLogger.i(TAG, "Parsed ${stops.size} bus stops")
        return stops
    }

    private fun parseTripSchedule(json: String): List<MbtaTripScheduleEntry> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        // Build stop lookup from included
        val stopNames = mutableMapOf<String, String>()
        val platformCodes = mutableMapOf<String, String?>()
        root.getAsJsonArray("included")?.forEach { element ->
            val obj = element.asJsonObject
            if (obj.get("type")?.asString == "stop") {
                val id = obj.get("id")?.asString ?: return@forEach
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                stopNames[id] = attrs.get("name")?.asString ?: id
                platformCodes[id] = attrs.get("platform_code")?.takeIf { !it.isJsonNull }?.asString
            }
        }

        val entries = mutableListOf<MbtaTripScheduleEntry>()
        dataArray.forEach { element ->
            try {
                val obj = element.asJsonObject
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val rels = obj.getAsJsonObject("relationships")

                val stopId = rels?.getAsJsonObject("stop")
                    ?.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("id")?.asString ?: return@forEach
                val stopSequence = attrs.get("stop_sequence")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val arrivalTime = attrs.get("arrival_time")?.takeIf { !it.isJsonNull }?.asString
                val departureTime = attrs.get("departure_time")?.takeIf { !it.isJsonNull }?.asString

                entries.add(MbtaTripScheduleEntry(
                    stopId = stopId,
                    stopName = stopNames[stopId] ?: stopId,
                    stopSequence = stopSequence,
                    arrivalTime = arrivalTime,
                    departureTime = departureTime,
                    platformCode = platformCodes[stopId]
                ))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse schedule entry: ${e.message}")
            }
        }
        DebugLogger.i(TAG, "Parsed ${entries.size} schedule entries")
        return entries
    }
}
