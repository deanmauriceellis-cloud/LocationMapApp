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
import com.example.wickedsalemwitchcitytour.userdata.db.GpsTrackPoint

/**
 * Storage interface for the user's GPS journey log.
 * Phase 9T+ GPS Journey feature (Session 109).
 *
 * All callers should go through [com.example.wickedsalemwitchcitytour.userdata.GpsTrackRecorder]
 * rather than touching the DAO directly — the recorder owns batching and pruning.
 */
@Dao
interface GpsTrackPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: GpsTrackPoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<GpsTrackPoint>)

    /**
     * All points with `ts_ms >= sinceMs`, ordered oldest → newest. Used to seed
     * the polyline overlay at app start.
     */
    @Query("SELECT * FROM gps_track_points WHERE ts_ms >= :sinceMs ORDER BY ts_ms ASC")
    suspend fun getRecent(sinceMs: Long): List<GpsTrackPoint>

    /** Hard-delete points older than the cutoff. Returns the deleted count. */
    @Query("DELETE FROM gps_track_points WHERE ts_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM gps_track_points")
    suspend fun count(): Int

    /** Manual nuke. Not currently wired to UI; for debugging / future "clear journey" action. */
    @Query("DELETE FROM gps_track_points")
    suspend fun deleteAll(): Int
}
