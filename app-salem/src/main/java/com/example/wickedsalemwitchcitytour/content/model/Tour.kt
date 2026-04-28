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
 * Pre-defined tour route definitions — 8-10 themed walking tours.
 */
@Entity(tableName = "tours")
data class Tour(
    @PrimaryKey val id: String,
    val name: String,
    val theme: String,
    val description: String,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Float,
    @ColumnInfo(name = "stop_count") val stopCount: Int,
    /** easy|moderate|challenging */
    val difficulty: String = "moderate",
    val seasonal: Boolean = false,
    @ColumnInfo(name = "icon_asset") val iconAsset: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    /**
     * S193: when true the tour runs in Historical Narration mode — the
     * narration script is `salem_pois.historical_narration` (strictly
     * pre-1860, no modern context). POIs without populated historical_narration
     * stay silent.
     */
    @ColumnInfo(name = "is_historical_tour") val isHistoricalTour: Boolean = false,

    // --- Provenance & Staleness ---
    /** manual_curated|salem_project|overpass_import|api_sync|user_report */
    @ColumnInfo(name = "data_source") val dataSource: String = "manual_curated",
    /** 0.0–1.0 trust score */
    val confidence: Float = 1.0f,
    /** ISO date of last human/automated verification */
    @ColumnInfo(name = "verified_date") val verifiedDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    /** Epoch millis when this record becomes stale (0 = never) */
    @ColumnInfo(name = "stale_after") val staleAfter: Long = 0L
)
