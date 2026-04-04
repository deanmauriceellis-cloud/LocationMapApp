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
import com.example.wickedsalemwitchcitytour.content.model.Tour

@Dao
interface TourDao {

    @Query("SELECT * FROM tours ORDER BY sort_order ASC")
    suspend fun findAll(): List<Tour>

    @Query("SELECT * FROM tours WHERE id = :id")
    suspend fun findById(id: String): Tour?

    @Query("SELECT * FROM tours WHERE seasonal = :seasonal ORDER BY sort_order ASC")
    suspend fun findBySeason(seasonal: Boolean): List<Tour>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tours: List<Tour>)
}
