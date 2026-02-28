package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.AircraftState
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
