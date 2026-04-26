package com.example.wickedsalemwitchcitytour.routing.di

import android.content.Context
import com.example.wickedsalemwitchcitytour.routing.SalemRouterProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    @Provides
    @Singleton
    fun provideSalemRouterProvider(@ApplicationContext context: Context): SalemRouterProvider =
        SalemRouterProvider(context)
}
