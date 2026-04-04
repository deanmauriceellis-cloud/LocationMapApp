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
import com.example.wickedsalemwitchcitytour.content.model.PrimarySource

@Dao
interface PrimarySourceDao {

    @Query("SELECT * FROM primary_sources WHERE figure_id = :figureId ORDER BY date ASC")
    suspend fun findByFigure(figureId: String): List<PrimarySource>

    @Query("SELECT * FROM primary_sources WHERE poi_id = :poiId ORDER BY date ASC")
    suspend fun findByPoi(poiId: String): List<PrimarySource>

    @Query("SELECT * FROM primary_sources WHERE source_type = :type ORDER BY date ASC")
    suspend fun findByType(type: String): List<PrimarySource>

    @Query("SELECT * FROM primary_sources WHERE title LIKE '%' || :query || '%' OR excerpt LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<PrimarySource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<PrimarySource>)
}
