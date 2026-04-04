/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.example.wickedsalemwitchcitytour.content.model.TourPoi

@Dao
interface TourPoiDao {

    @Query("SELECT * FROM tour_pois WHERE id = :id")
    suspend fun findById(id: String): TourPoi?

    @Query("SELECT * FROM tour_pois WHERE category = :category ORDER BY priority ASC, name ASC")
    suspend fun findByCategory(category: String): List<TourPoi>

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT *,
          ((:lat - lat) * (:lat - lat) + (:lng - lng) * (:lng - lng)) AS dist_sq
        FROM tour_pois
        WHERE lat BETWEEN :lat - :radiusDeg AND :lat + :radiusDeg
          AND lng BETWEEN :lng - :radiusDeg AND :lng + :radiusDeg
        ORDER BY dist_sq ASC
    """)
    suspend fun findNearby(lat: Double, lng: Double, radiusDeg: Double): List<TourPoi>

    @Query("SELECT * FROM tour_pois WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY priority ASC")
    suspend fun search(query: String): List<TourPoi>

    @Query("SELECT * FROM tour_pois ORDER BY priority ASC, name ASC")
    suspend fun findAll(): List<TourPoi>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<TourPoi>)
}
