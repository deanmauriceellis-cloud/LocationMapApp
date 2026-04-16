/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper

@Dao
interface WitchTrialsNewspaperDao {

    @Query("SELECT * FROM salem_witch_trials_newspapers ORDER BY date ASC")
    suspend fun findAll(): List<WitchTrialsNewspaper>

    @Query("SELECT * FROM salem_witch_trials_newspapers WHERE id = :id")
    suspend fun findById(id: String): WitchTrialsNewspaper?

    @Query("SELECT * FROM salem_witch_trials_newspapers WHERE date = :date")
    suspend fun findByDate(date: String): WitchTrialsNewspaper?

    @Query("SELECT * FROM salem_witch_trials_newspapers WHERE crisis_phase = :phase ORDER BY date ASC")
    suspend fun findByPhase(phase: Int): List<WitchTrialsNewspaper>

    @Query("SELECT * FROM salem_witch_trials_newspapers WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun findByDateRange(startDate: String, endDate: String): List<WitchTrialsNewspaper>

    /** Find newspapers matching a month-day pattern (e.g. "%-04-16" for April 16). */
    @Query("SELECT * FROM salem_witch_trials_newspapers WHERE date LIKE :monthDayPattern ORDER BY date ASC")
    suspend fun findByMonthDay(monthDayPattern: String): List<WitchTrialsNewspaper>

    @Query("SELECT COUNT(*) FROM salem_witch_trials_newspapers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(papers: List<WitchTrialsNewspaper>)

    @Query("DELETE FROM salem_witch_trials_newspapers")
    suspend fun deleteAll()
}
