/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui.menu

import android.content.SharedPreferences

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MenuPrefs.kt"

/**
 * Preference key constants for all menu-controlled settings.
 * Extracted from AppBarMenuManager companion object for clean imports.
 */
object MenuPrefs {

    const val PREFS_NAME = "app_bar_menu_prefs"

    // ── Radar ─────────────────────────────────────────────────────────────
    const val PREF_RADAR_ON          = "radar_on"
    const val PREF_RADAR_VISIBILITY  = "radar_visibility"
    const val PREF_RADAR_FREQ        = "radar_freq_min"
    const val DEFAULT_RADAR_FREQ_MIN = 5

    // ── Weather / METAR / Aircraft ──────────────────────────────────────
    const val PREF_METAR_DISPLAY      = "metar_display_on"
    const val PREF_METAR_FREQ         = "metar_freq_min"
    const val PREF_AIRCRAFT_DISPLAY   = "aircraft_display_on"
    const val PREF_AIRCRAFT_FREQ      = "aircraft_freq_sec"

    // ── Transit ───────────────────────────────────────────────────────────
    const val PREF_MBTA_STATIONS    = "mbta_stations_on"
    const val PREF_MBTA_TRAINS      = "mbta_trains_on"
    const val PREF_MBTA_TRAINS_FREQ = "mbta_trains_freq_sec"
    const val PREF_MBTA_SUBWAY      = "mbta_subway_on"
    const val PREF_MBTA_SUBWAY_FREQ = "mbta_subway_freq_sec"
    const val PREF_MBTA_BUSES       = "mbta_buses_on"
    const val PREF_MBTA_BUSES_FREQ  = "mbta_buses_freq_sec"
    const val PREF_MBTA_BUS_STOPS   = "mbta_bus_stops_on"
    const val PREF_NAT_ALERTS       = "national_alerts_on"
    const val PREF_NAT_ALERTS_FREQ  = "national_alerts_freq_min"

    // ── Cameras ───────────────────────────────────────────────────────────
    const val PREF_WEBCAMS_ON          = "webcams_on"
    const val PREF_WEBCAM_CATEGORIES   = "webcam_categories"

    // ── Alerts / Geofence ────────────────────────────────────────────────
    const val PREF_TFR_OVERLAY      = "tfr_overlay_on"
    const val PREF_CAMERA_OVERLAY   = "camera_overlay_on"
    const val PREF_SCHOOL_OVERLAY   = "school_overlay_on"
    const val PREF_FLOOD_OVERLAY    = "flood_overlay_on"
    const val PREF_CROSSING_OVERLAY = "crossing_overlay_on"
    const val PREF_ALERT_SOUND      = "alert_sound_on"
    const val PREF_ALERT_DISTANCE   = "alert_distance_nm"

    // ── Dark Mode / Tile Source ─────────────────────────────────────────
    const val PREF_DARK_MODE = "dark_mode_enabled"
    const val PREF_TILE_SOURCE = "tile_source_id"

    // ── POI Location Layer (S162) ───────────────────────────────────────
    // Toggles which set of POI coordinates the map renders:
    //   "current"  — live lat/lng (default)
    //   "proposed" — lat_proposed/lng_proposed from the verifier
    //   "both"     — both sets, drawn together for visual diff
    // Set via the layers icon on the toolbar (alongside the basemap picker).
    const val PREF_POI_LOCATION_LAYER = "poi_location_layer"

    // ── Radar Animation ──────────────────────────────────────────────────
    const val PREF_RADAR_ANIMATE    = "radar_animate_on"
    const val PREF_RADAR_ANIM_SPEED = "radar_anim_speed_ms"
    const val DEFAULT_RADAR_ANIM_SPEED = 800

    // ── Home Location ──────────────────────────────────────────────────────
    const val PREF_HOME_LAT  = "home_lat"
    const val PREF_HOME_LON  = "home_lon"
    const val PREF_HOME_SET  = "home_set"

    // ── Utility ───────────────────────────────────────────────────────────
    const val PREF_RECORD_GPS            = "record_gps_on"
    const val PREF_GPS_MODE              = "gps_mode_auto"
    const val PREF_AUTO_FOLLOW_AIRCRAFT  = "auto_follow_aircraft_on"
    const val PREF_POPULATE_POIS         = "populate_pois_on"
    const val PREF_SILENT_FILL_DEBUG     = "silent_fill_debug_on"
    /** S149: when true, disable the out-of-Salem-bbox clamp so the app uses real GPS
     *  positions regardless of whether the fix lands inside the Salem bounding box.
     *  S168: install default flipped back to `false` — a user opening the app from
     *  outside Salem should land at the Samantha statue (the game's home base) so
     *  narration/POIs work on first launch. Operators can still opt in via
     *  Journey → "Use Real GPS Outside Salem". */
    const val PREF_GPS_BBOX_OVERRIDE     = "gps_bbox_override_on"
    /** Default value for [PREF_GPS_BBOX_OVERRIDE]. Shared between the menu sync and
     *  the startup pref read so they can't drift. Default `false` — first launch
     *  outside Salem clamps to the Samantha statue (the game's home base) so
     *  narration/POIs work immediately. Toggle in Journey menu opts a tester in. */
    const val PREF_GPS_BBOX_OVERRIDE_DEFAULT = false

