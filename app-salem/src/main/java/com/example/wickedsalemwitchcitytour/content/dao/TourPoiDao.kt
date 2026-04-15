/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.content.model.TourPoi

/**
 * Phase 9U: legacy `tour_pois` table dropped. Surviving call sites
 * (TourEngine.getTourPoiById, TourEngine.getAllTourPois, TourViewModel)
 * read from `salem_pois` projected into TourPoi shape. Same projection
 * TourStopDao.findTourPoisByTour uses.
 */
@Dao
interface TourPoiDao {

    @Query("""
        SELECT
            p.id                       AS id,
            p.name                     AS name,
            p.lat                      AS lat,
            p.lng                      AS lng,
            COALESCE(p.address, '')    AS address,
            p.category                 AS category,
            p.subcategory              AS subcategories,
            p.short_narration          AS short_narration,
            p.long_narration           AS long_narration,
            p.description              AS description,
            p.historical_period        AS historical_period,
            p.admission_info           AS admission_info,
            p.hours                    AS hours,
            p.phone                    AS phone,
            p.website                  AS website,
            p.image_asset              AS image_asset,
            p.geofence_radius_m        AS geofence_radius_m,
            p.requires_transportation  AS requires_transportation,
            p.wheelchair_accessible    AS wheelchair_accessible,
            p.seasonal                 AS seasonal,
            p.priority                 AS priority,
            p.data_source              AS data_source,
            p.confidence               AS confidence,
            NULL                       AS verified_date,
            0                          AS created_at,
            0                          AS updated_at,
            0                          AS stale_after
        FROM salem_pois p
        WHERE p.id = :id
    """)
    suspend fun findById(id: String): TourPoi?

    @Query("""
        SELECT
            p.id                       AS id,
            p.name                     AS name,
            p.lat                      AS lat,
            p.lng                      AS lng,
            COALESCE(p.address, '')    AS address,
            p.category                 AS category,
            p.subcategory              AS subcategories,
            p.short_narration          AS short_narration,
            p.long_narration           AS long_narration,
            p.description              AS description,
            p.historical_period        AS historical_period,
            p.admission_info           AS admission_info,
            p.hours                    AS hours,
            p.phone                    AS phone,
            p.website                  AS website,
            p.image_asset              AS image_asset,
            p.geofence_radius_m        AS geofence_radius_m,
            p.requires_transportation  AS requires_transportation,
            p.wheelchair_accessible    AS wheelchair_accessible,
            p.seasonal                 AS seasonal,
            p.priority                 AS priority,
            p.data_source              AS data_source,
            p.confidence               AS confidence,
            NULL                       AS verified_date,
            0                          AS created_at,
            0                          AS updated_at,
            0                          AS stale_after
        FROM salem_pois p
        ORDER BY p.priority ASC, p.name ASC
    """)
    suspend fun findAll(): List<TourPoi>
}
