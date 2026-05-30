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

    // S249 — fourth toolbar button: SuperAdmin mode. Toggles the runtime
    // override (`SuperAdminMode.allowNetwork`) that bypasses V1_OFFLINE_ONLY
    // for the parked external services (weather / MBTA / aircraft / METAR /
    // webcams / radar). Debug-only — release/AAB hardcodes false so R8
    // strips the toolbar wiring, the override path, and never re-adds the
    // INTERNET permission (which lives in the debug-only manifest at
    // app-salem/src/debug/AndroidManifest.xml).
    const val SUPER_ADMIN_ENABLED: Boolean = BuildConfig.RECON_DEFAULTS

    // S232 / S233 — matrix-tilt 3D. TiltContainer wraps the MapView and skews
    // its canvas via setPolyToPoly + grows the MapView upward in tilt mode so
    // the wedges fill with real distant tiles. Touch events are manually
    // perspective-transformed via Matrix.mapPoints. S233 promoted to V1
    // approved (operator sign-off) — flipped from BuildConfig.DEBUG to true
    // so the dedicated 3D FAB ships in release/AAB builds.
    const val TILT_3D_ENABLED: Boolean = true

    // S308 — adaptive tilt rendering for weak/virtualized GPUs (BlueStacks tile
    // corruption: basemap tiles drawn THROUGH the setPolyToPoly perspective matrix
    // on a HARDWARE canvas flicker magenta/cyan on virtualized/software GL drivers).
    // Ships TRUE in RELEASE (NOT RECON-gated) — the safe behavior must reach paying
    // users on weak/unknown GPUs. GOOD mobile GPUs (Adreno/Mali/…) skip it and keep
    // the hardware fast path; non-GOOD route the tilted composite through a software
    // (Skia/CPU) layer (correctness > speed). See GpuCapability.kt +
    // docs/plans/adaptive-tilt-gpu-fix-2026-05-29.md.
    const val TILT_SOFTWARE_LAYER_ON_WEAK_GPU: Boolean = true
    const val TILT_SOFTWARE_MAX_FPS: Int = 12

    // OMEN-025 Phase 1 (S296) — activation gate dev affordances, same proven
    // RECON_DEFAULTS pattern as SUPER_ADMIN_ENABLED (const-folds + R8-strips in
    // release, identical safety story; see feedback_super_admin_runtime_override_pattern).
    //
    // ACTIVATION_BYPASS: debug devices are sideloaded with no real Play verdict,
    //   so debug bypasses the handshake and goes straight to ACTIVATED. Release/
    //   AAB hardcodes false → the gate is live and a tampered build actually
    //   LOCKS (the const folds out, so do NOT assume R8 alone removed it —
    //   verify via apkanalyzer at S7 per the plan's release-strip proof).
    // ACTIVATION_TRIPWIRES_LOG_ONLY: debug only logs soft tripwires (installer≠
    //   Play, signing-cert drift) instead of locking — adb installs trip them
    //   harmlessly. Release enforces them.
    const val ACTIVATION_BYPASS:             Boolean = BuildConfig.RECON_DEFAULTS
    const val ACTIVATION_TRIPWIRES_LOG_ONLY: Boolean = BuildConfig.RECON_DEFAULTS

    val DEFAULT_ZOOM:   Double = if (BuildConfig.RECON_DEFAULTS) 19.0 else 18.0
    val FAB_ZOOM_STEP:  Double = 1.0
    val MAGNIFY_LEVEL:  Int    = if (BuildConfig.RECON_DEFAULTS) 1   else 0
}
