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
import com.example.wickedsalemwitchcitytour.userdata.dao.PoiEncounterDao

/**
 * Room database for user-mutable data: things the user generates while running
 * the app, as opposed to the read-only content shipped in the APK
 * ([com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase]).
 *
 * Currently holds:
 *   - [GpsTrackPoint]: rolling 24h GPS journey log (Session 109)
 *   - [PoiEncounter]: POI proximity encounters with min/max/duration (Session 110)
 *
 * Schema version: bumped 1 → 2 in S110 to add the `poi_encounters` table.
 * Migration uses `fallbackToDestructiveMigration` because the only existing
 * table holds 24h-rolling GPS points; losing them on schema upgrade is
 * acceptable (next fix re-seeds the journey log).
 *
 * Provided by [com.example.wickedsalemwitchcitytour.userdata.di.UserDataModule].
 */
@Database(
    entities = [GpsTrackPoint::class, PoiEncounter::class],
    version = 2,
    exportSchema = false
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun gpsTrackPointDao(): GpsTrackPointDao
    abstract fun poiEncounterDao(): PoiEncounterDao
}
