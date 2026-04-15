/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsArticle

@Dao
interface WitchTrialsArticleDao {

    @Query("SELECT * FROM salem_witch_trials_articles ORDER BY tile_order ASC")
    suspend fun findAll(): List<WitchTrialsArticle>

    @Query("SELECT * FROM salem_witch_trials_articles WHERE id = :id")
    suspend fun findById(id: String): WitchTrialsArticle?

    @Query("SELECT * FROM salem_witch_trials_articles WHERE tile_order = :order")
    suspend fun findByOrder(order: Int): WitchTrialsArticle?

    @Query("SELECT COUNT(*) FROM salem_witch_trials_articles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<WitchTrialsArticle>)

    @Query("DELETE FROM salem_witch_trials_articles")
    suspend fun deleteAll()
}
