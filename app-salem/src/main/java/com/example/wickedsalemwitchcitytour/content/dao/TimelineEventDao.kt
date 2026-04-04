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
import com.example.wickedsalemwitchcitytour.content.model.TimelineEvent

@Dao
interface TimelineEventDao {

    @Query("SELECT * FROM timeline_events ORDER BY date ASC")
    suspend fun findAll(): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE is_anchor = 1 ORDER BY date ASC")
    suspend fun findAnchorEvents(): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE poi_id = :poiId ORDER BY date ASC")
    suspend fun findByPoi(poiId: String): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE crisis_phase = :phase ORDER BY date ASC")
    suspend fun findByPhase(phase: String): List<TimelineEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<TimelineEvent>)

    // --- Provenance & Staleness ---

    @Query("SELECT * FROM timeline_events WHERE stale_after > 0 AND stale_after < :now ORDER BY stale_after ASC")
    suspend fun findStale(now: Long): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE data_source = :source ORDER BY date ASC")
    suspend fun findBySource(source: String): List<TimelineEvent>

    @Query("UPDATE timeline_events SET updated_at = :now WHERE id = :id")
    suspend fun markUpdated(id: String, now: Long)
}
