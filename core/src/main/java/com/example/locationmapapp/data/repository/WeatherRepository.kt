/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.*
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WeatherRepository.kt"

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
        val url = "http://10.0.0.229:4300/nws-alerts"
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

    // ── NWS Composite Weather (via proxy /weather) ────────────────────────────

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.229:4300/weather?lat=$lat&lon=$lon"
        DebugLogger.d(TAG, "Fetching weather for $lat,$lon")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "Weather code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "Weather HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body!!.string()
        parseWeatherJson(bodyStr)
    }

    private fun parseWeatherJson(json: String): WeatherData {
        val root = JsonParser.parseString(json).asJsonObject

        // Location
        val loc = root.getAsJsonObject("location")
        val location = WeatherLocation(
            city = loc["city"]?.asString ?: "",
            state = loc["state"]?.asString ?: "",
            station = loc["station"]?.asString ?: ""
        )

        // Current conditions
        val cur = root.getAsJsonObject("current")
        val current = if (cur != null && !cur.isJsonNull) CurrentConditions(
            temperature = cur["temperature"]?.let { if (it.isJsonNull) null else it.asInt },
            temperatureUnit = cur["temperatureUnit"]?.asString ?: "F",
            humidity = cur["humidity"]?.let { if (it.isJsonNull) null else it.asInt },
            windSpeed = cur["windSpeed"]?.let { if (it.isJsonNull) null else it.asInt },
            windDirection = cur["windDirection"]?.let { if (it.isJsonNull) null else it.asString },
            windChill = cur["windChill"]?.let { if (it.isJsonNull) null else it.asInt },
            heatIndex = cur["heatIndex"]?.let { if (it.isJsonNull) null else it.asInt },
            dewpoint = cur["dewpoint"]?.let { if (it.isJsonNull) null else it.asInt },
            description = cur["description"]?.asString ?: "",
            iconCode = cur["iconCode"]?.asString ?: "unknown",
            isDaytime = cur["isDaytime"]?.asBoolean ?: true,
            visibility = cur["visibility"]?.let { if (it.isJsonNull) null else it.asDouble },
            barometer = cur["barometer"]?.let { if (it.isJsonNull) null else it.asDouble }
        ) else null

        // Hourly forecast
        val hourlyArr = root.getAsJsonArray("hourly") ?: com.google.gson.JsonArray()
        val hourly = mutableListOf<HourlyForecast>()
        for (el in hourlyArr) {
            val h = el.asJsonObject
            hourly.add(HourlyForecast(
                time = h["time"]?.asString ?: "",
                temperature = h["temperature"]?.asInt ?: 0,
                windSpeed = h["windSpeed"]?.asString ?: "",
                windDirection = h["windDirection"]?.asString ?: "",
                precipProbability = h["precipProbability"]?.asInt ?: 0,
                shortForecast = h["shortForecast"]?.asString ?: "",
                iconCode = h["iconCode"]?.asString ?: "unknown",
                isDaytime = h["isDaytime"]?.asBoolean ?: true
            ))
        }

        // Daily forecast
        val dailyArr = root.getAsJsonArray("daily") ?: com.google.gson.JsonArray()
        val daily = mutableListOf<DailyForecast>()
        for (el in dailyArr) {
            val d = el.asJsonObject
            daily.add(DailyForecast(
                name = d["name"]?.asString ?: "",
                isDaytime = d["isDaytime"]?.asBoolean ?: true,
                temperature = d["temperature"]?.asInt ?: 0,
                windSpeed = d["windSpeed"]?.asString ?: "",
                shortForecast = d["shortForecast"]?.asString ?: "",
                detailedForecast = d["detailedForecast"]?.asString ?: "",
                iconCode = d["iconCode"]?.asString ?: "unknown",
                precipProbability = d["precipProbability"]?.asInt ?: 0
            ))
        }

        // Alerts
        val alertArr = root.getAsJsonArray("alerts") ?: com.google.gson.JsonArray()
        val alerts = mutableListOf<WeatherAlert>()
        for (el in alertArr) {
            val a = el.asJsonObject
            alerts.add(WeatherAlert(
                id = a["id"]?.asString ?: "",
                event = a["event"]?.asString ?: "",
                headline = a["headline"]?.asString ?: "",
                description = a["description"]?.asString ?: "",
                severity = a["severity"]?.asString ?: "",
                urgency = a["urgency"]?.asString ?: "",
                instruction = a["instruction"]?.asString ?: "",
                effective = a["effective"]?.asString ?: "",
                expires = a["expires"]?.asString ?: "",
                areaDesc = a["areaDesc"]?.asString ?: ""
            ))
        }

        val result = WeatherData(
            location = location,
            current = current,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
            fetchedAt = root["fetchedAt"]?.asString ?: ""
        )
        DebugLogger.i(TAG, "Parsed weather: ${location.city},${location.state} " +
                "current=${current != null} hourly=${hourly.size} daily=${daily.size} alerts=${alerts.size}")
        return result
    }

    // ── METAR (aviationweather.gov AWOS/ASOS) ─────────────────────────────────

    suspend fun fetchMetars(south: Double, west: Double, north: Double, east: Double): List<MetarStation> = withContext(Dispatchers.IO) {
        val bbox = "$south,$west,$north,$east"
        val url = "http://10.0.0.229:4300/metar?bbox=$bbox"
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
        val bodyStr = response.body?.string().orEmpty()
        DebugLogger.d(TAG, "METAR body length=${bodyStr.length}")
        if (bodyStr.isBlank()) emptyList() else parseMetarJson(bodyStr)
    }

    private fun parseMetarJson(json: String): List<MetarStation> {
        val results = mutableListOf<MetarStation>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val lat = obj["lat"]?.asDouble ?: continue
                val lon = obj["lon"]?.asDouble ?: continue
                val visibRaw = obj["visib"]?.let {
                    if (it.isJsonNull) null
                    else if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                        it.asString.replace("+", "").toDoubleOrNull()
                    } else it.asDouble
                }
                results.add(MetarStation(
                    stationId       = obj["icaoId"]?.asString ?: "",
                    name            = obj["name"]?.let { if (it.isJsonNull) null else it.asString },
                    lat             = lat,
                    lon             = lon,
                    rawMetar        = obj["rawOb"]?.asString ?: "",
                    tempC           = obj["temp"]?.let { if (it.isJsonNull) null else it.asDouble },
                    dewpointC       = obj["dewp"]?.let { if (it.isJsonNull) null else it.asDouble },
                    windDirDeg      = obj["wdir"]?.let { if (it.isJsonNull) null else it.asInt },
                    windSpeedKt     = obj["wspd"]?.let { if (it.isJsonNull) null else it.asInt },
                    windGustKt      = obj["wgst"]?.let { if (it.isJsonNull) null else it.asInt },
                    visibilityMiles = visibRaw,
                    altimeterInHg   = obj["altim"]?.let { if (it.isJsonNull) null else it.asDouble / 33.8639 },
                    slpMb           = obj["slp"]?.let { if (it.isJsonNull) null else it.asDouble },
                    flightCategory  = obj["fltCat"]?.let { if (it.isJsonNull) null else it.asString },
                    skyCover        = obj["cover"]?.let { if (it.isJsonNull) null else it.asString },
                    wxString        = obj["wxString"]?.let { if (it.isJsonNull) null else it.asString },
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