    // ── POI source filters (S186 — Layers checkboxes, mode-dependent) ──────────
    // Each Layers checkbox (POIs Hist. Landmark, POIs Civic) has TWO prefs:
    // one for Explore Salem (no tour) and one for Tour Mode. Defaults differ:
    //   Explore: both ON  (default install state — wander Salem hearing
    //                      everything visible, including civic + hist landmark)
    //   Tour:    both OFF (more restrictive — only is_tour_poi=true narrates
    //                      until user opts back in mid-tour via Layers)
    // Each checkbox reads/writes the pref matching the current tour state, so
    // the user's Explore choice is independent of their Tour choice.
    // Both prefs gate BOTH map visibility AND narration eligibility unified.

    /** Explore-mode "POIs Hist. Landmark" toggle. Default ON.
     *  Gates HIST_BLDG/ENT/LODGING with year_established ≤ 1860 + data_source=massgis_mhc. */
    const val PREF_POI_HIST_LANDMARK = "poi_hist_landmark_on"
    const val PREF_POI_HIST_LANDMARK_DEFAULT = true

    /** Tour-mode "POIs Hist. Landmark" toggle. Default OFF.
     *  When ON during a tour, unsilences + shows the same Historical Landmark set. */
    const val PREF_POI_HIST_LANDMARK_TOUR = "poi_hist_landmark_tour_on"
    const val PREF_POI_HIST_LANDMARK_TOUR_DEFAULT = false

    /** Explore-mode "POIs Civic" toggle. Default ON.
     *  Gates POIs flagged is_civic_poi=true (police/fire/town hall/etc.). */
    const val PREF_POI_CIVIC = "poi_civic_on"
    const val PREF_POI_CIVIC_DEFAULT = true

    /** Tour-mode "POIs Civic" toggle. Default OFF.
     *  When ON during a tour, unsilences + shows is_civic_poi=true POIs. */
    const val PREF_POI_CIVIC_TOUR = "poi_civic_tour_on"
    const val PREF_POI_CIVIC_TOUR_DEFAULT = false

    // ── Witch Trials feature (Phase 9X, S127) ─────────────────────────────
    /** When true, detail screens (history tile, newspaper, NPC bio) auto-speak the body 1s after open. */
    const val PREF_NARRATOR_MODE_ENABLED = "narrator_mode_enabled"

    // ── S186 helpers: pick the correct Layers pref for the active mode ────────
    fun histLandmarkPrefKey(tourActive: Boolean): String =
        if (tourActive) PREF_POI_HIST_LANDMARK_TOUR else PREF_POI_HIST_LANDMARK
    fun civicPrefKey(tourActive: Boolean): String =
        if (tourActive) PREF_POI_CIVIC_TOUR else PREF_POI_CIVIC

    // S206 — when entering tour mode for the first time and the tour-mode pref
    // has never been written, mirror the explore-mode pref instead of falling
    // through to the hardcoded TOUR_DEFAULT=false. Pre-S206 trap: operator
    // saw the Layers boxes checked in Explore (default ON), started a tour,
    // and tour-mode prefs silently defaulted OFF — markers + narration
    // disappeared until the operator re-checked the boxes mid-tour. With this
    // mirror, "I had them checked" intuition holds. Once the operator
    // explicitly toggles a tour-mode box, that written value wins and the
    // mirror is moot.
    fun histLandmarkPrefDefault(tourActive: Boolean, prefs: SharedPreferences? = null): Boolean = when {
        !tourActive -> PREF_POI_HIST_LANDMARK_DEFAULT
        prefs != null -> prefs.getBoolean(PREF_POI_HIST_LANDMARK, PREF_POI_HIST_LANDMARK_DEFAULT)
        else -> PREF_POI_HIST_LANDMARK_TOUR_DEFAULT
    }
    fun civicPrefDefault(tourActive: Boolean, prefs: SharedPreferences? = null): Boolean = when {
        !tourActive -> PREF_POI_CIVIC_DEFAULT
        prefs != null -> prefs.getBoolean(PREF_POI_CIVIC, PREF_POI_CIVIC_DEFAULT)
        else -> PREF_POI_CIVIC_TOUR_DEFAULT
    }
}
