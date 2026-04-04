/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Haunted Happenings, museum exhibits, shows, seasonal events.
 */
@Entity(tableName = "events_calendar")
data class EventsCalendar(
    @PrimaryKey val id: String,
    val name: String,
    /** FK to tour_pois.id */
    @ColumnInfo(name = "venue_poi_id") val venuePoiId: String? = null,
    /** haunted_tour|museum_exhibit|show|festival|market|parade|special_event */
    @ColumnInfo(name = "event_type") val eventType: String,
    val description: String? = null,
    @ColumnInfo(name = "start_date") val startDate: String? = null,
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    val hours: String? = null,
    val admission: String? = null,
    val website: String? = null,
    val recurring: Boolean = false,
    @ColumnInfo(name = "recurrence_pattern") val recurrencePattern: String? = null,
    /** 10 for October events, null for year-round */
    @ColumnInfo(name = "seasonal_month") val seasonalMonth: Int? = null
)
