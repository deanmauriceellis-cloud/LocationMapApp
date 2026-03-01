package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.FindCounts
import com.example.locationmapapp.data.model.FindResponse
import com.example.locationmapapp.data.model.FindResult
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindRepository @Inject constructor() {

    private val TAG = "FindRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Client-side counts cache (10 min) — avoids repeated network on dialog reopen
    private var cachedCounts: FindCounts? = null
    private var countsCachedAt: Long = 0
    private val COUNTS_TTL_MS = 10 * 60 * 1000L

    suspend fun fetchCounts(): FindCounts? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedCounts?.let {
            if (now - countsCachedAt < COUNTS_TTL_MS) {
                DebugLogger.d(TAG, "Counts from client cache")
                return@withContext it
            }
        }

        val url = "http://10.0.0.4:3000/db/pois/counts"
        DebugLogger.d(TAG, "Fetching counts")
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Counts HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val root = JsonParser.parseString(body).asJsonObject
            val countsObj = root.getAsJsonObject("counts") ?: return@withContext null
            val counts = mutableMapOf<String, Int>()
            for ((key, value) in countsObj.entrySet()) {
                counts[key] = value.asInt
            }
            val total = root["total"]?.asInt ?: counts.values.sum()
            val result = FindCounts(counts, total)
            cachedCounts = result
            countsCachedAt = now
            DebugLogger.i(TAG, "Counts loaded: ${counts.size} categories, $total total")
            result
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Counts error", e)
            null
        }
    }

    suspend fun findNearby(
        lat: Double,
        lon: Double,
        categories: List<String>,
        limit: Int = 50,
        offset: Int = 0
    ): FindResponse? = withContext(Dispatchers.IO) {
        val catParam = URLEncoder.encode(categories.joinToString(","), "UTF-8")
        val url = "http://10.0.0.4:3000/db/pois/find?lat=$lat&lon=$lon&categories=$catParam&limit=$limit&offset=$offset"
        DebugLogger.d(TAG, "findNearby lat=$lat lon=$lon cats=${categories.size} limit=$limit")
        try {
            val t0 = System.currentTimeMillis()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val elapsed = System.currentTimeMillis() - t0
            DebugLogger.i(TAG, "findNearby response code=${response.code} in ${elapsed}ms")
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "findNearby HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val root = JsonParser.parseString(body).asJsonObject
            val totalInRange = root["total_in_range"]?.asInt ?: 0
            val scopeM = root["scope_m"]?.asInt ?: 0
            val elements = root.getAsJsonArray("elements") ?: return@withContext FindResponse(emptyList(), 0, 0)

            val results = elements.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val tags = mutableMapOf<String, String>()
                    obj.getAsJsonObject("tags")?.entrySet()?.forEach { (k, v) ->
                        if (!v.isJsonNull) tags[k] = v.asString
                    }
                    FindResult(
                        id = obj["id"].asLong,
                        type = obj["type"]?.asString ?: "node",
                        name = obj["name"]?.let { if (it.isJsonNull) null else it.asString },
                        lat = obj["lat"].asDouble,
                        lon = obj["lon"].asDouble,
                        category = obj["category"]?.asString ?: "",
                        distanceM = obj["distance_m"]?.asInt ?: 0,
                        tags = tags,
                        address = tags["addr:street"]?.let { street ->
                            val num = tags["addr:housenumber"]
                            if (num != null) "$num $street" else street
                        },
                        cuisine = tags["cuisine"],
                        phone = tags["phone"],
                        openingHours = tags["opening_hours"]
                    )
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Parse element error: ${e.message}")
                    null
                }
            }

            DebugLogger.i(TAG, "findNearby: ${results.size} results, $totalInRange in range, scope=${scopeM}m")
            FindResponse(results, totalInRange, scopeM)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "findNearby error", e)
            null
        }
    }
}
