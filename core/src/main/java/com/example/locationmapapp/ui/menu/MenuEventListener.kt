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
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MenuEventListener.kt"

/**
 * MenuEventListener  — v1.5
 *
 * Clean boundary between AppBarMenuManager (pure UI/pref layer) and the rest of the app.
 * MainActivity implements this interface and delegates to MainViewModel or fires Intents.
 *
 * Design rules:
 *   - Every binary toggle has its own named callback so the compiler enforces coverage.
 *   - Every slider has its own named callback carrying the chosen value.
 *   - Every action button has its own named callback.
 *   - onStubAction() is kept ONLY as a safety net for truly unknown IDs — not for known features.
 *
 * Stub implementations in MainActivity should log + show a Toast until the feature is built.
 */
interface MenuEventListener {

    // =========================================================================
    // WEATHER
    // =========================================================================

    /** Open the rich weather dialog (current, hourly, daily, alerts). */
    fun onWeatherRequested()

    /** METAR station markers with human-readable pop-up. */
    fun onMetarDisplayToggled(enabled: Boolean)

    /** How often to refresh METAR data from aviationweather.gov. [minutes] in 1..10 */
    fun onMetarFrequencyChanged(minutes: Int)

    /** Live aircraft positions from OpenSky Network. */
    fun onAircraftDisplayToggled(enabled: Boolean)

    /** How often to refresh aircraft positions. [seconds] in 5..60 */
    fun onAircraftFrequencyChanged(seconds: Int)

    // =========================================================================
    // PUBLIC TRANSIT
    // =========================================================================

    /** MBTA train station markers on map (subway + commuter rail). */
    fun onMbtaStationsToggled(enabled: Boolean)

    /** MBTA bus stop markers on map (route_type=3). */
    fun onMbtaBusStopsToggled(enabled: Boolean)

    /** Live MBTA Commuter Rail vehicle positions. */
    fun onMbtaTrainsToggled(enabled: Boolean)

    /** Refresh interval for commuter rail positions. [seconds] in 30..300 */
    fun onMbtaTrainsFrequencyChanged(seconds: Int)

    /** Live MBTA Subway (Light Rail + Heavy Rail) vehicle positions. */
    fun onMbtaSubwayToggled(enabled: Boolean)

    /** Refresh interval for subway positions. [seconds] in 30..300 */
    fun onMbtaSubwayFrequencyChanged(seconds: Int)

    /** Live MBTA Bus positions. */
    fun onMbtaBusesToggled(enabled: Boolean)

    /** Refresh interval for bus positions. [seconds] in 30..300 */
    fun onMbtaBusesFrequencyChanged(seconds: Int)

    /**
     * National/regional emergency alert overlay.
     * Source: https://alerts.weather.gov/cap/ma.php?x=1 (CAP/XML feed)
     */
    fun onNationalAlertsToggled(enabled: Boolean)

    /** Refresh interval for CAP alert feed. [minutes] in 1..15 */
    fun onNationalAlertsFrequencyChanged(minutes: Int)

    // =========================================================================
    // CAMERAS
    // =========================================================================

    /** Windy webcam markers on map. */
    fun onWebcamToggled(enabled: Boolean)

    /** User changed selected webcam categories — reload markers. */
    fun onWebcamCategoriesChanged(categories: Set<String>)

    // =========================================================================
    // RADAR
    // =========================================================================

    /** Weather radar tile overlay toggled on/off. */
    fun onRadarToggled(enabled: Boolean)

    /** Radar tile alpha/opacity. [percent] in 0..100 */
    fun onRadarVisibilityChanged(percent: Int)

    /** How often the radar tile URL is invalidated and re-fetched. [minutes] in 5..15 */
    fun onRadarFrequencyChanged(minutes: Int)

    /** Toggle animated radar (multi-frame NEXRAD loop). */
    fun onRadarAnimateToggled(enabled: Boolean)

    /** Speed of radar animation loop. [ms] frame interval. */
    fun onRadarAnimSpeedChanged(ms: Int)

    // =========================================================================
    // DARK MODE
    // =========================================================================

    /** Toggle dark map tiles (CartoDB Dark Matter). */
    fun onDarkModeToggled(dark: Boolean)

    /** Switch tile source (SATELLITE, STREET, DARK). Default no-op for backward compat. */
    fun onTileSourceChanged(tileSourceId: String) {}

    /**
     * S162: Switch which POI coordinate set the map renders.
     * [layerId] is one of "current" | "proposed" | "both".
     * Default no-op — implementers opt in when verifier-proposed coords
     * are available (TigerLine/MassGIS workflow).
     */
    fun onPoiLocationLayerChanged(layerId: String) {}

    // =========================================================================
    // POINTS OF INTEREST
    // =========================================================================

    /**
     * Generic POI layer toggle.
     * [layerId] — use constants from [PoiLayerId].
     */
    fun onPoiLayerToggled(layerId: String, enabled: Boolean)

    // =========================================================================
    // UTILITY
    // =========================================================================

    /** Start / stop recording GPS breadcrumb track to local storage. */
    fun onGpsRecordingToggled(enabled: Boolean)

    /** Launch external story-builder website with stored GPS coordinate payload. */
    fun onBuildStoryRequested()

    /** Launch external analytics site — daily travel summary for current day. */
    fun onAnalyzeTodayRequested()

    /** Launch external analytics site — travel anomaly detection on stored track. */
    fun onTravelAnomaliesRequested()

