/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
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
import com.example.wickedsalemwitchcitytour.userdata.db.UserDataDatabase
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideGpsTrackPointDao(db: UserDataDatabase): GpsTrackPointDao =
        db.gpsTrackPointDao()

    @Provides
    fun providePoiEncounterDao(db: UserDataDatabase): PoiEncounterDao =
        db.poiEncounterDao()
}
