/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour

import android.app.Application
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.BuildConfig
import com.example.wickedsalemwitchcitytour.util.OfflineTileManager
import com.example.wickedsalemwitchcitytour.util.SplashVoice
import com.example.wickedsalemwitchcitytour.util.TcpLogStreamer
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module WickedSalemApp.kt"

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

        // 0a. Debug-only TCP log streamer → collector PC running `nc -lk 4301`.
        //     Debug builds only — release/AAB must stay V1-offline per
        //     memory feedback_v1_no_external_contact.md.
        if (BuildConfig.DEBUG) {
            TcpLogStreamer.start()
        }

        // 1. Set osmdroid base path in prefs BEFORE load() picks it up.
        //    Ensures scoped storage compatibility on Android 10+.
        OfflineTileManager.configureStoragePath(applicationContext)

        // 2. Load osmdroid config (reads our base path from prefs).
        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = "WickedSalemWitchCityTour/1.0 (Android)"
            // S289 verbose: turn on osmdroid's internal debug logs (debug-only). This
            // exposes archive-provider hits/misses + tile cache evictions + downloader
            // failures (which should be zero in offline mode). Stripped in release.
            if (BuildConfig.DEBUG) {
                isDebugMode = true
                isDebugMapView = true
                isDebugTileProviders = true
            }
        }

        // 3. Extract bundled offline tiles to osmdroid base path on first launch.
        //    S305: started on a BACKGROUND thread (was a ~370 MB synchronous
        //    main-thread copy here — the cold-start ANR, review finding #1).
        //    SplashActivity gates its handoff to the map on
        //    OfflineTileManager.awaitReady() so tiles are present before render.
        OfflineTileManager.startExtraction(applicationContext)

        // 4. Warm up the splash TTS engine so SplashActivity can speak without
        //    eating a ~3-4s engine-bind delay on top of its animation budget.
        SplashVoice.initEarly(applicationContext)
    }
}
