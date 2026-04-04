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
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
)
