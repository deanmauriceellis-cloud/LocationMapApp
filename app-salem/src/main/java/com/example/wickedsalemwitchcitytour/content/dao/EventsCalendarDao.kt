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
import com.example.wickedsalemwitchcitytour.content.model.EventsCalendar

@Dao
interface EventsCalendarDao {

    @Query("SELECT * FROM events_calendar WHERE start_date >= :today OR end_date >= :today ORDER BY start_date ASC")
    suspend fun findUpcoming(today: String): List<EventsCalendar>

    @Query("SELECT * FROM events_calendar WHERE seasonal_month = :month ORDER BY start_date ASC")
    suspend fun findByMonth(month: Int): List<EventsCalendar>

    @Query("SELECT * FROM events_calendar WHERE venue_poi_id = :poiId ORDER BY start_date ASC")
    suspend fun findByVenue(poiId: String): List<EventsCalendar>

    @Query("SELECT * FROM events_calendar WHERE start_date <= :today AND (end_date >= :today OR end_date IS NULL) ORDER BY name ASC")
    suspend fun findActive(today: String): List<EventsCalendar>

    @Query("SELECT * FROM events_calendar ORDER BY start_date ASC")
    suspend fun findAll(): List<EventsCalendar>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventsCalendar>)
}
