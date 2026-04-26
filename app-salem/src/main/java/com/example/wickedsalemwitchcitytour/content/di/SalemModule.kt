/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.di

import android.content.Context
import androidx.room.Room
import com.example.wickedsalemwitchcitytour.content.dao.*
import com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SalemModule {

    @Provides @Singleton
    fun provideSalemContentDatabase(@ApplicationContext context: Context): SalemContentDatabase =
        Room.databaseBuilder(
            context,
            SalemContentDatabase::class.java,
            "salem_content.db"
        )
            .createFromAsset("salem_content.db")
            // S180 — fallbackToDestructiveMigration is INTENTIONAL for this DB.
            // salem_content.db is read-only bundled content (createFromAsset).
            // When the schema bumps in a new APK, the new asset replaces the
            // user's local copy — destructive is the correct semantic. The
            // user has no editable state in this DB to preserve.
            // Contrast: UserDataDatabase has user-generated state and must use
            // real migrations (S180 lockdown — fallback removed there).
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTourPoiDao(db: SalemContentDatabase): TourPoiDao = db.tourPoiDao()
    @Provides fun provideHistoricalFigureDao(db: SalemContentDatabase): HistoricalFigureDao = db.historicalFigureDao()
    @Provides fun provideHistoricalFactDao(db: SalemContentDatabase): HistoricalFactDao = db.historicalFactDao()
    @Provides fun provideTimelineEventDao(db: SalemContentDatabase): TimelineEventDao = db.timelineEventDao()
    @Provides fun providePrimarySourceDao(db: SalemContentDatabase): PrimarySourceDao = db.primarySourceDao()
    @Provides fun provideTourDao(db: SalemContentDatabase): TourDao = db.tourDao()
    @Provides fun provideTourStopDao(db: SalemContentDatabase): TourStopDao = db.tourStopDao()
    @Provides fun provideEventsCalendarDao(db: SalemContentDatabase): EventsCalendarDao = db.eventsCalendarDao()
    @Provides fun provideSalemPoiDao(db: SalemContentDatabase): SalemPoiDao = db.salemPoiDao()

    @Provides fun provideWitchTrialsArticleDao(db: SalemContentDatabase): WitchTrialsArticleDao = db.witchTrialsArticleDao()
    @Provides fun provideWitchTrialsNpcBioDao(db: SalemContentDatabase): WitchTrialsNpcBioDao = db.witchTrialsNpcBioDao()
    @Provides fun provideWitchTrialsNewspaperDao(db: SalemContentDatabase): WitchTrialsNewspaperDao = db.witchTrialsNewspaperDao()
}
