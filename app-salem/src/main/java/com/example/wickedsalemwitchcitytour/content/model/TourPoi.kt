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

/**
 * Tour-stop projection. Phase 9U removed the legacy `tour_pois` table; this
 * type is now a Room result POJO produced by TourPoiDao + TourStopDao
 * projecting from `salem_pois`. No `@Entity` — the table is gone.
 */
data class TourPoi(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val category: String,
    /** JSON array of subcategory strings */
    val subcategories: String? = null,
    /** TTS-optimized, 15-30 sec (~50-100 words) — spoken on approach */
    @ColumnInfo(name = "short_narration") val shortNarration: String? = null,
    /** TTS-optimized, 60-120 sec (~200-400 words) — spoken when stopped at location */
    @ColumnInfo(name = "long_narration") val longNarration: String? = null,
    /** Text display description (can be longer/more detailed than narration) */
    val description: String? = null,
    @ColumnInfo(name = "historical_period") val historicalPeriod: String? = null,
    @ColumnInfo(name = "admission_info") val admissionInfo: String? = null,
    val hours: String? = null,
    val phone: String? = null,
    val website: String? = null,
    @ColumnInfo(name = "image_asset") val imageAsset: String? = null,
    @ColumnInfo(name = "geofence_radius_m") val geofenceRadiusM: Int = 50,
    @ColumnInfo(name = "requires_transportation") val requiresTransportation: Boolean = false,
    @ColumnInfo(name = "wheelchair_accessible") val wheelchairAccessible: Boolean = true,
    val seasonal: Boolean = false,
    /** 1 = must-see, 5 = minor interest */
    val priority: Int = 3,

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
