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
 * Enhanced business listings — restaurants, bars, cafes, lodging, shops, attractions.
 * Hundreds of entries covering Salem's tourist-facing businesses.
 */
@Entity(tableName = "salem_businesses")
data class SalemBusiness(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    /** restaurant|bar|cafe|lodging|shop_occult|shop_retail|shop_gift|attraction|event_venue|service */
    @ColumnInfo(name = "business_type") val businessType: String,
    @ColumnInfo(name = "cuisine_type") val cuisineType: String? = null,
    /** $|$$|$$$|$$$$ */
    @ColumnInfo(name = "price_range") val priceRange: String? = null,
    val hours: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val description: String? = null,
    /** Many Salem businesses have historical connections */
    @ColumnInfo(name = "historical_note") val historicalNote: String? = null,
    /** JSON array of tag strings */
    val tags: String? = null,
    val rating: Float? = null,
    @ColumnInfo(name = "image_asset") val imageAsset: String? = null
)
