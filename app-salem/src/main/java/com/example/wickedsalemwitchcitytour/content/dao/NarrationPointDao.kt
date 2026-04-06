/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.*
import com.example.wickedsalemwitchcitytour.content.model.NarrationPoint

@Dao
interface NarrationPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<NarrationPoint>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: NarrationPoint)

    @Query("SELECT * FROM narration_points ORDER BY priority ASC, name ASC")
    suspend fun getAll(): List<NarrationPoint>

    @Query("SELECT * FROM narration_points WHERE id = :id")
    suspend fun findById(id: String): NarrationPoint?

    @Query("SELECT * FROM narration_points WHERE type = :type ORDER BY priority ASC")
    suspend fun findByType(type: String): List<NarrationPoint>

    @Query("SELECT * FROM narration_points WHERE wave = :wave ORDER BY priority ASC, name ASC")
    suspend fun findByWave(wave: Int): List<NarrationPoint>

    @Query("SELECT * FROM narration_points WHERE priority <= :maxPriority ORDER BY priority ASC")
    suspend fun findByMaxPriority(maxPriority: Int): List<NarrationPoint>

    /** Find narration points within a geographic bounding box (for viewport filtering) */
    @Query("""
        SELECT * FROM narration_points
        WHERE lat BETWEEN :latMin AND :latMax
        AND lng BETWEEN :lngMin AND :lngMax
        ORDER BY priority ASC
    """)
    suspend fun findInBbox(latMin: Double, latMax: Double, lngMin: Double, lngMax: Double): List<NarrationPoint>

    /** Proximity search — find points within approximate radius (degree-based) */
    @Query("""
        SELECT *,
        ((lat - :lat) * (lat - :lat) + (lng - :lng) * (lng - :lng)) AS dist_sq
        FROM narration_points
        WHERE lat BETWEEN (:lat - :radiusDeg) AND (:lat + :radiusDeg)
        AND lng BETWEEN (:lng - :radiusDeg) AND (:lng + :radiusDeg)
        ORDER BY dist_sq ASC
    """)
    suspend fun findNearby(lat: Double, lng: Double, radiusDeg: Double): List<NarrationPoint>

    /** Text search across name and description */
    @Query("""
        SELECT * FROM narration_points
        WHERE name LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY priority ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<NarrationPoint>

    /** Count wave 1 narration points */
    @Query("SELECT COUNT(*) FROM narration_points WHERE wave = :wave")
    suspend fun countByWave(wave: Int): Int

    @Query("SELECT COUNT(*) FROM narration_points")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM narration_points WHERE short_narration IS NOT NULL")
    suspend fun countWithNarration(): Int

    @Delete
    suspend fun delete(point: NarrationPoint)

    @Query("DELETE FROM narration_points")
    suspend fun deleteAll()
}
