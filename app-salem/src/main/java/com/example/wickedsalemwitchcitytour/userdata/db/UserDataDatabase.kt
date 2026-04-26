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
 *
 * S180 lockdown: schema is now the v1.0.0 paid Play Store FLOOR. Every
 * future schema bump (v3+) MUST register a real Room Migration object via
 * UserDataModule.addMigrations(...). fallbackToDestructiveMigration was
 * removed in S180 — paying users would lose their poi_encounters history
 * on every app update, which is unacceptable for a paid product.
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
