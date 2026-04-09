/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded GPS fix from the user's journey through Salem.
 *
 * Phase 9T+ "GPS Journey" feature (Session 109):
 * Recording is always-on while the activity is alive (Option B). The visibility
 * of the rendered polyline is controlled by a separate user preference and does
 * NOT gate recording.
 *
 * Retention: 24 hours rolling window, pruned by [GpsTrackRecorder].
 *
 * This entity lives in [UserDataDatabase] (a separate Room DB from the bundled
 * [SalemContentDatabase]) so that user-mutable journey data never collides with
 * the read-only content asset that ships in the APK.
 */
@Entity(
    tableName = "gps_track_points",
    indices = [Index(value = ["ts_ms"])]
)
data class GpsTrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall-clock millis at the moment the fix was recorded by [GpsTrackRecorder]. */
    @ColumnInfo(name = "ts_ms") val tsMs: Long,
    val lat: Double,
    val lng: Double,
    /** Reported horizontal accuracy in meters; 0f if the fix did not carry one. */
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float,
    /** Speed in meters/sec, or null if the fix did not carry one. */
    @ColumnInfo(name = "speed_mps") val speedMps: Float? = null,
    /** Bearing in degrees from true north, or null if the fix did not carry one. */
    @ColumnInfo(name = "bearing_deg") val bearingDeg: Float? = null
)
