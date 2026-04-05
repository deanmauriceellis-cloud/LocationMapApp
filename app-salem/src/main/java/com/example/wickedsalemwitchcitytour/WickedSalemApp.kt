/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour

import android.app.Application
import com.example.wickedsalemwitchcitytour.util.OfflineTileManager
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WickedSalemApp.kt"

@HiltAndroidApp
class WickedSalemApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Set osmdroid base path in prefs BEFORE load() picks it up.
        //    Ensures scoped storage compatibility on Android 10+.
        OfflineTileManager.configureStoragePath(applicationContext)

        // 2. Load osmdroid config (reads our base path from prefs).
        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = "WickedSalemWitchCityTour/1.0 (Android)"
        }

        // 3. Extract bundled offline tiles to osmdroid base path on first launch.
        OfflineTileManager.extractArchiveIfNeeded(applicationContext)
    }
}
