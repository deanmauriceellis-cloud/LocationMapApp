/*
 * WickedSalemWitchCityTour v1.5 — Phase 9U (Session 118)
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
 * Unified POI entity — the single source of truth for all Salem points of interest.
 * Replaces the three legacy tables (tour_pois, salem_businesses, narration_points)
 * in PG; coexists with them in Room until consumer migration is complete.
 *
 * 2,190 rows: 817 narrated + 133 business-only + 26 tour-only + 1,214 BCS imports.
 * All data is bundled offline in the APK — no server calls at runtime.
 */
@Entity(tableName = "salem_pois")
data class SalemPoi(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val status: String? = "open",

    // ── Taxonomy ──
    val category: String,
    val subcategory: String? = null,

    // ── Narration (geofence-triggered TTS content) ──
    @ColumnInfo(name = "short_narration") val shortNarration: String? = null,
    @ColumnInfo(name = "long_narration") val longNarration: String? = null,
    @ColumnInfo(name = "historical_narration") val historicalNarration: String? = null,

    // ── Geofence ──
    @ColumnInfo(name = "geofence_radius_m") val geofenceRadiusM: Int = 40,
    @ColumnInfo(name = "geofence_shape") val geofenceShape: String = "circle",
    @ColumnInfo(name = "corridor_points") val corridorPoints: String? = null,

    // ── Tour orchestration ──
    val priority: Int = 3,
    val wave: Int? = null,
    @ColumnInfo(name = "voice_clip_asset") val voiceClipAsset: String? = null,
    @ColumnInfo(name = "custom_voice_asset") val customVoiceAsset: String? = null,

    // ── Business metadata ──
    @ColumnInfo(name = "cuisine_type") val cuisineType: String? = null,
    @ColumnInfo(name = "price_range") val priceRange: String? = null,
    val rating: Float? = null,
    @ColumnInfo(name = "merchant_tier") val merchantTier: Int = 0,
    @ColumnInfo(name = "ad_priority") val adPriority: Int = 0,

    // ── Historical ──
    @ColumnInfo(name = "historical_period") val historicalPeriod: String? = null,
    @ColumnInfo(name = "historical_note") val historicalNote: String? = null,
    @ColumnInfo(name = "admission_info") val admissionInfo: String? = null,
    @ColumnInfo(name = "requires_transportation") val requiresTransportation: Boolean = false,
    @ColumnInfo(name = "wheelchair_accessible") val wheelchairAccessible: Boolean = true,
    val seasonal: Boolean = false,

    // ── Contact & hours ──
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val hours: String? = null,
    @ColumnInfo(name = "hours_text") val hoursText: String? = null,
    @ColumnInfo(name = "menu_url") val menuUrl: String? = null,
    @ColumnInfo(name = "reservations_url") val reservationsUrl: String? = null,
    @ColumnInfo(name = "order_url") val orderUrl: String? = null,

    // ── Content ──
    val description: String? = null,
    @ColumnInfo(name = "short_description") val shortDescription: String? = null,
    @ColumnInfo(name = "custom_description") val customDescription: String? = null,
    @ColumnInfo(name = "origin_story") val originStory: String? = null,
    @ColumnInfo(name = "image_asset") val imageAsset: String? = null,
    @ColumnInfo(name = "custom_icon_asset") val customIconAsset: String? = null,
    @ColumnInfo(name = "action_buttons") val actionButtons: String? = null,

    // ── BCS enrichment (JSON text) ──
    @ColumnInfo(name = "secondary_categories") val secondaryCategories: String? = null,
    val specialties: String? = null,
    val owners: String? = null,
    @ColumnInfo(name = "year_established") val yearEstablished: Int? = null,
    val amenities: String? = null,
    val district: String? = null,

    // ── Relations (JSON arrays of IDs) ──
    @ColumnInfo(name = "related_figure_ids") val relatedFigureIds: String? = null,
    @ColumnInfo(name = "related_fact_ids") val relatedFactIds: String? = null,
    @ColumnInfo(name = "related_source_ids") val relatedSourceIds: String? = null,

    // ── Provenance ──
    @ColumnInfo(name = "data_source") val dataSource: String = "manual_curated",
    val confidence: Float = 0.8f,

    // ── Flags ──
    @ColumnInfo(name = "is_tour_poi") val isTourPoi: Boolean = false,
    @ColumnInfo(name = "is_civic_poi") val isCivicPoi: Boolean = false,
    @ColumnInfo(name = "is_narrated") val isNarrated: Boolean = false,
    @ColumnInfo(name = "default_visible") val defaultVisible: Boolean = true,
    /**
     * S125: precomputed flag — TRUE iff the POI has any text the narration
     * engine would actually speak (historical_note / short_narration /
     * description). Populated by `cache-proxy/scripts/flag-narration-status.js`
     * and propagated via publish-salem-pois.js. Runtime does the same null
     * check at enqueue time; this column is for reports, admin-tool
     * filtering, and future bulk-generation targeting.
     */
    @ColumnInfo(name = "has_announce_narration") val hasAnnounceNarration: Boolean = false,

    // ── Phase 9Y — MassGIS / MHC Inventory / L3 parcel enrichment ──
    // Populated by cache-proxy/scripts/enrich-pois-from-massgis.js (9Y.3).
    // Consumers: polygon geofence runtime (9Y.9), MHC narrative UI, parcel-owner filters.
    @ColumnInfo(name = "building_footprint_geojson") val buildingFootprintGeojson: String? = null,
    @ColumnInfo(name = "mhc_id") val mhcId: String? = null,
    @ColumnInfo(name = "mhc_year_built") val mhcYearBuilt: Int? = null,
    @ColumnInfo(name = "mhc_style") val mhcStyle: String? = null,
    @ColumnInfo(name = "mhc_nr_status") val mhcNrStatus: String? = null,
    @ColumnInfo(name = "mhc_narrative") val mhcNarrative: String? = null,
    @ColumnInfo(name = "canonical_address_point_id") val canonicalAddressPointId: String? = null,
    @ColumnInfo(name = "local_historic_district") val localHistoricDistrict: String? = null,
    @ColumnInfo(name = "parcel_owner_class") val parcelOwnerClass: String? = null
)
