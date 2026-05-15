/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.wickedsalemwitchcitytour.userdata.dao.GpsTrackPointDao
import com.example.wickedsalemwitchcitytour.userdata.dao.PassportVisitDao
import com.example.wickedsalemwitchcitytour.userdata.dao.PoiEncounterDao

/**
 * Room database for user-mutable data: things the user generates while running
 * the app, as opposed to the read-only content shipped in the APK
 * ([com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase]).
 *
 * Currently holds:
 *   - [GpsTrackPoint]: rolling 24h GPS journey log (Session 109)
 *   - [PoiEncounter]: POI proximity encounters with min/max/duration (Session 110)
 *   - [PassportVisit]: POI Passport "heard" log, POI-keyed lifetime (S268)
 *
 * Schema version history:
 *   v2 (S110) — added poi_encounters
 *   v3 (S268) — added passport_visit (via [UserDataMigrations.MIGRATION_2_3])
 *
 * S180 lockdown: schema is the v1.0.0 paid Play Store FLOOR. Every future
 * schema bump (v3+) MUST register a real Room Migration object via
 * UserDataModule.addMigrations(...). fallbackToDestructiveMigration was
 * removed in S180 — paying users would lose their poi_encounters history
 * (and now passport_visit history) on every app update.
 *
 * Provided by [com.example.wickedsalemwitchcitytour.userdata.di.UserDataModule].
 */
@Database(
    entities = [GpsTrackPoint::class, PoiEncounter::class, PassportVisit::class],
    version = 3,
    exportSchema = false
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun gpsTrackPointDao(): GpsTrackPointDao
    abstract fun poiEncounterDao(): PoiEncounterDao
    abstract fun passportVisitDao(): PassportVisitDao
}
