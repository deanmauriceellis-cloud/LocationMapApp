/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.wickedsalemwitchcitytour.userdata.dao.GpsTrackPointDao

/**
 * Room database for user-mutable data: things the user generates while running
 * the app, as opposed to the read-only content shipped in the APK
 * ([com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase]).
 *
 * Currently holds:
 *   - [GpsTrackPoint]: rolling 24h GPS journey log (Session 109)
 *
 * Provided by [com.example.wickedsalemwitchcitytour.userdata.di.UserDataModule].
 */
@Database(
    entities = [GpsTrackPoint::class],
    version = 1,
    exportSchema = false
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun gpsTrackPointDao(): GpsTrackPointDao
}
