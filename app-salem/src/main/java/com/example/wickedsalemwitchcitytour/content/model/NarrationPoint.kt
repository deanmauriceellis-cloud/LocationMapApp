/*
 * WickedSalemApp v1.5
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
 * A narration-enabled point in downtown Salem's ambient content layer.
 * Unlike TourPoi (linear tour stops), NarrationPoints trigger by proximity
 * regardless of route — the city narrates itself as you walk.
 *
 * Phase 9T: Salem Walking Tour Restructure
 */
@Entity(tableName = "narration_points")
data class NarrationPoint(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,

    /** Type classification for UI display and grouping */
    val type: String,  // witch_museum, witch_shop, psychic, ghost_tour, haunted_attraction,
                       // museum, historic_site, public_art, cemetery, tour, attraction,
                       // park, place_of_worship, lodging, restaurant, cafe, bar, etc.

    /** Short narration for APPROACH trigger (~50-100 words, 15-30 sec TTS) */
    @ColumnInfo(name = "short_narration") val shortNarration: String? = null,

    /** Long narration for ENTRY trigger (~200-400 words, 60-120 sec TTS) */
    @ColumnInfo(name = "long_narration") val longNarration: String? = null,

    /** Description for display (may differ from TTS-optimized narration) */
    val description: String? = null,

    /** Image asset filename in assets/poi-icons/{type}/ — randomly assigned from category */
    @ColumnInfo(name = "image_asset") val imageAsset: String? = null,

    /** Voice clip asset path in assets/audio/voices/{character}/{type}_{n}.mp3 */
    @ColumnInfo(name = "voice_clip_asset") val voiceClipAsset: String? = null,

    /** Geofence radius in meters (20-50m, smaller = denser areas) */
    @ColumnInfo(name = "geofence_radius_m") val geofenceRadiusM: Int = 40,

    /** Geofence shape: "circle" for point locations, "corridor" for streets */
    @ColumnInfo(name = "geofence_shape") val geofenceShape: String = "circle",

    /** Corridor polyline as JSON array of [lat,lng] pairs (only for shape=corridor) */
    @ColumnInfo(name = "corridor_points") val corridorPoints: String? = null,

    /** Narration priority: 1=must-hear, 2=important, 3=interesting, 4=minor, 5=filler */
    val priority: Int = 3,

    /** Narration wave: 1=launch set (113), 2=tourist services, 3=dining/civic */
    val wave: Int = 1,

    /** JSON array of related historical figure IDs */
    @ColumnInfo(name = "related_figure_ids") val relatedFigureIds: String? = null,

    /** JSON array of related historical fact IDs */
    @ColumnInfo(name = "related_fact_ids") val relatedFactIds: String? = null,

    /** JSON array of related primary source IDs */
    @ColumnInfo(name = "related_source_ids") val relatedSourceIds: String? = null,

    /** JSON array of action button configs for the narration dialog */
    @ColumnInfo(name = "action_buttons") val actionButtons: String? = null,

    /** Phone number */
    val phone: String? = null,
    /** Website URL */
    val website: String? = null,
    /** Operating hours */
    val hours: String? = null,

    // ── Merchant override fields (future monetization) ──
    @ColumnInfo(name = "merchant_tier") val merchantTier: String? = null,
    @ColumnInfo(name = "ad_priority") val adPriority: Int = 0,
    @ColumnInfo(name = "custom_icon_asset") val customIconAsset: String? = null,
    @ColumnInfo(name = "custom_voice_asset") val customVoiceAsset: String? = null,
    @ColumnInfo(name = "custom_description") val customDescription: String? = null,

    // ── Provenance ──
    @ColumnInfo(name = "data_source") val dataSource: String = "manual_curated",
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "verified_date") val verifiedDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "stale_after") val staleAfter: Long = 0L
)
