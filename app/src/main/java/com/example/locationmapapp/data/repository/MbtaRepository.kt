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

    /** Subway routes to query (location_type=1 = parent station) */
    private val SUBWAY_ROUTES = "Red,Orange,Blue,Green-B,Green-C,Green-D,Green-E,Mattapan"

    /**
     * Fetch all subway + commuter rail stations.
     * Two API calls: one for subway lines, one for CR (route_type=2).
     * Merges by stop ID so multi-line stations combine their routeIds.
     */
    suspend fun fetchStations(): List<MbtaStop> = withContext(Dispatchers.IO) {
        val mergedStops = mutableMapOf<String, MbtaStop>()

        // 1) Subway stops (filter by explicit route names)
        val subwayUrl = "$BASE_URL/stops" +
                "?filter%5Broute%5D=$SUBWAY_ROUTES" +
                "&filter%5Blocation_type%5D=1" +
                "&include=route" +
                "&api_key=$API_KEY"
        val subwayJson = executeGet(subwayUrl, "subway stops")
        parseStops(subwayJson).forEach { stop ->
            mergedStops.merge(stop.id, stop) { existing, new ->
                existing.copy(routeIds = (existing.routeIds + new.routeIds).distinct())
            }
        }

        // 2) Commuter Rail stops (route_type=2)
        val crUrl = "$BASE_URL/stops" +
                "?filter%5Broute_type%5D=2" +
                "&filter%5Blocation_type%5D=1" +
                "&include=route" +
                "&api_key=$API_KEY"
        val crJson = executeGet(crUrl, "CR stops")
        parseStops(crJson).forEach { stop ->
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
                    "&include=stop%2Croute" +
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

        // Build stop name lookup from included objects
        val stopNames = mutableMapOf<String, String>()
        val routeNames = mutableMapOf<String, String>()

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

                vehicles.add(
                    MbtaVehicle(
                        id            = id,
                        label         = label,
                        routeId       = routeId,
                        routeName     = routeName,
                        tripId        = tripId,
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

    private fun parseStops(json: String): List<MbtaStop> {
        val root = JsonParser.parseString(json).asJsonObject
        val dataArray = root.getAsJsonArray("data") ?: return emptyList()

        // Build route name lookup from included
        val routeNames = mutableMapOf<String, String>()
        root.getAsJsonArray("included")?.forEach { element ->
            val obj = element.asJsonObject
            if (obj.get("type")?.asString == "route") {
                val id = obj.get("id")?.asString ?: return@forEach
                routeNames[id] = id  // use route ID as-is (Red, Orange, CR-Worcester...)
            }
        }

        val stops = mutableListOf<MbtaStop>()
        dataArray.forEach { element ->
            try {
                val obj = element.asJsonObject
                val id = obj.get("id")?.asString ?: return@forEach
                val attrs = obj.getAsJsonObject("attributes") ?: return@forEach
                val lat = attrs.get("latitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val lon = attrs.get("longitude")?.takeIf { !it.isJsonNull }?.asDouble ?: return@forEach
                val name = attrs.get("name")?.asString ?: id

                // Extract route IDs from relationships
                val routeIds = mutableListOf<String>()
                obj.getAsJsonObject("relationships")
                    ?.getAsJsonObject("route")
                    ?.get("data")?.let { data ->
                        if (data.isJsonArray) {
                            data.asJsonArray.forEach { r ->
                                r.asJsonObject.get("id")?.asString?.let { routeIds.add(it) }
                            }
                        } else if (data.isJsonObject) {
                            data.asJsonObject.get("id")?.asString?.let { routeIds.add(it) }
                        }
                    }

                stops.add(MbtaStop(id = id, name = name, lat = lat, lon = lon, routeIds = routeIds))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse stop: ${e.message}")
            }
        }
        DebugLogger.i(TAG, "Parsed ${stops.size} stops")
        return stops
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
