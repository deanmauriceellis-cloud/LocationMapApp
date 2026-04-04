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
 * Tour-worthy points of interest with narration — ~50-100 curated locations.
 * These are the stops on walking tours: memorials, museums, historic sites, etc.
 */
@Entity(tableName = "tour_pois")
data class TourPoi(
    @PrimaryKey val id: String,
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
    val priority: Int = 3
)
