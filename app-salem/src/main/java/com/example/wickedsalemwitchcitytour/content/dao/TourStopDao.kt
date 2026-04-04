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

    /** Get the full TourPoi objects for a tour, ordered by stop_order */
    @Query("""
        SELECT p.* FROM tour_pois p
        INNER JOIN tour_stops s ON p.id = s.poi_id
        WHERE s.tour_id = :tourId
        ORDER BY s.stop_order ASC
    """)
    suspend fun findTourPoisByTour(tourId: String): List<TourPoi>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<TourStop>)
}
