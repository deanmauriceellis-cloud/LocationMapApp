/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.FindCounts
import com.example.locationmapapp.data.model.FindResponse
import com.example.locationmapapp.data.model.FindResult
import com.example.locationmapapp.data.model.PoiWebsite
import com.example.locationmapapp.data.model.SearchResponse
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

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module FindRepository.kt"

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
    private var countsCachedLat: Double = 0.0
    private var countsCachedLon: Double = 0.0
    private val COUNTS_TTL_MS = 10 * 60 * 1000L
    private val COUNTS_MOVE_THRESHOLD_M = 500.0  // invalidate cache if moved >500m

    suspend fun fetchCounts(lat: Double, lon: Double, radiusM: Int = 10000): FindCounts? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedCounts?.let {
            val moved = haversineM(lat, lon, countsCachedLat, countsCachedLon)
            if (now - countsCachedAt < COUNTS_TTL_MS && moved < COUNTS_MOVE_THRESHOLD_M) {
                DebugLogger.d(TAG, "Counts from client cache (moved ${moved.toInt()}m)")
                return@withContext it
            }
        }

        val url = "http://10.0.0.229:4300/db/pois/counts?lat=$lat&lon=$lon&radius=$radiusM"
        DebugLogger.d(TAG, "Fetching counts lat=$lat lon=$lon radius=${radiusM}m")
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
            countsCachedLat = lat
            countsCachedLon = lon
            DebugLogger.i(TAG, "Counts loaded: ${counts.size} categories, $total total within ${radiusM}m")
            result
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Counts error", e)
            null
        }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    suspend fun findNearby(
        lat: Double,
        lon: Double,
        categories: List<String>,
        limit: Int = 50,
        offset: Int = 0
    ): FindResponse? = withContext(Dispatchers.IO) {
        val catParam = URLEncoder.encode(categories.joinToString(","), "UTF-8")
        val url = "http://10.0.0.229:4300/db/pois/find?lat=$lat&lon=$lon&categories=$catParam&limit=$limit&offset=$offset"
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

    suspend fun searchByName(
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 50
    ): SearchResponse? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "http://10.0.0.229:4300/db/pois/search?q=$q&lat=$lat&lon=$lon&limit=$limit"
        DebugLogger.d(TAG, "searchByName q=$query lat=$lat lon=$lon")
        try {
            val t0 = System.currentTimeMillis()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val elapsed = System.currentTimeMillis() - t0
            DebugLogger.i(TAG, "searchByName response code=${response.code} in ${elapsed}ms")
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "searchByName HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val root = JsonParser.parseString(body).asJsonObject
            val count = root["count"]?.asInt ?: 0
            val scopeM = root["scope_m"]?.asInt ?: 50000
            val categoryHint = root["category_hint"]?.let { if (it.isJsonNull) null else it.asString }
            val elements = root.getAsJsonArray("elements") ?: return@withContext SearchResponse(emptyList(), 0, scopeM, categoryHint)

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
                    DebugLogger.w(TAG, "Search parse error: ${e.message}")
                    null
                }
            }

            DebugLogger.i(TAG, "searchByName: ${results.size} results for '$query' (hint=$categoryHint scope=${scopeM}m)")
            SearchResponse(results, count, scopeM, categoryHint)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "searchByName error", e)
            null
        }
    }

    suspend fun fetchWebsite(
        osmType: String,
        osmId: Long,
        name: String?,
        lat: Double,
        lon: Double
    ): PoiWebsite? = withContext(Dispatchers.IO) {
        val params = StringBuilder("osm_type=${URLEncoder.encode(osmType, "UTF-8")}&osm_id=$osmId")
        if (name != null) params.append("&name=${URLEncoder.encode(name, "UTF-8")}")
        params.append("&lat=$lat&lon=$lon")
        val url = "http://10.0.0.229:4300/pois/website?$params"
        DebugLogger.d(TAG, "fetchWebsite osm=$osmType/$osmId")
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "fetchWebsite HTTP ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val obj = JsonParser.parseString(body).asJsonObject
            PoiWebsite(
                url = obj["url"]?.let { if (it.isJsonNull) null else it.asString },
                source = obj["source"]?.asString ?: "none",
                phone = obj["phone"]?.let { if (it.isJsonNull) null else it.asString },
                hours = obj["hours"]?.let { if (it.isJsonNull) null else it.asString },
                address = obj["address"]?.let { if (it.isJsonNull) null else it.asString }
            )
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchWebsite error", e)
            null
        }
    }
}
