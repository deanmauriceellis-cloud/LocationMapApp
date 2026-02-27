package com.example.locationmapapp.data.repository

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

    // ── Internal ───────────────────────────────────────────────────────────────

    private suspend fun fetchVehicles(routeType: Int): List<MbtaVehicle> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/vehicles" +
                    "?filter%5Broute_type%5D=$routeType" +
                    "&include=stop%2Croute" +
                    "&api_key=$API_KEY"

            DebugLogger.d(TAG, "Fetching vehicles routeType=$routeType")
            val t0 = System.currentTimeMillis()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - t0

            DebugLogger.i(TAG, "MBTA response code=${response.code} routeType=$routeType in ${elapsed}ms")

            if (!response.isSuccessful) {
                val err = response.body?.string()?.take(200) ?: "(empty)"
                DebugLogger.e(TAG, "MBTA HTTP ${response.code}: $err")
                throw RuntimeException("MBTA HTTP ${response.code}")
            }

            val bodyStr = response.body!!.string()
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
}
