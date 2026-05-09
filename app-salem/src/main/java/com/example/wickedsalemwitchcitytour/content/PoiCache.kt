/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content

import android.os.SystemClock
import android.util.Log
import com.example.wickedsalemwitchcitytour.content.dao.SalemPoiDao
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory snapshot of every SalemPoi in the bundled Room asset.
 *
 * V1 POI data is fully baked into the APK at build time and never changes at
 * runtime. Pre-S234 the repository ran a fresh Room query for every read
 * (16 methods × N call sites = significant per-frame work during interaction).
 * This cache loads ALL rows once on first access, builds the indexes the
 * various filters need, and then serves every subsequent read in pure memory.
 *
 * Loading is lazy + serialized: the first caller to `ensureLoaded` triggers
 * the Room read; concurrent callers wait on the mutex; subsequent callers
 * see the cached snapshot and return immediately.
 *
 * The snapshot is immutable. SalemPoi is a Kotlin data class with `val`
 * fields, so handing out shared references is safe; nothing in the app
 * mutates POIs at runtime. If `insertPois` is ever called (write path —
 * not used in V1 device runtime), call `invalidate()` to force the next
 * read to re-load.
 */
@Singleton
class PoiCache @Inject constructor(
    private val salemPoiDao: SalemPoiDao,
) {
    @Volatile private var snapshot: Snapshot? = null
    private val mutex = Mutex()

    private data class Snapshot(
        val all: List<SalemPoi>,
        val byId: Map<String, SalemPoi>,
        val byCategory: Map<String, List<SalemPoi>>,        // key uppercased
        val bySubcategory: Map<String, List<SalemPoi>>,
        val byDistrict: Map<String, List<SalemPoi>>,
        val visible: List<SalemPoi>,
        val narrated: List<SalemPoi>,
        val tour: List<SalemPoi>,
        val districts: List<String>,
    )

    /**
     * Idempotent. First call hits Room; subsequent calls return immediately.
     * Safe to call from any coroutine context.
     */
    suspend fun ensureLoaded() {
        if (snapshot != null) return
        mutex.withLock {
            if (snapshot != null) return
            val t0 = SystemClock.elapsedRealtimeNanos()
            val all = salemPoiDao.findAll()
            snapshot = build(all)
            val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            Log.i(TAG, "Loaded ${all.size} POIs in ${ms}ms (categories=${snapshot!!.byCategory.size}, " +
                "districts=${snapshot!!.districts.size}, visible=${snapshot!!.visible.size}, " +
                "narrated=${snapshot!!.narrated.size}, tour=${snapshot!!.tour.size})")
        }
    }

    /** Drop the snapshot. Next read reloads. Only used after `insertPois`. */
    suspend fun invalidate() {
        mutex.withLock { snapshot = null }
    }

    private fun build(all: List<SalemPoi>): Snapshot {
        val byId = all.associateBy { it.id }
        val byCategory = all.groupBy { it.category.uppercase() }
        val bySubcategory = all
            .filter { !it.subcategory.isNullOrBlank() }
            .groupBy { it.subcategory!! }
        val byDistrict = all
            .filter { !it.district.isNullOrBlank() }
            .groupBy { it.district!! }
        val visible = all.filter { it.defaultVisible }
        // Mirrors SalemPoiDao.findNarrated SQL exactly:
        //   (is_narrated OR (haunt_sprite_id IS NOT NULL AND haunt_enabled))
        //   AND (default_visible OR is_tour_poi OR is_historical_property OR is_civic_poi)
        val narrated = all.filter { p ->
            val poolEligible = p.isNarrated || (p.hauntSpriteId != null && p.hauntEnabled)
            val visibilityGate = p.defaultVisible || p.isTourPoi || p.isHistoricalProperty || p.isCivicPoi
            poolEligible && visibilityGate
        }
        val tour = all.filter { it.isTourPoi }
        val districts = byDistrict.keys.sorted()
        return Snapshot(all, byId, byCategory, bySubcategory, byDistrict, visible, narrated, tour, districts)
    }

    private fun snap(): Snapshot =
        snapshot ?: error("PoiCache not loaded — call ensureLoaded() before any read")

    // ── Read API mirrors SalemPoiDao ────────────────────────────────────────

    fun findAll(): List<SalemPoi> = snap().all
    fun findAllVisible(): List<SalemPoi> = snap().visible
    fun findById(id: String): SalemPoi? = snap().byId[id]

    fun findByCategory(category: String): List<SalemPoi> =
        snap().byCategory[category.uppercase()] ?: emptyList()

    fun findBySubcategory(subcategory: String): List<SalemPoi> =
        snap().bySubcategory[subcategory] ?: emptyList()

    fun findNarrated(): List<SalemPoi> = snap().narrated

    fun findNarratedByWave(wave: Int): List<SalemPoi> =
        snap().narrated.filter { it.wave == wave }

    fun findNarratedByMaxPriority(maxPriority: Int): List<SalemPoi> =
        snap().narrated.filter { it.priority <= maxPriority }

    fun findNarratedInBbox(
        latMin: Double, latMax: Double, lngMin: Double, lngMax: Double,
    ): List<SalemPoi> = snap().narrated.filter {
        it.lat in latMin..latMax && it.lng in lngMin..lngMax
    }

    fun findTourPois(): List<SalemPoi> = snap().tour

    fun findTourPoisByCategory(category: String): List<SalemPoi> =
        snap().tour.filter { it.category.equals(category, ignoreCase = true) }

    fun findNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemPoi> =
        nearbyFilter(snap().all, lat, lng, radiusDeg)

    fun findNarratedNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemPoi> =
        nearbyFilter(snap().narrated, lat, lng, radiusDeg)

    fun findVisibleNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemPoi> =
        nearbyFilter(snap().visible, lat, lng, radiusDeg)

    private fun nearbyFilter(
        source: List<SalemPoi>, lat: Double, lng: Double, radiusDeg: Double,
    ): List<SalemPoi> {
        val latLo = lat - radiusDeg; val latHi = lat + radiusDeg
        val lngLo = lng - radiusDeg; val lngHi = lng + radiusDeg
        // Filter then sort by squared euclidean distance — matches DAO ORDER BY dist_sq.
        return source.asSequence()
            .filter { it.lat in latLo..latHi && it.lng in lngLo..lngHi }
            .sortedBy {
                val dlat = lat - it.lat
                val dlng = lng - it.lng
                dlat * dlat + dlng * dlng
            }
            .toList()
    }

    fun search(query: String, limit: Int = 50): List<SalemPoi> {
        if (query.isBlank()) return emptyList()
        return snap().all.asSequence()
            .filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                    p.description?.contains(query, ignoreCase = true) == true ||
                    p.shortDescription?.contains(query, ignoreCase = true) == true
            }
            .sortedBy { it.priority }
            .take(limit)
            .toList()
    }

    fun findByDistrict(district: String): List<SalemPoi> =
        snap().byDistrict[district] ?: emptyList()

    fun getDistricts(): List<String> = snap().districts

    fun count(): Int = snap().all.size
    fun countNarrated(): Int = snap().narrated.size
    fun countVisible(): Int = snap().visible.size
    fun countByWave(wave: Int): Int = snap().narrated.count { it.wave == wave }

    companion object { private const val TAG = "PoiCache" }
}
