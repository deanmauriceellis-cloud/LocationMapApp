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
    @ColumnInfo(name = "image_asset") val imageAsset: String? = null,

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
