package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.AircraftState
import com.example.locationmapapp.data.model.FlightPathPoint
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AircraftRepository @Inject constructor() {

    private val TAG = "AircraftRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAircraft(south: Double, west: Double, north: Double, east: Double): List<AircraftState> = withContext(Dispatchers.IO) {
        val bbox = "$south,$west,$north,$east"
        val url = "http://10.0.0.4:3000/aircraft?bbox=$bbox"
        DebugLogger.d(TAG, "Fetching aircraft for bbox=$bbox")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "Aircraft response code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "Aircraft HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body?.string().orEmpty()
        DebugLogger.d(TAG, "Aircraft body length=${bodyStr.length}")
        if (bodyStr.isBlank()) emptyList() else parseAircraftJson(bodyStr)
    }

    /** Fetch a single aircraft by ICAO24 hex address (global, no bbox). Returns null if not found. */
    suspend fun fetchAircraftByIcao(icao24: String): AircraftState? = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.4:3000/aircraft?icao24=${icao24.lowercase()}"
        DebugLogger.d(TAG, "Fetching aircraft icao24=$icao24")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "Aircraft icao24 response code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            DebugLogger.e(TAG, "Aircraft icao24 HTTP ${response.code}")
            return@withContext null
        }
        val bodyStr = response.body?.string().orEmpty()
        if (bodyStr.isBlank()) null else parseAircraftJson(bodyStr).firstOrNull()
    }

    /** Fetch DB flight history for an aircraft. Returns chronological path points (2 per sighting). */
    suspend fun fetchFlightHistory(icao24: String): List<FlightPathPoint> = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.4:3000/db/aircraft/${icao24.lowercase()}"
        DebugLogger.d(TAG, "Fetching flight history for $icao24")
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Flight history HTTP ${response.code}")
                return@withContext emptyList()
            }
            val bodyStr = response.body?.string().orEmpty()
            if (bodyStr.isBlank()) return@withContext emptyList()
            val root = JsonParser.parseString(bodyStr).asJsonObject
            val pathArr = root.getAsJsonArray("path") ?: return@withContext emptyList()
            val points = mutableListOf<FlightPathPoint>()
            for (el in pathArr) {
                val obj = el.asJsonObject
                val firstLat = obj["firstLat"]?.let { if (it.isJsonNull) null else it.asDouble }
                val firstLon = obj["firstLon"]?.let { if (it.isJsonNull) null else it.asDouble }
                val lastLat = obj["lastLat"]?.let { if (it.isJsonNull) null else it.asDouble }
                val lastLon = obj["lastLon"]?.let { if (it.isJsonNull) null else it.asDouble }
                val altitude = obj["altitude"]?.let { if (it.isJsonNull) null else it.asDouble }
                val firstSeen = obj["firstSeen"]?.let { if (it.isJsonNull) null else it.asString }
                val lastSeen = obj["lastSeen"]?.let { if (it.isJsonNull) null else it.asString }
                val firstTs = parseIsoTimestamp(firstSeen)
                val lastTs = parseIsoTimestamp(lastSeen)
                if (firstLat != null && firstLon != null && firstTs > 0) {
                    points.add(FlightPathPoint(firstLat, firstLon, altitude, firstTs))
                }
                if (lastLat != null && lastLon != null && lastTs > 0 &&
                    (lastLat != firstLat || lastLon != firstLon)) {
                    points.add(FlightPathPoint(lastLat, lastLon, altitude, lastTs))
                }
            }
            DebugLogger.i(TAG, "Flight history: ${points.size} path points from ${pathArr.size()} sightings")
            points
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Flight history error", e)
            emptyList()
        }
    }

    private fun parseIsoTimestamp(iso: String?): Long {
        if (iso == null) return 0
        return try {
            java.time.Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) { 0 }
    }

    private fun parseAircraftJson(json: String): List<AircraftState> {
        val results = mutableListOf<AircraftState>()
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val states = root["states"]
            if (states == null || states.isJsonNull) {
                DebugLogger.i(TAG, "No aircraft states in response")
                return emptyList()
            }
            val arr = states.asJsonArray
            for (el in arr) {
                val s = el.asJsonArray
                val lat = s[6]?.let { if (it.isJsonNull) null else it.asDouble } ?: continue
                val lon = s[5]?.let { if (it.isJsonNull) null else it.asDouble } ?: continue
                results.add(AircraftState(
                    icao24         = s[0]?.asString ?: "",
                    callsign       = s[1]?.let { if (it.isJsonNull) null else it.asString?.trim() },
                    originCountry  = s[2]?.asString ?: "",
                    timePosition   = s[3]?.let { if (it.isJsonNull) null else it.asLong },
                    lastContact    = s[4]?.let { if (it.isJsonNull) null else it.asLong },
                    lat            = lat,
                    lon            = lon,
                    baroAltitude   = s[7]?.let { if (it.isJsonNull) null else it.asDouble },
                    onGround       = s[8]?.asBoolean ?: false,
                    velocity       = s[9]?.let { if (it.isJsonNull) null else it.asDouble },
                    track          = s[10]?.let { if (it.isJsonNull) null else it.asDouble },
                    verticalRate   = s[11]?.let { if (it.isJsonNull) null else it.asDouble },
                    geoAltitude    = s[13]?.let { if (it.isJsonNull) null else it.asDouble },
                    squawk         = s[14]?.let { if (it.isJsonNull) null else it.asString },
                    spi            = if (s.size() > 15) s[15]?.let { if (it.isJsonNull) false else it.asBoolean } ?: false else false,
                    positionSource = if (s.size() > 16) s[16]?.let { if (it.isJsonNull) 0 else it.asInt } ?: 0 else 0,
                    category       = if (s.size() > 17) s[17]?.let { if (it.isJsonNull) 0 else it.asInt } ?: 0 else 0
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Aircraft parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} aircraft")
        return results
    }
}
