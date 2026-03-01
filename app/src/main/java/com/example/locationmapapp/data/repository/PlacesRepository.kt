package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.CapEvent
import com.example.locationmapapp.data.model.PlaceResult
import com.example.locationmapapp.data.model.PopulateSearchResult
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Cap detection events — emitted when Overpass returns exactly the result limit */
    private val _capEvents = MutableSharedFlow<CapEvent>(extraBufferCapacity = 8)
    val capEvents: SharedFlow<CapEvent> = _capEvents.asSharedFlow()

    /** Session-level in-memory cache of radius hints keyed by "lat3:lon3" */
    private val radiusHintCache = ConcurrentHashMap<String, Int>()

    // ── Radius hint helpers ──────────────────────────────────────────────────

    private fun gridKey(lat: Double, lon: Double): String =
        "%.3f:%.3f".format(lat, lon)

    /** Find the nearest cached radius hint within 1 mile, or null. */
    private fun fuzzyRadiusHint(lat: Double, lon: Double): Int? {
        val oneMileDeg = 0.01449  // ~1 mile in degrees latitude
        val cosLat = Math.cos(Math.toRadians(lat))
        var nearest: Int? = null
        var nearestDist = Double.MAX_VALUE

        for ((key, radius) in radiusHintCache) {
            val parts = key.split(":")
            if (parts.size != 2) continue
            val hLat = parts[0].toDoubleOrNull() ?: continue
            val hLon = parts[1].toDoubleOrNull() ?: continue
            val dLat = hLat - lat
            val dLon = (hLon - lon) * cosLat
            val dist = Math.sqrt(dLat * dLat + dLon * dLon)
            if (dist <= oneMileDeg && dist < nearestDist) {
                nearest = radius
                nearestDist = dist
            }
        }
        return nearest
    }

    /** Fetch the recommended search radius from the proxy, with session cache. */
    private fun fetchRadiusHint(center: GeoPoint): Int {
        val key = gridKey(center.latitude, center.longitude)
        radiusHintCache[key]?.let { return it }

        // Fuzzy: check nearby cached hints before network call
        fuzzyRadiusHint(center.latitude, center.longitude)?.let {
            DebugLogger.d(TAG, "Radius fuzzy hit for $key → ${it}m")
            return it
        }

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
    private fun postRadiusFeedback(center: GeoPoint, resultCount: Int, error: Boolean, capped: Boolean = false) {
        try {
            val key = gridKey(center.latitude, center.longitude)
            val json = """{"lat":${center.latitude},"lon":${center.longitude},"resultCount":$resultCount,"error":$error,"capped":$capped}"""
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
            val (results, rawCount) = parseOverpassJson(bodyStr)
            val capped = rawCount >= OVERPASS_RESULT_LIMIT
            if (capped) {
                DebugLogger.w(TAG, "CAPPED — raw=$rawCount, parsed=${results.size} at radius=${radiusM}m")
                postRadiusFeedback(center, results.size, error = false, capped = true)
                _capEvents.tryEmit(CapEvent(center, radiusM, rawCount, results.size, categories))
            } else {
                postRadiusFeedback(center, results.size, error = false)
            }
            results
        }

    /** Cache-only variant: returns cached POIs if available, empty list if not cached.
     *  Uses local hint cache for radius (no network round-trip for hint). */
    suspend fun searchPoisCacheOnly(center: GeoPoint, categories: List<String> = emptyList()): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            val key = gridKey(center.latitude, center.longitude)
            val radiusM = radiusHintCache[key]
                ?: fuzzyRadiusHint(center.latitude, center.longitude)
                ?: DEFAULT_RADIUS_M
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
            parseOverpassJson(bodyStr).first
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

    /** Pair<parsed results, raw element count from Overpass before filtering> */
    private fun parseOverpassJson(json: String): Pair<List<PlaceResult>, Int> {
        val results = mutableListOf<PlaceResult>()
        var rawCount = 0
        try {
            val elements = JsonParser.parseString(json)
                .asJsonObject["elements"].asJsonArray
            rawCount = elements.size()
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
                    ?: tags["leisure"]?.asString
                    ?: tags["historic"]?.asString
                    ?: tags["office"]?.asString
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
        DebugLogger.i(TAG, "Parsed ${results.size} POIs (raw elements: $rawCount)")
        return Pair(results, rawCount)
    }

    /** Populate-mode variant: searches POIs and reads X-Cache header to report cache status.
     *  @param radiusOverride if non-null, overrides the radius hint (used for cap-retry with smaller radius) */
    suspend fun searchPoisForPopulate(center: GeoPoint, categories: List<String> = emptyList(), radiusOverride: Int? = null): PopulateSearchResult =
        withContext(Dispatchers.IO) {
            val key = gridKey(center.latitude, center.longitude)
            val radiusM = radiusOverride ?: fetchRadiusHint(center)
            val query = buildOverpassQuery(center, categories, radiusM)
            DebugLogger.d(TAG, "Populate search at $key (radius=${radiusM}m)")
            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder().url(OVERPASS_URL).post(body).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                postRadiusFeedback(center, 0, error = true)
                throw RuntimeException("HTTP ${response.code}")
            }
            val cacheHeader = response.header("X-Cache") ?: "MISS"
            val cacheHit = cacheHeader.equals("HIT", ignoreCase = true)
            val bodyStr = response.body!!.string()
            val (results, rawCount) = parseOverpassJson(bodyStr)
            val capped = rawCount >= OVERPASS_RESULT_LIMIT
            if (capped) {
                DebugLogger.w(TAG, "Populate CAPPED — raw=$rawCount elements (limit=$OVERPASS_RESULT_LIMIT), parsed=${results.size} named POIs at $key (radius=${radiusM}m) — POIs likely lost")
            }
            postRadiusFeedback(center, results.size, error = false)
            PopulateSearchResult(results, cacheHit, key, radiusM, capped)
        }

    /** Subdivision search result — includes cap status for recursive subdivision decisions. */
    data class SubdivisionResult(val poiCount: Int, val capped: Boolean)

    /** Lightweight subdivision search — queries a sub-cell at given radius.
     *  Results flow into the proxy cache automatically; returns count + cap status. */
    suspend fun searchSubdivision(center: GeoPoint, categories: List<String>, radiusM: Int): SubdivisionResult =
        withContext(Dispatchers.IO) {
            val query = buildOverpassQuery(center, categories, radiusM)
            DebugLogger.d(TAG, "Subdivision search at ${center.latitude},${center.longitude} radius=${radiusM}m")
            val body = FormBody.Builder().add("data", query).build()
            val request = Request.Builder().url(OVERPASS_URL).post(body).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                DebugLogger.w(TAG, "Subdivision search failed: HTTP ${response.code}")
                return@withContext SubdivisionResult(0, false)
            }
            val bodyStr = response.body!!.string()
            val (results, rawCount) = parseOverpassJson(bodyStr)
            val capped = rawCount >= OVERPASS_RESULT_LIMIT
            if (capped) {
                DebugLogger.w(TAG, "Subdivision sub-cell capped (raw=$rawCount, parsed=${results.size}) at radius=${radiusM}m")
            }
            postRadiusFeedback(center, results.size, error = false, capped = capped)
            SubdivisionResult(results.size, capped)
        }

    companion object {
        const val DEFAULT_RADIUS_M = 3000
        const val MIN_RADIUS_M     = 250
        const val MAX_RADIUS_M     = 15000
        const val MIN_USEFUL_POI_COUNT = 5
        const val OVERPASS_RESULT_LIMIT = 200
    }

    /** Fetch cached POIs within a bounding box from the proxy's poi-cache. */
    suspend fun fetchCachedPoisInBbox(south: Double, west: Double, north: Double, east: Double): List<PlaceResult> = withContext(Dispatchers.IO) {
        val url = "$PROXY_BASE/pois/bbox?s=$south&w=$west&n=$north&e=$east"
        DebugLogger.d(TAG, "Fetching cached POIs for bbox=$south,$west,$north,$east")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "POI bbox response code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) return@withContext emptyList()
        val bodyStr = response.body?.string().orEmpty()
        if (bodyStr.isBlank()) return@withContext emptyList()
        // Response format: { count, elements: [...] } — same element format as Overpass
        parseOverpassJson(bodyStr).first
    }

    private fun parsePoiExportJson(json: String): List<PlaceResult> {
        val results = mutableListOf<PlaceResult>()
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val pois = root["pois"]?.asJsonArray ?: return emptyList()
            for (entry in pois) {
                val obj = entry.asJsonObject
                val el = obj["element"]?.asJsonObject ?: continue
                val tags = el["tags"]?.asJsonObject ?: continue
                val lat = when {
                    el.has("lat")    -> el["lat"].asDouble
                    el.has("center") -> el["center"].asJsonObject["lat"].asDouble
                    else -> continue
                }
                val lon = when {
                    el.has("lon")    -> el["lon"].asDouble
                    el.has("center") -> el["center"].asJsonObject["lon"].asDouble
                    else -> continue
                }
                val category = tags["amenity"]?.asString
                    ?: tags["shop"]?.asString
                    ?: tags["tourism"]?.asString
                    ?: tags["leisure"]?.asString
                    ?: tags["historic"]?.asString
                    ?: tags["office"]?.asString
                    ?: "place"
                val name = tags["name"]?.asString ?: category
                results.add(PlaceResult(
                    id       = el["id"]?.asString ?: continue,
                    name     = name,
                    lat      = lat,
                    lon      = lon,
                    category = category,
                    address  = buildAddress(tags),
                    phone    = tags["phone"]?.asString,
                    website  = tags["website"]?.asString,
                    openingHours = tags["opening_hours"]?.asString
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "POI export parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} POIs from export")
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
}
