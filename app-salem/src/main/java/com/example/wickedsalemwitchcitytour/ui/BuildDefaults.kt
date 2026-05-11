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
 *   - [GPS_TRACK_VISIBLE]  — recon: polyline shown; retail: hidden
 *   - [HEADING_UP]         — recon: rotates with bearing; retail: north-up
 * (S242: RECORD_GPS dropped — breadcrumb recording stub never shipped.)
 *
 * Three are numeric flips:
 *   - [DEFAULT_ZOOM]       — recon 19.0; retail 18.0
 *   - [FAB_ZOOM_STEP]      — 1.0 in both (S231: was recon 2.0, skipped z18)
 *   - [MAGNIFY_LEVEL]      — recon 1 (x2); retail 0 (x1)
 *
 * The values are `const`/`val` so R8 strips the dead branch in release builds.
 */
object BuildDefaults {
    const val GPS_BBOX_OVERRIDE: Boolean = BuildConfig.RECON_DEFAULTS
    const val GPS_TRACK_VISIBLE: Boolean = BuildConfig.RECON_DEFAULTS
    const val HEADING_UP:        Boolean = BuildConfig.RECON_DEFAULTS

    // S228 — second toolbar camera that auto-fires every 3s while the toggle is
    // ON, for post-walk POI/path-alignment QC. Headless CameraX, no preview.
    // Const so R8 strips the toolbar wiring + controller refs in retail.
    const val GPS_BURST_ENABLED: Boolean = BuildConfig.RECON_DEFAULTS

    // S229 — third toolbar button: field-edit mode. Long-press a POI to open
    // a bottom sheet that captures move/category/subcategory/note/photo into
    // an append-only JSONL at /sdcard/Documents/WickedSalemFieldEdits/. Pulled
    // home post-walk and triaged in the web admin's Field Edits inbox.
    const val FIELD_EDIT_ENABLED: Boolean = BuildConfig.RECON_DEFAULTS

    // S232 / S233 — matrix-tilt 3D. TiltContainer wraps the MapView and skews
    // its canvas via setPolyToPoly + grows the MapView upward in tilt mode so
    // the wedges fill with real distant tiles. Touch events are manually
    // perspective-transformed via Matrix.mapPoints. S233 promoted to V1
    // approved (operator sign-off) — flipped from BuildConfig.DEBUG to true
    // so the dedicated 3D FAB ships in release/AAB builds.
    const val TILT_3D_ENABLED: Boolean = true

    val DEFAULT_ZOOM:   Double = if (BuildConfig.RECON_DEFAULTS) 19.0 else 18.0
    val FAB_ZOOM_STEP:  Double = 1.0
    val MAGNIFY_LEVEL:  Int    = if (BuildConfig.RECON_DEFAULTS) 1   else 0
}
