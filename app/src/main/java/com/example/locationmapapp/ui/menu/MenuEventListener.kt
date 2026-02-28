package com.example.locationmapapp.ui.menu

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
    // GPS ALERTS
    // =========================================================================

    /** Live NWS weather alert polygons toggled on/off map. */
    fun onWeatherAlertsToggled(enabled: Boolean)

    /** Scrolling ribbon at bottom of map showing current-location weather summary + alerts. */
    fun onWeatherBannerToggled(enabled: Boolean)

    /** Highway incident / hazard markers toggled on map. */
    fun onHighwayAlertsToggled(enabled: Boolean)

    /** How often to refresh highway alert data. [minutes] in 1..5 */
    fun onHighwayAlertsFrequencyChanged(minutes: Int)

    /** Color-coded traffic speed overlay on road lines. */
    fun onTrafficSpeedToggled(enabled: Boolean)

    /** How often to refresh traffic speed data. [minutes] in 1..5 */
    fun onTrafficSpeedFrequencyChanged(minutes: Int)

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

    /** Switch between automatic GPS centering and manual tap-to-set-location mode. */
    fun onGpsModeToggled(autoGps: Boolean)

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
    const val TOURISM_HISTORY = "tourism_history"
    const val EMERGENCY       = "emergency"
    const val AUTO_SERVICES   = "auto_services"
    const val ENTERTAINMENT   = "entertainment"
}