    /** Share / email recorded track as a standard GPX file attachment. */
    fun onEmailGpxRequested()

    /** Open in-app DebugLogActivity. */
    fun onDebugLogRequested()

    /** Auto-follow random high-altitude aircraft every 20 min for POI cache building. */
    fun onAutoFollowAircraftToggled(enabled: Boolean)

    /** Systematic grid scanner: spiral outward from map center, searching every cell for POIs. */
    fun onPopulatePoisToggled(enabled: Boolean)

    /** Single 10km probe at map center — wide POI discovery + radius hint creation. */
    fun onProbe10kmRequested()

    /** Fill gaps using existing radius hints (stub — future implementation). */
    fun onFillProbeRequested()

    /** Switch between automatic GPS centering and manual tap-to-set-location mode. */
    fun onGpsModeToggled(autoGps: Boolean)

    /** S149: toggle the Samantha-bbox clamp — when true, real GPS is used regardless
     *  of whether the user is inside or outside the Salem bounding box. Default impl
     *  is a no-op so non-Salem consumers of this interface don't have to implement it. */
    fun onGpsBboxOverrideToggled(useRealGps: Boolean) {}

    /** Toggle debug banner visibility for silent background POI fill. */
    fun onSilentFillDebugToggled(enabled: Boolean)

    /** Phase 9X: Witch Trials feature — when on, detail screens auto-speak the body. */
    fun onNarratorModeToggled(enabled: Boolean) {}

    // =========================================================================
    // ALERTS / GEOFENCE
    // =========================================================================

    /** Open the alerts popup menu. */
    fun onAlertsRequested()

    /** TFR overlay display on map. */
    fun onTfrOverlayToggled(enabled: Boolean)

    /** Speed/red-light camera zone overlay. */
    fun onCameraOverlayToggled(enabled: Boolean)

    /** School zone overlay (weekday hours only). */
    fun onSchoolOverlayToggled(enabled: Boolean)

    /** FEMA flood zone overlay. */
    fun onFloodOverlayToggled(enabled: Boolean)

    /** Railroad crossing zone overlay. */
    fun onCrossingOverlayToggled(enabled: Boolean)

    /** Audible alert sounds for geofence events. */
    fun onAlertSoundToggled(enabled: Boolean)

    /** Proximity warning distance in nautical miles. */
    fun onAlertDistanceChanged(nm: Int)

    /** Open the geofence database manager dialog. */
    fun onDatabaseManagerRequested()

    // =========================================================================
    // FIND / LEGEND
    // =========================================================================

    /** Open the Find POI discovery dialog. */
    fun onFindRequested()

    /** Open the map legend dialog showing all marker types and their meanings. */
    fun onLegendRequested()

    /** Open the Go to Location geocoder dialog. */
    fun onGoToLocationRequested()

    // =========================================================================
    // SOCIAL
    // =========================================================================

    /** Open the Social hub (auth dialog if not logged in, profile if logged in). */
    fun onSocialRequested()

    /** Open the chat room list. */
    fun onChatRequested()

    /** Open the user profile dialog. */
    fun onProfileRequested()

    // =========================================================================
    // TOURS
    // =========================================================================

    /** Open the tour selection / active tour UI. */
    fun onTourRequested()

    /** Open the events calendar (Haunted Happenings, exhibits, seasonal). */
    fun onEventsRequested()

    /** Open the Salem Witch Trials 3-panel sub-menu (History / Newspapers / People). */
    fun onWitchTrialsRequested() {}

    // =========================================================================
    // TOOLBAR ACTIONS
    // =========================================================================

    /** Center map on current GPS location or saved home. */
    fun onHomeRequested()

    /** Long-press on home icon: set (true) or clear (false) home location. */
    fun onHomeLongPressed(setting: Boolean)

    /** Show the About dialog with version and copyright info. */
    fun onAboutRequested()

    // =========================================================================
    // SAFETY NET
    // =========================================================================

    /**
     * Called ONLY for unrecognised item IDs that don't match any named callback above.
     * Should never fire in production — treat hits as a bug.
     * [featureId] format: "feature_name" or "feature_name:value"
     */
    fun onStubAction(featureId: String)
}

// ─────────────────────────────────────────────────────────────────────────────
// POI layer ID constants — shared between AppBarMenuManager and MainActivity
// ─────────────────────────────────────────────────────────────────────────────
object PoiLayerId {
    const val FOOD_DRINK      = "food_drink"
    const val FUEL_CHARGING   = "fuel_charging"
    const val TRANSIT         = "transit"
    const val CIVIC           = "civic"
    const val PARKS_REC       = "parks_rec"
    const val SHOPPING        = "shopping"
    const val HEALTHCARE      = "healthcare"
    const val EDUCATION       = "education"
    const val LODGING         = "lodging"
    const val PARKING         = "parking"
    const val FINANCE         = "finance"
    const val WORSHIP         = "worship"
    const val HISTORICAL_BUILDINGS = "historical_buildings"
    const val EMERGENCY            = "emergency"
    const val AUTO_SERVICES        = "auto_services"
    const val ENTERTAINMENT        = "entertainment"
    const val OFFICES              = "offices"

    // Salem-specific categories
    const val WITCH_SHOP           = "witch_shop"
    const val PSYCHIC              = "psychic"
    const val TOUR_COMPANIES       = "tour_companies"
}
