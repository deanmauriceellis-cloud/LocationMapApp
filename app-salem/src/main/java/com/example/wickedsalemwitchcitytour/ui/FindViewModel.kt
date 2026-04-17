/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.FindCounts
import com.example.locationmapapp.data.model.FindResponse
import com.example.locationmapapp.data.model.FindResult
import com.example.locationmapapp.data.model.PoiWebsite
import com.example.locationmapapp.data.model.SearchResponse
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.dao.SalemPoiDao
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sqrt
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module FindViewModel.kt"

/**
 * FindViewModel — Salem POI search, backed by Room `SalemPoiDao`.
 *
 * S141: rewritten from cache-proxy `FindRepository` to the local Room DAO for
 * V1 offline-only posture. All 1,837 Salem POIs live in bundled
 * `salem_content.db`, so proximity search and name search run locally without
 * any network dependency. The cache-proxy FindRepository remains in
 * `core/` but is unused in V1; V2 can resume it by unblocking the
 * `V1_OFFLINE_ONLY` flag and reverting this ViewModel's dependency.
 *
 * Public API preserved exactly so `SalemMainActivityFind` does not change.
 * `FindResult.id` is populated from `SalemPoi.id.hashCode().toLong()` with a
 * session-level reverse-lookup cache so `fetchPoiWebsiteDirectly` can still
 * resolve website/phone/hours from the originating SalemPoi.
 */
