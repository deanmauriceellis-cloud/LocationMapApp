/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.di

import android.content.Context
import com.example.locationmapapp.data.location.LocationManager
import com.example.locationmapapp.data.repository.MbtaRepository
import com.example.locationmapapp.data.repository.PlacesRepository
import com.example.locationmapapp.data.repository.FindRepository
import com.example.locationmapapp.data.repository.AuthRepository
import com.example.locationmapapp.data.repository.ChatRepository
import com.example.locationmapapp.data.repository.CommentRepository
import com.example.locationmapapp.data.repository.GeofenceDatabaseRepository
import com.example.locationmapapp.data.repository.GeofenceRepository
import com.example.locationmapapp.data.repository.TfrRepository
import com.example.locationmapapp.data.repository.WebcamRepository
import com.example.locationmapapp.data.repository.WeatherRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module AppModule.kt"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager =
        LocationManager(context)

    @Provides @Singleton
    fun providePlacesRepository(@ApplicationContext context: Context): PlacesRepository = PlacesRepository(context)

    @Provides @Singleton
    fun provideWeatherRepository(): WeatherRepository = WeatherRepository()

    @Provides @Singleton
    fun provideMbtaRepository(): MbtaRepository = MbtaRepository()

    @Provides @Singleton
    fun provideWebcamRepository(): WebcamRepository = WebcamRepository()

    @Provides @Singleton
    fun provideFindRepository(): FindRepository = FindRepository()

    @Provides @Singleton
    fun provideTfrRepository(): TfrRepository = TfrRepository()

    @Provides @Singleton
    fun provideGeofenceRepository(): GeofenceRepository = GeofenceRepository()

    @Provides @Singleton
    fun provideGeofenceDatabaseRepository(@ApplicationContext context: Context) = GeofenceDatabaseRepository(context)

    @Provides @Singleton
    fun provideAuthRepository(@ApplicationContext context: Context) = AuthRepository(context)

    @Provides @Singleton
    fun provideCommentRepository(authRepository: AuthRepository) = CommentRepository(authRepository)

    @Provides @Singleton
    fun provideChatRepository(authRepository: AuthRepository) = ChatRepository(authRepository)
}
