/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * S274 — One row per (collection, POI) pair in a baked Katrina's Collection
 * list. Renamed from PoiPassport (S268) as part of the "Katrina's Collection"
 * rebrand.
 *
 * Operator authors collections in PG (`salem_collections`); the publish chain
 * (`cache-proxy/scripts/publish-poi-collection.js`) runs each collection's
 * SQL against `salem_pois`, then writes the results here so the runtime can
 * render the CollectionSheet without any network or runtime SQL.
 *
 * One row per (collection_id, poi_id) — the same POI can appear in multiple
 * collections (Global Salem + Heritage Trail), and we render each context
 * independently against the single global visit log in
 * [com.example.wickedsalemwitchcitytour.userdata.db.PoiVisit].
 *
 * Denormalized fields (collection_name, poi_name, poi_lat, poi_lng,
 * poi_category) let the CollectionSheet render entirely off this one table
 * without joining back to `salem_pois` at runtime.
 *
 * HIST_BLDG entries render with bespoke ghost art (Phase 2 work, ~107 POIs);
 * other entries render with category-default art.
 */
@Entity(
    tableName = "collection_entry",
    primaryKeys = ["collection_id", "poi_id"],
    indices = [
        Index(value = ["collection_id", "display_order"]),
        Index(value = ["tour_id"]),
        Index(value = ["poi_id"])
    ]
)
data class CollectionEntry(
    /** Matches `salem_collections.id` — e.g. `default_salem_walking`. */
    @ColumnInfo(name = "collection_id") val collectionId: String,

    /** Denormalized collection name for header display ("Default Salem Walking"). */
    @ColumnInfo(name = "collection_name") val collectionName: String,

    /** Optional `salem_tours.id` if this collection is bound to a tour; NULL = global. */
    @ColumnInfo(name = "tour_id") val tourId: String? = null,

    /** Matches `salem_pois.id`. */
    @ColumnInfo(name = "poi_id") val poiId: String,

    /** Display order within this collection (operator can re-sort via the admin UI). */
    @ColumnInfo(name = "display_order") val displayOrder: Int,

    @ColumnInfo(name = "poi_name") val poiName: String,
    @ColumnInfo(name = "poi_lat") val poiLat: Double,
    @ColumnInfo(name = "poi_lng") val poiLng: Double,
    @ColumnInfo(name = "poi_category") val poiCategory: String,

    // ── Katrina's Collection ghost (S275 — Phase 2) ──
    // Denormalized from salem_pois so CollectionSheet renders the badge grid
    // entirely off this one table without joining back at runtime. NULL for
    // non-HIST_BLDG entries (Phase 2 generated 107 paired portraits only).
    @ColumnInfo(name = "ghost_asset_a") val ghostAssetA: String? = null,
    @ColumnInfo(name = "ghost_asset_b") val ghostAssetB: String? = null,
    @ColumnInfo(name = "ghost_frame")   val ghostFrame:  String? = null
)
