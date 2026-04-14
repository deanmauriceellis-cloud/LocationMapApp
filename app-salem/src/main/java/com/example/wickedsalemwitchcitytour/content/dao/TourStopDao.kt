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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.content.model.TourStop

@Dao
interface TourStopDao {

    @Query("SELECT * FROM tour_stops WHERE tour_id = :tourId ORDER BY stop_order ASC")
    suspend fun findByTour(tourId: String): List<TourStop>

    @Query("SELECT * FROM tour_stops WHERE poi_id = :poiId")
    suspend fun findByPoi(poiId: String): List<TourStop>

    /**
     * Get the full TourPoi objects for a tour, ordered by stop_order.
     *
     * S125: Reads from the unified `salem_pois` table instead of the legacy
     * `tour_pois`. Phase 9U copied all tour-stop content into salem_pois but
     * did not backfill the tour_pois table, so the old INNER JOIN against
     * tour_pois returned only 4 of the 10 Heritage Trail stops — the tour
     * screen silently dropped the other 6 (SalemMainActivityTour.kt:617
     * skips stops whose POI isn't in activeTour.pois).
     *
     * Projection supplies defaults for the four columns `tour_pois` carried
     * that `salem_pois` doesn't (verified_date, created_at, updated_at,
     * stale_after). Once the legacy `tour_pois` table is dropped, this is
     * the only join needed for the tour UI.
     */
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
        INNER JOIN tour_stops s ON p.id = s.poi_id
        WHERE s.tour_id = :tourId
        ORDER BY s.stop_order ASC
    """)
    suspend fun findTourPoisByTour(tourId: String): List<TourPoi>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<TourStop>)
}
