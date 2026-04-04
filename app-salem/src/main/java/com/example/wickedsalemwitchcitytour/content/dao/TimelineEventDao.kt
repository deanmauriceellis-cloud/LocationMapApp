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
}
