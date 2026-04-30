package com.example.wickedsalemwitchcitytour.ui

import com.example.wickedsalemwitchcitytour.BuildConfig

/**
 * S203 — single switch that toggles the seven testing-window default flips
 * between recon-walk (debug) and retail (release/AAB) postures.
 *
 * Wired from [com.example.wickedsalemwitchcitytour.BuildConfig.RECON_DEFAULTS],
 * which is set per build type in `app-salem/build.gradle`:
 *   debug   → true   (recon walks: GPS recording + breadcrumb + heading-up + zoom 19 + 2x FAB)
 *   release → false  (Play Store: fresh-install posture, north-up zoom 18, no recording surprises)
 *
 * Three flips use this directly:
 *   - [GPS_BBOX_OVERRIDE]  — recon: real GPS outside Salem; retail: clamp to Samantha statue
 *   - [RECORD_GPS]         — recon: breadcrumb on; retail: off (privacy/battery)
 *   - [GPS_TRACK_VISIBLE]  — recon: polyline shown; retail: hidden
 *   - [HEADING_UP]         — recon: rotates with bearing; retail: north-up
 *
 * Three are numeric flips:
 *   - [DEFAULT_ZOOM]       — recon 19.0; retail 18.0
 *   - [FAB_ZOOM_STEP]      — recon 2.0; retail 1.0
 *   - [MAGNIFY_LEVEL]      — recon 1 (x2); retail 0 (x1)
 *
 * The values are `const`/`val` so R8 strips the dead branch in release builds.
 */
object BuildDefaults {
    const val GPS_BBOX_OVERRIDE: Boolean = BuildConfig.RECON_DEFAULTS
    const val RECORD_GPS:        Boolean = BuildConfig.RECON_DEFAULTS
    const val GPS_TRACK_VISIBLE: Boolean = BuildConfig.RECON_DEFAULTS
    const val HEADING_UP:        Boolean = BuildConfig.RECON_DEFAULTS

    val DEFAULT_ZOOM:   Double = if (BuildConfig.RECON_DEFAULTS) 19.0 else 18.0
    val FAB_ZOOM_STEP:  Double = if (BuildConfig.RECON_DEFAULTS) 2.0 else 1.0
    val MAGNIFY_LEVEL:  Int    = if (BuildConfig.RECON_DEFAULTS) 1   else 0
}
