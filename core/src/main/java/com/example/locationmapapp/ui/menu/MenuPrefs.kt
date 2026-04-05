/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui.menu

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
}
