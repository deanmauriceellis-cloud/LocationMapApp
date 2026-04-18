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
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.util.OfflineTileManager
import com.example.wickedsalemwitchcitytour.util.SplashVoice
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WickedSalemApp.kt"

@HiltAndroidApp
class WickedSalemApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 0. Wire up persistent DebugLogger file sink BEFORE anything else logs.
        //    Captures everything to /sdcard/Android/data/<pkg>/files/logs/debug-YYYYMMDD.log
        //    so logs survive process death — the gap that broke today's
        //    drive bug investigation. Pull with `adb pull` after a session.
        DebugLogger.initFileSink(applicationContext)
        DebugLogger.i("WickedSalemApp", "onCreate — file sink wired up")

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

        // 4. Warm up the splash TTS engine so SplashActivity can speak without
        //    eating a ~3-4s engine-bind delay on top of its animation budget.
        SplashVoice.initEarly(applicationContext)
    }
}
