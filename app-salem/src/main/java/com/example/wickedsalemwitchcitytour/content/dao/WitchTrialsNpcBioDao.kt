/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNpcBio

@Dao
interface WitchTrialsNpcBioDao {

    @Query("SELECT * FROM salem_witch_trials_npc_bios ORDER BY tier ASC, name ASC")
    suspend fun findAll(): List<WitchTrialsNpcBio>

    @Query("SELECT * FROM salem_witch_trials_npc_bios WHERE id = :id")
    suspend fun findById(id: String): WitchTrialsNpcBio?

    @Query("SELECT * FROM salem_witch_trials_npc_bios WHERE faction = :faction ORDER BY name ASC")
    suspend fun findByFaction(faction: String): List<WitchTrialsNpcBio>

    @Query("SELECT * FROM salem_witch_trials_npc_bios WHERE tier <= :maxTier ORDER BY tier ASC, name ASC")
    suspend fun findByMaxTier(maxTier: Int): List<WitchTrialsNpcBio>

    @Query("SELECT COUNT(*) FROM salem_witch_trials_npc_bios")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bios: List<WitchTrialsNpcBio>)

    @Query("DELETE FROM salem_witch_trials_npc_bios")
    suspend fun deleteAll()
}
