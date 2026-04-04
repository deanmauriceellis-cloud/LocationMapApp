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

/**
 * Tour-to-POI join table with ordering — links tours to their stops.
 */
@Entity(
    tableName = "tour_stops",
    primaryKeys = ["tour_id", "poi_id"]
)
data class TourStop(
    @ColumnInfo(name = "tour_id") val tourId: String,
    @ColumnInfo(name = "poi_id") val poiId: String,
    @ColumnInfo(name = "stop_order") val stopOrder: Int,
    /** TTS narration for the walk between the previous stop and this one */
    @ColumnInfo(name = "transition_narration") val transitionNarration: String? = null,
    @ColumnInfo(name = "walking_minutes_from_prev") val walkingMinutesFromPrev: Int? = null,
    @ColumnInfo(name = "distance_m_from_prev") val distanceMFromPrev: Int? = null
)
