package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.MetarStation
import com.example.locationmapapp.data.model.WeatherAlert
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
class WeatherRepository @Inject constructor() {

    private val TAG = "WeatherRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // NWS requires a User-Agent header with contact info
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "LocationMapApp/1.1 contact@example.com")
                    .build()
            )
        }
        .build()

    // ── NWS Weather Alerts (all active US alerts) ─────────────────────────────

    suspend fun fetchAlerts(): List<WeatherAlert> = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.4:3000/nws-alerts"
        DebugLogger.d(TAG, "Fetching NWS alerts")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "NWS alerts code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "NWS HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body!!.string()
        DebugLogger.d(TAG, "NWS alerts body length=${bodyStr.length}")
        parseAlertsJson(bodyStr)
    }

    private fun parseAlertsJson(json: String): List<WeatherAlert> {
        val results = mutableListOf<WeatherAlert>()
        try {
            val features = JsonParser.parseString(json)
                .asJsonObject["features"].asJsonArray
            for (f in features) {
                val p = f.asJsonObject["properties"].asJsonObject
                results.add(WeatherAlert(
                    id          = p["id"]?.asString ?: "",
                    event       = p["event"]?.asString ?: "",
                    headline    = p["headline"]?.asString ?: "",
                    description = p["description"]?.asString ?: "",
                    severity    = p["severity"]?.asString ?: "",
                    effective   = p["effective"]?.asString ?: "",
                    expires     = p["expires"]?.asString ?: "",
                    areaDesc    = p["areaDesc"]?.asString ?: ""
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Alert parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} weather alerts")
        return results
    }

    // ── METAR (aviationweather.gov AWOS/ASOS) ─────────────────────────────────

    suspend fun fetchMetars(): List<MetarStation> = withContext(Dispatchers.IO) {
        // Fetch all US METARs reported in last hour as JSON
        val url = "http://10.0.0.4:3000/metar"
        DebugLogger.d(TAG, "Fetching METARs")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed2 = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "METAR response code=${response.code} in ${elapsed2}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "METAR HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body!!.string()
        DebugLogger.d(TAG, "METAR body length=${bodyStr.length}")
        parseMetarJson(bodyStr)
    }

    private fun parseMetarJson(json: String): List<MetarStation> {
        val results = mutableListOf<MetarStation>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val lat = obj["lat"]?.asDouble ?: continue
                val lon = obj["lon"]?.asDouble ?: continue
                results.add(MetarStation(
                    stationId       = obj["icaoId"]?.asString ?: "",
                    lat             = lat,
                    lon             = lon,
                    rawMetar        = obj["rawOb"]?.asString ?: "",
                    tempC           = obj["temp"]?.asDouble,
                    dewpointC       = obj["dewp"]?.asDouble,
                    windDirDeg      = obj["wdir"]?.let { if (it.isJsonNull) null else it.asInt },
                    windSpeedKt     = obj["wspd"]?.let { if (it.isJsonNull) null else it.asInt },
                    visibilityMiles = obj["visib"]?.let { if (it.isJsonNull) null else it.asDouble },
                    altimeterInHg   = obj["altim"]?.let { if (it.isJsonNull) null else it.asDouble / 33.8639 },
                    flightCategory  = obj["fltcat"]?.asString,
                    observationTime = obj["reportTime"]?.asString
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "METAR parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} METAR stations")
        return results
    }
}
