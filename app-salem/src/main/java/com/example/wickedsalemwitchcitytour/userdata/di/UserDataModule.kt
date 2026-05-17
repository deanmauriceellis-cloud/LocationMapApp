/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.di

import android.content.Context
import androidx.room.Room
import com.example.wickedsalemwitchcitytour.userdata.dao.GpsTrackPointDao
import com.example.wickedsalemwitchcitytour.userdata.dao.PoiEncounterDao
import com.example.wickedsalemwitchcitytour.userdata.dao.PoiVisitDao
import com.example.wickedsalemwitchcitytour.userdata.db.UserDataDatabase
import com.example.wickedsalemwitchcitytour.userdata.db.UserDataMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserDataModule {

    @Provides
    @Singleton
    fun provideUserDataDatabase(@ApplicationContext context: Context): UserDataDatabase =
        Room.databaseBuilder(
            context,
            UserDataDatabase::class.java,
            "user_data.db"
        )
            // S180: v1.0.0 paid Play Store release floor. fallbackToDestructiveMigration
            // REMOVED — every future schema bump must register a real Room Migration
            // object via .addMigrations(...). Without this discipline, paying users
            // lose their POI encounter history on every app update.
            // Schema history:
            //   v2 (S110) — added poi_encounters
            //   v3 (S268) — added passport_visit
            //   v4 (S274) — renamed passport_visit → poi_visit (Katrina's Collection rebrand)
            .addMigrations(
                UserDataMigrations.MIGRATION_2_3,
                UserDataMigrations.MIGRATION_3_4,
            )
            .build()

    @Provides
    fun provideGpsTrackPointDao(db: UserDataDatabase): GpsTrackPointDao =
        db.gpsTrackPointDao()

    @Provides
    fun providePoiEncounterDao(db: UserDataDatabase): PoiEncounterDao =
        db.poiEncounterDao()

    @Provides
    fun providePoiVisitDao(db: UserDataDatabase): PoiVisitDao =
        db.poiVisitDao()
}
