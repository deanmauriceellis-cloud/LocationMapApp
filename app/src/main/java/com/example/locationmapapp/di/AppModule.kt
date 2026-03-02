package com.example.locationmapapp.di

import android.content.Context
import com.example.locationmapapp.data.location.LocationManager
import com.example.locationmapapp.data.repository.MbtaRepository
import com.example.locationmapapp.data.repository.PlacesRepository
import com.example.locationmapapp.data.repository.FindRepository
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
}
