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
    @ColumnInfo(name = "distance_m_from_prev") val distanceMFromPrev: Int? = null,

    // --- Provenance & Staleness ---
    /** manual_curated|salem_project|overpass_import|api_sync|user_report */
    @ColumnInfo(name = "data_source") val dataSource: String = "manual_curated",
    /** 0.0–1.0 trust score */
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    /** Epoch millis when this record becomes stale (0 = never) */
    @ColumnInfo(name = "stale_after") val staleAfter: Long = 0L
)
