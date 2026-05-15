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
 * S268 — One row per (passport, POI) pair in a baked Passport list.
 *
 * Operator authors passport filters in PG (`salem_passport_filters`); the
 * publish chain (`cache-proxy/scripts/publish-poi-passport.js`) runs each
 * filter's SQL against `salem_pois`, then writes the results here so the
 * runtime can render the Passport sheet without any network or runtime SQL.
 *
 * One row per (passport_id, poi_id) — the same POI can appear in multiple
 * passports (Global Salem + Heritage Trail), and we render each context
 * independently against the single global visit log in
 * [com.example.wickedsalemwitchcitytour.userdata.db.PassportVisit].
 *
 * Denormalized fields (passport_name, poi_name, poi_lat, poi_lng,
 * poi_category) let the Passport sheet render entirely off this one table
 * without joining back to `salem_pois` at runtime.
 *
 * Replaces the four stops-based tour UIs (HUD banner, Active Tour dialog,
 * completion-stats body, numbered polyline pins) — see plan in
 * `docs/plans/poi-passport-replaces-tour-stops.md`.
 */
@Entity(
    tableName = "poi_passport",
    primaryKeys = ["passport_id", "poi_id"],
    indices = [
        Index(value = ["passport_id", "display_order"]),
        Index(value = ["tour_id"]),
        Index(value = ["poi_id"])
    ]
)
data class PoiPassport(
    /** Matches `salem_passport_filters.id` — e.g. `default_salem_walking`. */
    @ColumnInfo(name = "passport_id") val passportId: String,

    /** Denormalized filter name for header display ("Default Salem Walking"). */
    @ColumnInfo(name = "passport_name") val passportName: String,

    /** Optional `salem_tours.id` if this passport is bound to a tour; NULL = global. */
    @ColumnInfo(name = "tour_id") val tourId: String? = null,

    /** Matches `salem_pois.id`. */
    @ColumnInfo(name = "poi_id") val poiId: String,

    /** Display order within this passport (operator can re-sort via the admin UI). */
    @ColumnInfo(name = "display_order") val displayOrder: Int,

    @ColumnInfo(name = "poi_name") val poiName: String,
    @ColumnInfo(name = "poi_lat") val poiLat: Double,
    @ColumnInfo(name = "poi_lng") val poiLng: Double,
    @ColumnInfo(name = "poi_category") val poiCategory: String
)
