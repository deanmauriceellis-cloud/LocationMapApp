/*
 * WickedSalemWitchCityTour v1.5 — Phase 9U (Session 118)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.*
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi

/**
 * Unified DAO for all Salem POIs. Covers all query patterns previously split
 * across TourPoiDao, SalemBusinessDao, and NarrationPointDao.
 *
 * Filters: is_narrated, is_tour_poi, default_visible, category, wave, priority.
 */
@Dao
interface SalemPoiDao {

    // ── Core lookups ────────────────────────────────────────────────────────

    @Query("SELECT * FROM salem_pois WHERE id = :id")
    suspend fun findById(id: String): SalemPoi?

    @Query("SELECT * FROM salem_pois ORDER BY priority ASC, name ASC")
    suspend fun findAll(): List<SalemPoi>

    @Query("SELECT * FROM salem_pois WHERE default_visible = 1 ORDER BY priority ASC, name ASC")
    suspend fun findAllVisible(): List<SalemPoi>

    @Query("SELECT * FROM salem_pois WHERE category = :category ORDER BY priority ASC, name ASC")
    suspend fun findByCategory(category: String): List<SalemPoi>

    @Query("SELECT * FROM salem_pois WHERE subcategory = :subcategory ORDER BY priority ASC, name ASC")
    suspend fun findBySubcategory(subcategory: String): List<SalemPoi>

    // ── Narration queries (replaces NarrationPointDao) ─────────────��────────

    @Query("SELECT * FROM salem_pois WHERE is_narrated = 1 ORDER BY priority ASC, name ASC")
    suspend fun findNarrated(): List<SalemPoi>

    @Query("SELECT * FROM salem_pois WHERE is_narrated = 1 AND wave = :wave ORDER BY priority ASC, name ASC")
    suspend fun findNarratedByWave(wave: Int): List<SalemPoi>

    @Query("SELECT * FROM salem_pois WHERE is_narrated = 1 AND priority <= :maxPriority ORDER BY priority ASC")
    suspend fun findNarratedByMaxPriority(maxPriority: Int): List<SalemPoi>

    @Query("""
        SELECT * FROM salem_pois
        WHERE is_narrated = 1
        AND lat BETWEEN :latMin AND :latMax
        AND lng BETWEEN :lngMin AND :lngMax
        ORDER BY priority ASC
    """)
    suspend fun findNarratedInBbox(
        latMin: Double, latMax: Double, lngMin: Double, lngMax: Double
    ): List<SalemPoi>

    // ── Tour queries (replaces TourPoiDao) ──────────────────────────────────

    @Query("SELECT * FROM salem_pois WHERE is_tour_poi = 1 ORDER BY priority ASC, name ASC")
    suspend fun findTourPois(): List<SalemPoi>

    @Query("SELECT * FROM salem_pois WHERE is_tour_poi = 1 AND category = :category ORDER BY priority ASC, name ASC")
    suspend fun findTourPoisByCategory(category: String): List<SalemPoi>

    // ── Proximity search ────────────────────────────────────────────────────

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT *,
          ((:lat - lat) * (:lat - lat) + (:lng - lng) * (:lng - lng)) AS dist_sq
        FROM salem_pois
        WHERE lat BETWEEN :lat - :radiusDeg AND :lat + :radiusDeg
          AND lng BETWEEN :lng - :radiusDeg AND :lng + :radiusDeg
        ORDER BY dist_sq ASC
    """)
    suspend fun findNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemPoi>

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT *,
          ((:lat - lat) * (:lat - lat) + (:lng - lng) * (:lng - lng)) AS dist_sq
        FROM salem_pois
        WHERE is_narrated = 1
          AND lat BETWEEN :lat - :radiusDeg AND :lat + :radiusDeg
          AND lng BETWEEN :lng - :radiusDeg AND :lng + :radiusDeg
        ORDER BY dist_sq ASC
    """)
    suspend fun findNarratedNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemPoi>

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT *,
          ((:lat - lat) * (:lat - lat) + (:lng - lng) * (:lng - lng)) AS dist_sq
        FROM salem_pois
        WHERE default_visible = 1
          AND lat BETWEEN :lat - :radiusDeg AND :lat + :radiusDeg
          AND lng BETWEEN :lng - :radiusDeg AND :lng + :radiusDeg
        ORDER BY dist_sq ASC
    """)
    suspend fun findVisibleNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemPoi>

    // ── Text search ─────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM salem_pois
        WHERE name LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR short_description LIKE '%' || :query || '%'
        ORDER BY priority ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<SalemPoi>

    // ── District queries ────────────────────────────────────────���───────────

    @Query("SELECT * FROM salem_pois WHERE district = :district ORDER BY category ASC, name ASC")
    suspend fun findByDistrict(district: String): List<SalemPoi>

    @Query("SELECT DISTINCT district FROM salem_pois WHERE district IS NOT NULL ORDER BY district")
    suspend fun getDistricts(): List<String>

    // ── Counts ──────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM salem_pois")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM salem_pois WHERE is_narrated = 1")
    suspend fun countNarrated(): Int

    @Query("SELECT COUNT(*) FROM salem_pois WHERE is_narrated = 1 AND short_narration IS NOT NULL")
    suspend fun countWithNarration(): Int

    @Query("SELECT COUNT(*) FROM salem_pois WHERE is_narrated = 1 AND wave = :wave")
    suspend fun countByWave(wave: Int): Int

    @Query("SELECT COUNT(*) FROM salem_pois WHERE default_visible = 1")
    suspend fun countVisible(): Int

    // ── Bulk insert (for content pipeline) ──────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<SalemPoi>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(poi: SalemPoi)

    @Query("DELETE FROM salem_pois")
    suspend fun deleteAll()
}