@HiltViewModel
class FindViewModel @Inject constructor(
    private val salemPoiDao: SalemPoiDao
) : ViewModel() {

    private val TAG = "FindVM"
    private val POI_TYPE_LABEL = "salem_poi"

    private val _findCounts = MutableLiveData<FindCounts?>()
    val findCounts: LiveData<FindCounts?> = _findCounts

    /**
     * Session-level cache of hashed-id → originating SalemPoi. Populated
     * whenever this VM emits FindResult objects. Enables later
     * `fetchPoiWebsiteDirectly` lookups (which only receive the hashed id).
     * Unbounded in theory but bounded in practice by the 1,837-POI catalog.
     */
    private val idToPoi = ConcurrentHashMap<Long, SalemPoi>()

    fun loadFindCounts(lat: Double, lon: Double) {
        DebugLogger.i(TAG, "loadFindCounts() at $lat,$lon")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // Default radius = 10 km, matches old cache-proxy default
                    queryNearby(lat, lon, radiusM = 10_000)
                }
            }
                .onSuccess { pois ->
                    val counts = pois
                        .groupingBy { it.category }
                        .eachCount()
                    _findCounts.value = FindCounts(counts = counts, total = pois.size)
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "FindCounts FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    /**
     * Direct suspend call for Find dialog — returns proximity-ordered results.
     * [categories] filters by `SalemPoi.category`; an empty list means no
     * filter (matches all categories).
     */
    suspend fun findNearbyDirectly(
        lat: Double,
        lon: Double,
        categories: List<String>,
        limit: Int = 50,
        offset: Int = 0
    ): FindResponse? = withContext(Dispatchers.IO) {
        try {
            val all = queryNearby(lat, lon, radiusM = 10_000)
            val filtered = if (categories.isEmpty()) all
                           else all.filter { it.category in categories }
            val paged = filtered.drop(offset).take(limit)
            val results = paged.map { it.toFindResult(lat, lon).also { fr -> idToPoi[fr.id] = it } }
            FindResponse(
                results = results,
                totalInRange = filtered.size,
                scopeM = 10_000
            )
        } catch (e: Exception) {
            DebugLogger.e(TAG, "findNearbyDirectly FAILED: ${e.message}", e)
            null
        }
    }

    /**
     * Name / description text search. Room DAO does a LIKE %query% match
     * against name, description, short_description. Results are sorted by
     * distance from (lat, lon).
     */
    suspend fun searchPoisByName(
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 200
    ): SearchResponse? = withContext(Dispatchers.IO) {
        try {
            val matches = salemPoiDao.search(query, limit)
            val sorted = matches.sortedBy { distanceM(lat, lon, it.lat, it.lng) }
            val results = sorted.map { it.toFindResult(lat, lon).also { fr -> idToPoi[fr.id] = it } }
            SearchResponse(
                results = results,
                totalCount = results.size,
                scopeM = Int.MAX_VALUE, // text search is bbox-unbounded
                categoryHint = null
            )
        } catch (e: Exception) {
            DebugLogger.e(TAG, "searchPoisByName FAILED: ${e.message}", e)
            null
        }
    }

    /**
     * Reverse-lookup the originating SalemPoi from the hashed id and return
     * its website / phone / hours / address. The (osmType, osmId) signature
     * is preserved from the V2 cache-proxy interface; in V1 [osmId] is the
     * SalemPoi.id hashCode.
     */
    suspend fun fetchPoiWebsiteDirectly(
        osmType: String,
        osmId: Long,
        name: String?,
        lat: Double,
        lon: Double
    ): PoiWebsite? = withContext(Dispatchers.IO) {
        try {
            // Primary lookup: cache populated by prior queries.
            val poi = idToPoi[osmId]
                // Fallback: rare cold-cache case. Scan by name + coords.
                ?: salemPoiDao.findAll().firstOrNull {
                    it.name == name && kotlin.math.abs(it.lat - lat) < 0.0001 &&
                        kotlin.math.abs(it.lng - lon) < 0.0001
                }

            if (poi == null) {
                PoiWebsite(url = null, source = "none", phone = null, hours = null, address = null)
            } else {
                PoiWebsite(
                    url = poi.website?.takeIf { it.isNotBlank() },
                    source = "salem_room",
                    phone = poi.phone,
                    hours = poi.hoursText ?: poi.hours,
                    address = poi.address
                )
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchPoiWebsiteDirectly FAILED: ${e.message}", e)
            null
        }
    }

    /** Returns total POI count within [radiusM] of (lat, lon), or -1 on error. */
    suspend fun fetchNearbyPoiCount(lat: Double, lon: Double, radiusM: Int = 10000): Int =
        withContext(Dispatchers.IO) {
            try {
                queryNearby(lat, lon, radiusM).size
            } catch (e: Exception) {
                DebugLogger.e(TAG, "fetchNearbyPoiCount FAILED: ${e.message}", e)
                -1
            }
        }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Query Room for POIs inside a (lat±radiusDeg, lng±radiusDeg) bounding box.
     * Room DAO filters first; we then filter by true great-circle distance
     * and sort by proximity. `radiusM` → degrees uses a latitude-aware
     * conversion (1° lat ≈ 111 km; 1° lng ≈ 111 km × cos(lat)).
     */
    private suspend fun queryNearby(lat: Double, lon: Double, radiusM: Int): List<SalemPoi> {
        val radiusDeg = radiusM / 111_000.0
        val candidates = salemPoiDao.findNearby(lat, lon, radiusDeg)
        return candidates
            .filter { distanceM(lat, lon, it.lat, it.lng) <= radiusM }
            .sortedBy { distanceM(lat, lon, it.lat, it.lng) }
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return (R * 2 * Math.atan2(sqrt(a), sqrt(1 - a))).toInt()
    }

    /** Map a SalemPoi to the legacy FindResult shape consumed by the UI. */
    private fun SalemPoi.toFindResult(fromLat: Double, fromLon: Double): FindResult {
        return FindResult(
            id = this.id.hashCode().toLong(),
            type = POI_TYPE_LABEL,
            name = this.name,
            lat = this.lat,
            lon = this.lng,
            category = this.category,
            distanceM = distanceM(fromLat, fromLon, this.lat, this.lng),
            tags = buildMap {
                this@toFindResult.subcategory?.let { put("subcategory", it) }
                this@toFindResult.district?.let { put("district", it) }
                this@toFindResult.historicalPeriod?.let { put("historical_period", it) }
                put("salem_id", this@toFindResult.id)
            },
            address = this.address,
            cuisine = this.cuisineType,
            phone = this.phone,
            openingHours = this.hoursText ?: this.hours
        )
    }
}
