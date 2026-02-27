package com.example.locationmapapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager

@HiltAndroidApp
class LocationMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = "LocationMapApp/1.1 (Android)"
        }
    }
}
