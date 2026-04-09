/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.userdata.db.PoiEncounter

/**
 * Storage interface for [PoiEncounter] rows.
 *
 * All callers should go through
 * [com.example.wickedsalemwitchcitytour.userdata.PoiEncounterTracker]
 * rather than touching this DAO directly — the tracker owns the in-memory
 * "active encounter" map and is the single source of truth for what's open.
 *
 * S110 — POI proximity tracking.
 */
@Dao
interface PoiEncounterDao {

    /**
     * INSERT OR REPLACE on the composite primary key (`poi_id|first_seen_at_ms`).
     * The tracker calls this on every GPS fix during an active encounter to
     * update the running min/max/duration/fixCount values.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(encounter: PoiEncounter)

    /**
     * All encounters where `last_seen_at_ms >= sinceMs`, ordered most-recent first.
     * Used for review queries like "what POIs did I get close to today?"
     */
    @Query("SELECT * FROM poi_encounters WHERE last_seen_at_ms >= :sinceMs ORDER BY first_seen_at_ms DESC")
    suspend fun getRecent(sinceMs: Long): List<PoiEncounter>

    /** Most recent N encounters by start time. */
    @Query("SELECT * FROM poi_encounters ORDER BY first_seen_at_ms DESC LIMIT :limit")
    suspend fun getMostRecent(limit: Int): List<PoiEncounter>

    /**
     * Closest-approach summary for the given POI ID — useful for "have I been
     * here before?" queries. Returns null if no encounters exist.
     */
    @Query("SELECT * FROM poi_encounters WHERE poi_id = :poiId ORDER BY min_distance_m ASC LIMIT 1")
    suspend fun getClosestApproachForPoi(poiId: String): PoiEncounter?

    /** Hard-delete encounters older than the cutoff. Returns the deleted count. */
    @Query("DELETE FROM poi_encounters WHERE last_seen_at_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM poi_encounters")
    suspend fun count(): Int

    @Query("DELETE FROM poi_encounters")
    suspend fun deleteAll(): Int
}
