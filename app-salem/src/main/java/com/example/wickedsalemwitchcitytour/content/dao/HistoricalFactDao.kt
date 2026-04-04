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
import com.example.wickedsalemwitchcitytour.content.model.HistoricalFact

@Dao
interface HistoricalFactDao {

    @Query("SELECT * FROM historical_facts WHERE poi_id = :poiId ORDER BY date ASC")
    suspend fun findByPoi(poiId: String): List<HistoricalFact>

    @Query("SELECT * FROM historical_facts WHERE figure_id = :figureId ORDER BY date ASC")
    suspend fun findByFigure(figureId: String): List<HistoricalFact>

    @Query("SELECT * FROM historical_facts WHERE category = :category ORDER BY date ASC")
    suspend fun findByCategory(category: String): List<HistoricalFact>

    @Query("SELECT * FROM historical_facts WHERE date LIKE :datePrefix || '%' ORDER BY date ASC")
    suspend fun findByDate(datePrefix: String): List<HistoricalFact>

    @Query("SELECT * FROM historical_facts WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<HistoricalFact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facts: List<HistoricalFact>)

    // --- Provenance & Staleness ---

    @Query("SELECT * FROM historical_facts WHERE stale_after > 0 AND stale_after < :now ORDER BY stale_after ASC")
    suspend fun findStale(now: Long): List<HistoricalFact>

    @Query("SELECT * FROM historical_facts WHERE data_source = :source ORDER BY date ASC")
    suspend fun findBySource(source: String): List<HistoricalFact>

    @Query("UPDATE historical_facts SET updated_at = :now WHERE id = :id")
    suspend fun markUpdated(id: String, now: Long)
}
