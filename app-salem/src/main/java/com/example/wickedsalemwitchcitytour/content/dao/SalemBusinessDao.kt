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
import com.example.wickedsalemwitchcitytour.content.model.SalemBusiness

@Dao
interface SalemBusinessDao {

    @Query("SELECT * FROM salem_businesses WHERE business_type = :type ORDER BY name ASC")
    suspend fun findByType(type: String): List<SalemBusiness>

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT *,
          ((:lat - lat) * (:lat - lat) + (:lng - lng) * (:lng - lng)) AS dist_sq
        FROM salem_businesses
        WHERE lat BETWEEN :lat - :radiusDeg AND :lat + :radiusDeg
          AND lng BETWEEN :lng - :radiusDeg AND :lng + :radiusDeg
        ORDER BY dist_sq ASC
    """)
    suspend fun findNearby(lat: Double, lng: Double, radiusDeg: Double): List<SalemBusiness>

    @Query("SELECT * FROM salem_businesses WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<SalemBusiness>

    @Query("SELECT * FROM salem_businesses WHERE tags LIKE '%' || :tag || '%' ORDER BY name ASC")
    suspend fun findByTag(tag: String): List<SalemBusiness>

    @Query("SELECT * FROM salem_businesses ORDER BY name ASC")
    suspend fun findAll(): List<SalemBusiness>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(businesses: List<SalemBusiness>)
}
