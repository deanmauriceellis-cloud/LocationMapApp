/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.userdata.db.PoiVisit

/**
 * S274 — Storage interface for [PoiVisit] rows. POI-keyed only.
 *
 * Renamed from PassportVisitDao (S268) for the Katrina's Collection rebrand.
 *
 * Visit-recording is fire-and-forget from the narration path:
 *   [com.example.wickedsalemwitchcitytour.narration.NarrationGeofenceManager]
 *   ENTRY → `recordHeard(poiId, now)`
 *
 * Reset flow (CollectionSheet overflow menu):
 *   "Reset Collection" → confirm dialog → [deleteAll].
 */
@Dao
interface PoiVisitDao {

    /**
     * UPSERT a heard event. Caller computes `firstHeardAtMs` as
     * `COALESCE(existingFirst, nowMs)` outside the DAO so we don't need a
     * SQLite-side IIF expression.
     */
    @Query(
        """
        INSERT INTO poi_visit (poi_id, first_heard_at_ms, last_heard_at_ms, heard_count)
        VALUES (:poiId, :nowMs, :nowMs, 1)
        ON CONFLICT(poi_id) DO UPDATE SET
            last_heard_at_ms = :nowMs,
            heard_count      = heard_count + 1
        """
    )
    suspend fun recordHeard(poiId: String, nowMs: Long)

    @Query("SELECT * FROM poi_visit WHERE poi_id = :poiId")
    suspend fun findByPoi(poiId: String): PoiVisit?

    @Query("SELECT * FROM poi_visit ORDER BY last_heard_at_ms DESC")
    suspend fun listAll(): List<PoiVisit>

    /** Used for "X / Y collected" — how many of the given POI IDs have been heard. */
    @Query("SELECT COUNT(*) FROM poi_visit WHERE poi_id IN (:poiIds)")
    suspend fun countHeardAmong(poiIds: List<String>): Int

    @Query("SELECT poi_id FROM poi_visit WHERE poi_id IN (:poiIds)")
    suspend fun listHeardAmong(poiIds: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM poi_visit")
    suspend fun count(): Int

    /** Reset entire collection — wipes every heard flag. Operator-triggered only. */
    @Query("DELETE FROM poi_visit")
    suspend fun deleteAll(): Int
}
