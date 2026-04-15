/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.wickedsalemwitchcitytour.content.dao.*
import com.example.wickedsalemwitchcitytour.content.model.*

@Database(
    entities = [
        HistoricalFigure::class,
        HistoricalFact::class,
        TimelineEvent::class,
        PrimarySource::class,
        Tour::class,
        TourStop::class,
        EventsCalendar::class,
        SalemPoi::class,
        WitchTrialsArticle::class,
        WitchTrialsNpcBio::class,
        WitchTrialsNewspaper::class
    ],
    version = 8,
    exportSchema = false
)
abstract class SalemContentDatabase : RoomDatabase() {
    abstract fun tourPoiDao(): TourPoiDao
    abstract fun historicalFigureDao(): HistoricalFigureDao
    abstract fun historicalFactDao(): HistoricalFactDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun primarySourceDao(): PrimarySourceDao
    abstract fun tourDao(): TourDao
    abstract fun tourStopDao(): TourStopDao
    abstract fun eventsCalendarDao(): EventsCalendarDao
    abstract fun salemPoiDao(): SalemPoiDao
    abstract fun witchTrialsArticleDao(): WitchTrialsArticleDao
    abstract fun witchTrialsNpcBioDao(): WitchTrialsNpcBioDao
    abstract fun witchTrialsNewspaperDao(): WitchTrialsNewspaperDao
}
