package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.EarthquakeEvent
import com.example.locationmapapp.data.model.PlaceResult
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.osmdroid.util.GeoPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesRepository @Inject constructor() {

    private val TAG = "PlacesRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val PROXY_BASE    = "http://10.0.0.4:3000"
    private val OVERPASS_URL  = "$PROXY_BASE/overpass"

    companion object {
        const val DEFAULT_RADIUS_M = 3000
        const val MIN_RADIUS_M     = 500
        const val MAX_RADIUS_M     = 15000
        const val MIN_USEFUL_POI_COUNT = 5
    }

    /** Session-level in-memory cache of radius hints keyed by "lat3:lon3" */
    private val radiusHintCache = ConcurrentHashMap<String, Int>()

    // ── Radius hint helpers ──────────────────────────────────────────────────

    private fun gridKey(lat: Double, lon: Double): String =
        "%.3f:%.3f".format(lat, lon)

    /** Fetch the recommended search radius from the proxy, with session cache. */
    private fun fetchRadiusHint(center: GeoPoint): Int {
        val key = gridKey(center.latitude, center.longitude)
        radiusHintCache[key]?.let { return it }

        return try {
            val url = "$PROXY_BASE/radius-hint?lat=${center.latitude}&lon=${center.longitude}"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JsonParser.parseString(response.body!!.string()).asJsonObject
                val radius = json["radius"].asInt
                radiusHintCache[key] = radius
                DebugLogger.d(TAG, "Radius hint for $key = ${radius}m")
                radius
            } else {
                DebugLogger.w(TAG, "Radius hint request failed (${response.code}), using default")
                DEFAULT_RADIUS_M
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Radius hint fetch error: ${e.message}, using default")
            DEFAULT_RADIUS_M
        }
    }

    /** Post search feedback to the proxy so it can adapt the radius for this grid cell. */
    private fun postRadiusFeedback(center: GeoPoint, resultCount: Int, error: Boolean) {
        try {
            val key = gridKey(center.latitude, center.longitude)
            val json = """{"lat":${center.latitude},"lon":${center.longitude},"resultCount":$resultCount,"error":$error}"""
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$PROXY_BASE/radius-hint").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respJson = JsonParser.parseString(response.body!!.string()).asJsonObject
                val newRadius = respJson["radius"].asInt
                radiusHintCache[key] = newRadius
                DebugLogger.d(TAG, "Radius feedback posted for $key → ${newRadius}m")
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Radius feedback post error: ${e.message}")
        }
    }

    // ── POI search ────────────────────────────────────────────────────────────

    suspend fun searchPois(center: GeoPoint, categories: List<String>): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            val radiusM = fetchRadiusHint(center)
            val query = buildOverpassQuery(center, categories, radiusM)
            DebugLogger.d(TAG, "Overpass POST query (${query.length} chars, radius=${radiusM}m)")
            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder().url(OVERPASS_URL).post(body).build()
            val t0 = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - t0
            DebugLogger.i(TAG, "Overpass response code=${response.code} in ${elapsed}ms contentType=${response.header("Content-Type")}")
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(300) ?: "(empty)"
                DebugLogger.e(TAG, "Overpass HTTP ${response.code} body: $errBody")
                postRadiusFeedback(center, 0, error = true)
                throw RuntimeException("HTTP ${response.code}: $errBody")
            }
            val bodyStr = response.body!!.string()
            DebugLogger.d(TAG, "Overpass response body length=${bodyStr.length} chars")
            val results = parseOverpassJson(bodyStr)
            postRadiusFeedback(center, results.size, error = false)
            results
        }

    suspend fun searchGasStations(center: GeoPoint): List<PlaceResult> =
        searchPois(center, listOf("amenity=fuel"))

    /** Cache-only variant: returns cached POIs if available, empty list if not cached.
     *  Uses local hint cache for radius (no network round-trip for hint). */
    suspend fun searchPoisCacheOnly(center: GeoPoint, categories: List<String> = emptyList()): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            val key = gridKey(center.latitude, center.longitude)
            val radiusM = radiusHintCache[key] ?: DEFAULT_RADIUS_M
            val query = buildOverpassQuery(center, categories, radiusM)
            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder()
                .url(OVERPASS_URL)
                .post(body)
                .header("X-Cache-Only", "true")
                .build()
            val response = client.newCall(request).execute()
            if (response.code == 204) {
                return@withContext emptyList<PlaceResult>()
            }
            if (!response.isSuccessful) {
                return@withContext emptyList<PlaceResult>()
            }
            val bodyStr = response.body!!.string()
            parseOverpassJson(bodyStr)
        }

    private fun buildOverpassQuery(center: GeoPoint, categories: List<String>, radiusM: Int): String {
        val lat = center.latitude
        val lon = center.longitude
        val tags = if (categories.isEmpty()) {
            listOf("amenity", "shop", "tourism", "historic", "leisure", "office")
        } else categories

        val sb = StringBuilder("[out:json][timeout:25];\n(\n")
        tags.forEach { tag ->
            val filter = if (tag.contains("=")) {
                val (k, v) = tag.split("=", limit = 2)
                "[\"$k\"=\"$v\"]"
            } else {
                "[\"$tag\"]"
            }
            sb.append("  node${filter}(around:$radiusM,$lat,$lon);\n")
            sb.append("  way${filter}(around:$radiusM,$lat,$lon);\n")
        }
        sb.append(");\nout center 200;")
        return sb.toString()
    }

    private fun parseOverpassJson(json: String): List<PlaceResult> {
        val results = mutableListOf<PlaceResult>()
        try {
            val elements = JsonParser.parseString(json)
                .asJsonObject["elements"].asJsonArray
            for (el in elements) {
                val obj  = el.asJsonObject
                val tags = obj["tags"]?.asJsonObject ?: continue
                val name = tags["name"]?.asString ?: continue   // skip nameless nodes
                val lat  = when {
                    obj.has("lat")    -> obj["lat"].asDouble
                    obj.has("center") -> obj["center"].asJsonObject["lat"].asDouble
                    else -> continue
                }
                val lon  = when {
                    obj.has("lon")    -> obj["lon"].asDouble
                    obj.has("center") -> obj["center"].asJsonObject["lon"].asDouble
                    else -> continue
                }
                val category = tags["amenity"]?.asString
                    ?: tags["shop"]?.asString
                    ?: tags["tourism"]?.asString
                    ?: "place"
                results.add(PlaceResult(
                    id           = obj["id"].asString,
                    name         = name,
                    lat          = lat,
                    lon          = lon,
                    category     = category,
                    address      = buildAddress(tags),
                    phone        = tags["phone"]?.asString,
                    website      = tags["website"]?.asString,
                    openingHours = tags["opening_hours"]?.asString
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "JSON parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} POIs")
        return results
    }

    private fun buildAddress(tags: com.google.gson.JsonObject): String? {
        val parts = listOfNotNull(
            tags["addr:housenumber"]?.asString,
            tags["addr:street"]?.asString,
            tags["addr:city"]?.asString,
            tags["addr:state"]?.asString
        )
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    // ── Earthquakes ───────────────────────────────────────────────────────────

    suspend fun fetchEarthquakes(): List<EarthquakeEvent> = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.4:3000/earthquakes"
        DebugLogger.d(TAG, "Fetching earthquakes")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "Earthquake response code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "Earthquake HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body!!.string()
        DebugLogger.d(TAG, "Earthquake body length=${bodyStr.length}")
        parseEarthquakeJson(bodyStr)
    }

    private fun parseEarthquakeJson(json: String): List<EarthquakeEvent> {
        val results = mutableListOf<EarthquakeEvent>()
        try {
            val features = JsonParser.parseString(json)
                .asJsonObject["features"].asJsonArray
            for (f in features) {
                val props = f.asJsonObject["properties"].asJsonObject
                val coords = f.asJsonObject["geometry"].asJsonObject["coordinates"].asJsonArray
                results.add(EarthquakeEvent(
                    id        = f.asJsonObject["id"].asString,
                    magnitude = props["mag"]?.asDouble ?: 0.0,
                    place     = props["place"]?.asString ?: "Unknown",
                    lat       = coords[1].asDouble,
                    lon       = coords[0].asDouble,
                    depth     = coords[2].asDouble,
                    time      = props["time"]?.asLong ?: 0L,
                    url       = props["url"]?.asString ?: ""
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Earthquake JSON parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} earthquakes")
        return results
    }
}
