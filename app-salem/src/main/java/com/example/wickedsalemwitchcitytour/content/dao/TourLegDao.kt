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
import com.example.wickedsalemwitchcitytour.content.model.TourLeg

@Dao
interface TourLegDao {

    @Query("SELECT * FROM tour_legs WHERE tour_id = :tourId ORDER BY from_stop_order ASC")
    suspend fun findByTour(tourId: String): List<TourLeg>
}
