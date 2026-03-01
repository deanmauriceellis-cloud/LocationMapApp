package com.example.locationmapapp.ui.menu

import android.content.Context
import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import com.example.locationmapapp.R
import com.example.locationmapapp.ui.MainViewModel
import com.example.locationmapapp.util.DebugLogger
import com.google.android.material.slider.Slider

/**
 * AppBarMenuManager  — v1.5
 *
 * Owns all toolbar / drop-down menu logic:
 *   • Inflates seven top-level menu-bar items (GPS Alerts, Transit, CAMs, Air, Radar, POI, Utility).
 *   • Each item opens a PopupMenu with checkable binary items and slider-launched dialogs.
 *   • Binary state is persisted to SharedPreferences; checkmarks are synced each time a
 *     popup opens so they always reflect the stored value.
 *   • All events are forwarded through [MenuEventListener] — zero business logic here.
 *
 * Two-phase wiring pattern (preserves pre-inflation safety):
 *   Phase 1 — setupToolbarMenus()   called from onCreate()              (no menu items yet)
 *   Phase 2 — onMenuInflated(menu)  called from onCreateOptionsMenu()   (items now exist)
 */
class AppBarMenuManager(
    private val context:           Context,
    private val toolbar:           Toolbar,
    private val viewModel:         MainViewModel,
    private val menuEventListener: MenuEventListener
) {

    private val TAG = "AppBarMenuManager"

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var radarUpdateMinutes: Int
        get() = prefs.getInt(PREF_RADAR_FREQ, DEFAULT_RADAR_FREQ_MIN)
        set(v) { prefs.edit().putInt(PREF_RADAR_FREQ, v).apply() }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 1 — called from onCreate (no menu items exist yet)
    // ─────────────────────────────────────────────────────────────────────────

    fun setupToolbarMenus() {
        DebugLogger.i(TAG, "setupToolbarMenus() — deferring click wiring until onMenuInflated()")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 2 — called from onCreateOptionsMenu AFTER menuInflater.inflate()
    // ─────────────────────────────────────────────────────────────────────────

    fun onMenuInflated(menu: Menu) {
        DebugLogger.i(TAG, "onMenuInflated() — ${menu.size()} top-level items")
        toolbar.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Top-bar click: '${item.title}' id=0x${item.itemId.toString(16)}")
            when (item.itemId) {
                R.id.menu_top_gps_alerts -> { showGpsAlertsMenu(toolbar); true }
                R.id.menu_top_transit    -> { showTransitMenu(toolbar);    true }
                R.id.menu_top_cams       -> { showCamsMenu(toolbar);       true }
                R.id.menu_top_aircraft   -> { showAircraftMenu(toolbar);   true }
                R.id.menu_top_radar      -> { showRadarMenu(toolbar);      true }
                R.id.menu_top_poi        -> { showPoiMenu(toolbar);        true }
                R.id.menu_top_utility    -> { showUtilityMenu(toolbar);    true }
                else -> {
                    DebugLogger.w(TAG, "No sub-menu for id=0x${item.itemId.toString(16)}")
                    false
                }
            }
        }
        DebugLogger.i(TAG, "Toolbar click listener wired")
    }

    // =========================================================================
    // GPS ALERTS
    // =========================================================================

    private fun showGpsAlertsMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_gps_alerts)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "GPS Alerts: '${item.title}'")
            when (item.itemId) {
                R.id.menu_weather_alerts ->
                    toggleBinary(item, PREF_WEATHER_ALERTS) { menuEventListener.onWeatherAlertsToggled(it) }

                R.id.menu_weather_banner ->
                    toggleBinary(item, PREF_WEATHER_BANNER) { menuEventListener.onWeatherBannerToggled(it) }

                R.id.menu_highway_alerts ->
                    toggleBinary(item, PREF_HWY_ALERTS) { menuEventListener.onHighwayAlertsToggled(it) }

                R.id.menu_highway_alerts_frequency ->
                    showSliderDialog("Highway Alert Frequency (min)", 1, 5,
                        prefs.getInt(PREF_HWY_ALERT_FREQ, 3)) { v ->
                        prefs.edit().putInt(PREF_HWY_ALERT_FREQ, v).apply()
                        menuEventListener.onHighwayAlertsFrequencyChanged(v)
                    }

                R.id.menu_traffic_speed ->
                    toggleBinary(item, PREF_TRAFFIC_SPEED) { menuEventListener.onTrafficSpeedToggled(it) }

                R.id.menu_traffic_speed_frequency ->
                    showSliderDialog("Speed Info Frequency (min)", 1, 5,
                        prefs.getInt(PREF_TRAFFIC_SPEED_FREQ, 3)) { v ->
                        prefs.edit().putInt(PREF_TRAFFIC_SPEED_FREQ, v).apply()
                        menuEventListener.onTrafficSpeedFrequencyChanged(v)
                    }

                R.id.menu_metar_display ->
                    toggleBinary(item, PREF_METAR_DISPLAY) { menuEventListener.onMetarDisplayToggled(it) }

                R.id.menu_metar_frequency ->
                    showSliderDialog("METAR Update Frequency (min)", 1, 10,
                        prefs.getInt(PREF_METAR_FREQ, 5)) { v ->
                        prefs.edit().putInt(PREF_METAR_FREQ, v).apply()
                        menuEventListener.onMetarFrequencyChanged(v)
                    }

                else -> {
                    DebugLogger.w(TAG, "GPS Alerts: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("gps_alerts_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_weather_alerts   to PREF_WEATHER_ALERTS,
            R.id.menu_weather_banner   to PREF_WEATHER_BANNER,
            R.id.menu_highway_alerts   to PREF_HWY_ALERTS,
            R.id.menu_traffic_speed    to PREF_TRAFFIC_SPEED,
            R.id.menu_metar_display    to PREF_METAR_DISPLAY
        )
        popup.show()
    }

    // =========================================================================
    // PUBLIC TRANSIT
    // =========================================================================

    private fun showTransitMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_transit)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Transit: '${item.title}'")
            when (item.itemId) {
                R.id.menu_mbta_trains ->
                    toggleBinary(item, PREF_MBTA_TRAINS) { menuEventListener.onMbtaTrainsToggled(it) }

                R.id.menu_mbta_trains_frequency ->
                    showSliderDialog("Commuter Rail Frequency (sec)", 30, 300,
                        prefs.getInt(PREF_MBTA_TRAINS_FREQ, 60)) { v ->
                        prefs.edit().putInt(PREF_MBTA_TRAINS_FREQ, v).apply()
                        menuEventListener.onMbtaTrainsFrequencyChanged(v)
                    }

                R.id.menu_mbta_subway ->
                    toggleBinary(item, PREF_MBTA_SUBWAY) { menuEventListener.onMbtaSubwayToggled(it) }

                R.id.menu_mbta_subway_frequency ->
                    showSliderDialog("Subway Frequency (sec)", 30, 300,
                        prefs.getInt(PREF_MBTA_SUBWAY_FREQ, 60)) { v ->
                        prefs.edit().putInt(PREF_MBTA_SUBWAY_FREQ, v).apply()
                        menuEventListener.onMbtaSubwayFrequencyChanged(v)
                    }

                R.id.menu_mbta_buses ->
                    toggleBinary(item, PREF_MBTA_BUSES) { menuEventListener.onMbtaBusesToggled(it) }

                R.id.menu_mbta_buses_frequency ->
                    showSliderDialog("Bus Frequency (sec)", 30, 300,
                        prefs.getInt(PREF_MBTA_BUSES_FREQ, 60)) { v ->
                        prefs.edit().putInt(PREF_MBTA_BUSES_FREQ, v).apply()
                        menuEventListener.onMbtaBusesFrequencyChanged(v)
                    }

                R.id.menu_national_alerts ->
                    toggleBinary(item, PREF_NAT_ALERTS) { menuEventListener.onNationalAlertsToggled(it) }

                R.id.menu_national_alerts_frequency ->
                    showSliderDialog("Emergency Alert Frequency (min)", 1, 15,
                        prefs.getInt(PREF_NAT_ALERTS_FREQ, 5)) { v ->
                        prefs.edit().putInt(PREF_NAT_ALERTS_FREQ, v).apply()
                        menuEventListener.onNationalAlertsFrequencyChanged(v)
                    }

                else -> {
                    DebugLogger.w(TAG, "Transit: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("transit_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_mbta_trains     to PREF_MBTA_TRAINS,
            R.id.menu_mbta_subway     to PREF_MBTA_SUBWAY,
            R.id.menu_mbta_buses      to PREF_MBTA_BUSES,
            R.id.menu_national_alerts to PREF_NAT_ALERTS
        )
        popup.show()
    }

    // =========================================================================
    // CAMERAS
    // =========================================================================

    private fun showCamsMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_cams)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "CAMs: '${item.title}'")
            when (item.itemId) {
                R.id.menu_webcams ->
                    toggleBinary(item, PREF_WEBCAMS_ON) { menuEventListener.onWebcamToggled(it) }

                R.id.menu_webcam_categories ->
                    showWebcamCategoryDialog()

                else -> {
                    DebugLogger.w(TAG, "CAMs: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("cams_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu, R.id.menu_webcams to PREF_WEBCAMS_ON)
        popup.show()
    }

    /** Windy webcam categories (matches Windy API v3). */
    private val WEBCAM_CATEGORIES = arrayOf(
        "traffic", "city", "village", "beach", "coast", "port",
        "lake", "river", "mountain", "forest", "landscape", "indoor",
        "airport", "building", "square", "observatory", "meteo", "sportArea"
    )

    private fun showWebcamCategoryDialog() {
        val saved = prefs.getStringSet(PREF_WEBCAM_CATEGORIES, setOf("traffic")) ?: setOf("traffic")
        val checked = BooleanArray(WEBCAM_CATEGORIES.size) { i -> saved.contains(WEBCAM_CATEGORIES[i]) }
        val labels = WEBCAM_CATEGORIES.map { cat ->
            cat.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
        }.toTypedArray()

        // Track whether all are selected for the toggle button
        var allSelected = checked.all { it }

        AlertDialog.Builder(context)
            .setTitle("Camera Types")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNeutralButton(if (allSelected) "Deselect All" else "Select All") { dialog, _ ->
                // Toggle all
                val newState = !allSelected
                for (i in checked.indices) checked[i] = newState
                // Save and notify
                val selected = if (newState) WEBCAM_CATEGORIES.toSet() else emptySet()
                prefs.edit().putStringSet(PREF_WEBCAM_CATEGORIES, selected).apply()
                DebugLogger.i(TAG, "Webcam categories ${if (newState) "select all" else "deselect all"}")
                menuEventListener.onWebcamCategoriesChanged(selected)
                dialog.dismiss()
            }
            .setPositiveButton("OK") { _, _ ->
                val selected = WEBCAM_CATEGORIES.filterIndexed { i, _ -> checked[i] }.toSet()
                prefs.edit().putStringSet(PREF_WEBCAM_CATEGORIES, selected).apply()
                DebugLogger.i(TAG, "Webcam categories OK: $selected")
                menuEventListener.onWebcamCategoriesChanged(selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =========================================================================
    // AIRCRAFT
    // =========================================================================

    private fun showAircraftMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_aircraft)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Aircraft: '${item.title}'")
            when (item.itemId) {
                R.id.menu_aircraft_display ->
                    toggleBinary(item, PREF_AIRCRAFT_DISPLAY) { menuEventListener.onAircraftDisplayToggled(it) }

                R.id.menu_aircraft_frequency ->
                    showSliderDialog("Aircraft Update Frequency (sec)", 30, 300,
                        prefs.getInt(PREF_AIRCRAFT_FREQ, 60)) { v ->
                        prefs.edit().putInt(PREF_AIRCRAFT_FREQ, v).apply()
                        menuEventListener.onAircraftFrequencyChanged(v)
                    }

                R.id.menu_auto_follow_aircraft -> {
                    val newState = !prefs.getBoolean(PREF_AUTO_FOLLOW_AIRCRAFT, false)
                    prefs.edit().putBoolean(PREF_AUTO_FOLLOW_AIRCRAFT, newState).apply()
                    item.isChecked = newState
                    DebugLogger.i(TAG, "toggleBinary '$PREF_AUTO_FOLLOW_AIRCRAFT' → $newState")
                    menuEventListener.onAutoFollowAircraftToggled(newState)
                }

                else -> {
                    DebugLogger.w(TAG, "Aircraft: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("aircraft_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_aircraft_display to PREF_AIRCRAFT_DISPLAY
        )
        // Auto-follow defaults to OFF, sync manually
        popup.menu.findItem(R.id.menu_auto_follow_aircraft)?.isChecked =
            prefs.getBoolean(PREF_AUTO_FOLLOW_AIRCRAFT, false)
        popup.show()
    }

    // =========================================================================
    // RADAR
    // =========================================================================

    private fun showRadarMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_radar)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Radar: '${item.title}'")
            when (item.itemId) {
                R.id.menu_radar_toggle ->
                    toggleBinary(item, PREF_RADAR_ON) { menuEventListener.onRadarToggled(it) }

                R.id.menu_radar_visibility ->
                    showSliderDialog("Radar Visibility (0–100)", 0, 100,
                        prefs.getInt(PREF_RADAR_VISIBILITY, 70)) { v ->
                        prefs.edit().putInt(PREF_RADAR_VISIBILITY, v).apply()
                        menuEventListener.onRadarVisibilityChanged(v)
                    }

                R.id.menu_radar_frequency ->
                    showSliderDialog("Radar Update (minutes)", 5, 15,
                        radarUpdateMinutes) { v ->
                        radarUpdateMinutes = v
                        menuEventListener.onRadarFrequencyChanged(v)
                    }

                else -> {
                    DebugLogger.w(TAG, "Radar: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("radar_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu, R.id.menu_radar_toggle to PREF_RADAR_ON)
        popup.show()
    }

    // =========================================================================
    // POINTS OF INTEREST
    // =========================================================================

    /** Map from menu item XML id to PoiCategory.id — built once via reflection. */
    private val menuIdToCategory: Map<Int, PoiCategory> by lazy {
        PoiCategories.ALL.mapNotNull { cat ->
            val resName = "menu_poi_${cat.id}"
            val resId = context.resources.getIdentifier(resName, "id", context.packageName)
            if (resId != 0) resId to cat else null
        }.toMap()
    }

    private fun showPoiMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_poi)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "POI: '${item.title}'")
            val cat = menuIdToCategory[item.itemId]
            if (cat != null) {
                if (cat.subtypes != null) {
                    // Has subtypes → show subtype dialog (always, whether toggling on or off)
                    showPoiSubtypeDialog(cat, item)
                } else {
                    // Simple toggle — no subtypes
                    toggleBinary(item, cat.prefKey) { menuEventListener.onPoiLayerToggled(cat.id, it) }
                }
            } else {
                DebugLogger.w(TAG, "POI: unhandled id=0x${item.itemId.toString(16)}")
                menuEventListener.onStubAction("poi_unknown:0x${item.itemId.toString(16)}")
            }
            true
        }
        // Sync check states for all 16 categories
        val pairs = menuIdToCategory.map { (resId, cat) -> resId to cat.prefKey }.toTypedArray()
        syncCheckStates(popup.menu, *pairs)
        popup.show()
    }

    /**
     * Show an AlertDialog with multi-choice checkboxes for a category's subtypes.
     * Checking any subtype enables the layer; unchecking all disables it.
     * Selected subtypes are stored as a StringSet pref keyed "poi_{id}_subtypes".
     */
    private fun showPoiSubtypeDialog(cat: PoiCategory, menuItem: android.view.MenuItem) {
        val subtypes = cat.subtypes ?: return
        val subtypePrefKey = "poi_${cat.id}_subtypes"
        val savedSet = prefs.getStringSet(subtypePrefKey, null) ?: emptySet()

        val labels = subtypes.map { it.label }.toTypedArray()
        val checked = BooleanArray(subtypes.size) { i -> savedSet.contains(subtypes[i].label) }

        AlertDialog.Builder(context)
            .setTitle(cat.label)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val selected = subtypes.filterIndexed { i, _ -> checked[i] }.map { it.label }.toSet()
                prefs.edit().putStringSet(subtypePrefKey, selected).apply()
                val anySelected = selected.isNotEmpty()
                prefs.edit().putBoolean(cat.prefKey, anySelected).apply()
                menuItem.isChecked = anySelected
                DebugLogger.i(TAG, "POI subtype dialog OK: ${cat.id} selected=$selected enabled=$anySelected")
                menuEventListener.onPoiLayerToggled(cat.id, anySelected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Get the active Overpass tags for a category, filtered by selected subtypes.
     * If no subtypes exist or none saved, returns the full default tag list.
     */
    fun getActiveTags(categoryId: String): List<String> {
        val cat = PoiCategories.find(categoryId) ?: return emptyList()
        val subtypes = cat.subtypes ?: return cat.tags
        val subtypePrefKey = "poi_${cat.id}_subtypes"
        val savedSet = prefs.getStringSet(subtypePrefKey, null)
        if (savedSet.isNullOrEmpty()) return cat.tags
        return subtypes.filter { savedSet.contains(it.label) }.flatMap { it.tags }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private fun showUtilityMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_utility)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Utility: '${item.title}'")
            when (item.itemId) {
                R.id.menu_util_record_gps ->
                    toggleBinary(item, PREF_RECORD_GPS) { menuEventListener.onGpsRecordingToggled(it) }

                R.id.menu_util_build_story   -> menuEventListener.onBuildStoryRequested()
                R.id.menu_util_analyze_today -> menuEventListener.onAnalyzeTodayRequested()
                R.id.menu_util_anomalies     -> menuEventListener.onTravelAnomaliesRequested()
                R.id.menu_util_email_gpx     -> menuEventListener.onEmailGpxRequested()
                R.id.menu_util_populate_pois -> {
                    val running = prefs.getBoolean(PREF_POPULATE_POIS, false)
                    val newState = !running
                    prefs.edit().putBoolean(PREF_POPULATE_POIS, newState).apply()
                    DebugLogger.i(TAG, "Populate POIs → $newState")
                    menuEventListener.onPopulatePoisToggled(newState)
                }

                R.id.menu_util_debug_log     -> menuEventListener.onDebugLogRequested()

                R.id.menu_util_gps_mode ->
                    toggleBinary(item, PREF_GPS_MODE) { menuEventListener.onGpsModeToggled(it) }

                else -> {
                    DebugLogger.w(TAG, "Utility: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("utility_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_util_record_gps     to PREF_RECORD_GPS,
            R.id.menu_util_gps_mode       to PREF_GPS_MODE
        )
        // Update populate title to reflect running state
        val popRunning = prefs.getBoolean(PREF_POPULATE_POIS, false)
        popup.menu.findItem(R.id.menu_util_populate_pois)?.title =
            if (popRunning) "\u2316 Populate POIs (active)" else "Populate POIs"
        popup.show()
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Inflate [menuRes] into a PopupMenu anchored to [anchor].
     * Forces the icon/checkbox column visible via reflection so checkmarks render.
     */
    private fun buildPopup(anchor: View, menuRes: Int): PopupMenu {
        val popup = PopupMenu(context, anchor)
        popup.inflate(menuRes)

        // Reflection hack to force the icon gutter visible — without this,
        // android:checkable items show no visible tick on many OEM ROMs.
        try {
            val field = popup.javaClass.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popup)
            helper.javaClass.getMethod("setForceShowIcon", Boolean::class.java)
                .invoke(helper, true)
        } catch (e: Exception) {
            DebugLogger.w(TAG, "setForceShowIcon reflection failed: ${e.javaClass.simpleName}")
        }
        return popup
    }

    /** Default value for a given pref key (most default ON, aircraft defaults OFF). */
    private fun prefDefault(prefKey: String): Boolean = when (prefKey) {
        PREF_AIRCRAFT_DISPLAY, PREF_AUTO_FOLLOW_AIRCRAFT, PREF_POPULATE_POIS -> false
        else -> true
    }

    /**
     * Flip a boolean pref, update the menu item's checked state, notify caller.
     * Returns the NEW state.
     */
    private fun toggleBinary(
        item:     MenuItem,
        prefKey:  String,
        onChanged: (Boolean) -> Unit
    ) {
        val newState = !prefs.getBoolean(prefKey, prefDefault(prefKey))
        prefs.edit().putBoolean(prefKey, newState).apply()
        item.isChecked = newState
        DebugLogger.i(TAG, "toggleBinary '$prefKey' → $newState  ('${item.title}')")
        onChanged(newState)
    }

    /**
     * Read each pref key and apply its stored boolean as the checked state on the
     * corresponding menu item. Call this immediately after inflate and before show().
     */
    private fun syncCheckStates(menu: Menu, vararg pairs: Pair<Int, String>) {
        pairs.forEach { (id, prefKey) ->
            val item = menu.findItem(id)
            if (item != null) {
                item.isChecked = prefs.getBoolean(prefKey, prefDefault(prefKey))
            } else {
                DebugLogger.w(TAG, "syncCheckStates: findItem(0x${id.toString(16)}) null — check R.id vs menu XML")
            }
        }
    }

    /**
     * Show a modal dialog containing a Material [Slider] and an OK/Cancel button pair.
     * [onConfirm] receives the chosen integer value only on OK.
     */
    private fun showSliderDialog(
        title:     String,
        min:       Int,
        max:       Int,
        current:   Int,
        onConfirm: (Int) -> Unit
    ) {
        DebugLogger.i(TAG, "showSliderDialog '$title' min=$min max=$max current=$current")
        val slider = Slider(context).apply {
            valueFrom = min.toFloat()
            valueTo   = max.toFloat()
            value     = current.toFloat().coerceIn(min.toFloat(), max.toFloat())
            stepSize  = 1f
            setPadding(48, 32, 48, 16)
        }
        val label = TextView(context).apply {
            text     = "Value: $current"
            textSize = 14f
            setPadding(48, 8, 48, 0)
        }
        slider.addOnChangeListener { _, v, _ -> label.text = "Value: ${v.toInt()}" }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(slider)
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("OK")     { _, _ -> onConfirm(slider.value.toInt()) }
            .setNegativeButton("Cancel") { _, _ -> /* dismiss */ }
            .show()
    }

    // =========================================================================
    // PREFERENCE KEY CONSTANTS
    // =========================================================================

    companion object {

        const val PREFS_NAME = "app_bar_menu_prefs"

        // ── Radar ─────────────────────────────────────────────────────────────
        const val PREF_RADAR_ON          = "radar_on"
        const val PREF_RADAR_VISIBILITY  = "radar_visibility"
        const val PREF_RADAR_FREQ        = "radar_freq_min"
        const val DEFAULT_RADAR_FREQ_MIN = 5

        // ── GPS Alerts ────────────────────────────────────────────────────────
        const val PREF_WEATHER_ALERTS     = "weather_alerts_on"
        const val PREF_WEATHER_BANNER     = "weather_banner_on"
        const val PREF_HWY_ALERTS         = "highway_alerts_on"
        const val PREF_HWY_ALERT_FREQ     = "highway_alert_freq_min"
        const val PREF_TRAFFIC_SPEED      = "traffic_speed_on"
        const val PREF_TRAFFIC_SPEED_FREQ = "traffic_speed_freq_min"
        const val PREF_METAR_DISPLAY      = "metar_display_on"
        const val PREF_METAR_FREQ         = "metar_freq_min"
        const val PREF_AIRCRAFT_DISPLAY   = "aircraft_display_on"
        const val PREF_AIRCRAFT_FREQ      = "aircraft_freq_sec"

        // ── Transit ───────────────────────────────────────────────────────────
        const val PREF_MBTA_TRAINS      = "mbta_trains_on"
        const val PREF_MBTA_TRAINS_FREQ = "mbta_trains_freq_sec"
        const val PREF_MBTA_SUBWAY      = "mbta_subway_on"
        const val PREF_MBTA_SUBWAY_FREQ = "mbta_subway_freq_sec"
        const val PREF_MBTA_BUSES       = "mbta_buses_on"
        const val PREF_MBTA_BUSES_FREQ  = "mbta_buses_freq_sec"
        const val PREF_NAT_ALERTS       = "national_alerts_on"
        const val PREF_NAT_ALERTS_FREQ  = "national_alerts_freq_min"

        // ── Cameras ───────────────────────────────────────────────────────────
        const val PREF_WEBCAMS_ON          = "webcams_on"
        const val PREF_WEBCAM_CATEGORIES   = "webcam_categories"

        // ── POI ───────────────────────────────────────────────────────────────
        // POI pref keys now live in PoiCategories.ALL (PoiCategory.prefKey)
        // Old constants removed — use PoiCategories.find(id)?.prefKey

        // ── Utility ───────────────────────────────────────────────────────────
        const val PREF_RECORD_GPS            = "record_gps_on"
        const val PREF_GPS_MODE              = "gps_mode_auto"
        const val PREF_AUTO_FOLLOW_AIRCRAFT  = "auto_follow_aircraft_on"
        const val PREF_POPULATE_POIS         = "populate_pois_on"
    }
}
