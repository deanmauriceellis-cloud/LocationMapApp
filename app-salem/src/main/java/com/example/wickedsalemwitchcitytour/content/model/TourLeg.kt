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

/**
 * S185 — Pre-computed walking polyline between two consecutive tour stops.
 *
 * Authored via the web admin tool (S183/S184) into PG `salem_tour_legs`,
 * baked into `salem_content.db` by `cache-proxy/scripts/publish-tour-legs.js`,
 * read at runtime by [com.example.wickedsalemwitchcitytour.content.dao.TourLegDao]
 * so the tour player can render the polyline + distance/duration without
 * running the on-device router.
 *
 * `geometry` matches the bundle's `edges.geom_polyline` text format
 * (`"lat,lng;lat,lng;..."`) for parser parity with the runtime Router.
 */
@Entity(
    tableName = "tour_legs",
    primaryKeys = ["tour_id", "from_stop_order"]
)
data class TourLeg(
    @ColumnInfo(name = "tour_id") val tourId: String,
    @ColumnInfo(name = "from_stop_order") val fromStopOrder: Int,
    @ColumnInfo(name = "to_stop_order") val toStopOrder: Int,
    @ColumnInfo(name = "from_poi_id") val fromPoiId: String? = null,
    @ColumnInfo(name = "to_poi_id") val toPoiId: String? = null,
    @ColumnInfo(name = "from_lat") val fromLat: Double? = null,
    @ColumnInfo(name = "from_lng") val fromLng: Double? = null,
    @ColumnInfo(name = "to_lat") val toLat: Double? = null,
    @ColumnInfo(name = "to_lng") val toLng: Double? = null,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "duration_s") val durationS: Double,
    @ColumnInfo(name = "edge_count") val edgeCount: Int = 0,
    val geometry: String,

    @ColumnInfo(name = "data_source") val dataSource: String = "pg_admin_curated",
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "stale_after") val staleAfter: Long = 0L
)
