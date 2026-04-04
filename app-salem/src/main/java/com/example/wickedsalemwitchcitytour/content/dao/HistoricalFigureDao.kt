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
import com.example.wickedsalemwitchcitytour.content.model.HistoricalFigure

@Dao
interface HistoricalFigureDao {

    @Query("SELECT * FROM historical_figures ORDER BY surname ASC")
    suspend fun findAll(): List<HistoricalFigure>

    @Query("SELECT * FROM historical_figures WHERE id = :id")
    suspend fun findById(id: String): HistoricalFigure?

    @Query("SELECT * FROM historical_figures WHERE primary_poi_id = :poiId")
    suspend fun findByPoi(poiId: String): List<HistoricalFigure>

    @Query("SELECT * FROM historical_figures WHERE role = :role ORDER BY surname ASC")
    suspend fun findByRole(role: String): List<HistoricalFigure>

    @Query("SELECT * FROM historical_figures WHERE name LIKE '%' || :query || '%' OR short_bio LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<HistoricalFigure>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(figures: List<HistoricalFigure>)
}
