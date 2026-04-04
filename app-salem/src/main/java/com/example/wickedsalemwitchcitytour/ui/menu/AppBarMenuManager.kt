/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui.menu

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import com.example.wickedsalemwitchcitytour.R
import com.example.locationmapapp.ui.menu.MenuEventListener
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.ui.menu.PoiLayerId
import com.example.locationmapapp.util.DebugLogger
import com.google.android.material.slider.Slider

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module AppBarMenuManager.kt"

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
    private val menuEventListener: MenuEventListener
) {

    private val TAG = "AppBarMenuManager"

    private val prefs: SharedPreferences =
        context.getSharedPreferences(MenuPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    var radarUpdateMinutes: Int
        get() = prefs.getInt(MenuPrefs.PREF_RADAR_FREQ, MenuPrefs.DEFAULT_RADAR_FREQ_MIN)
        set(v) { prefs.edit().putInt(MenuPrefs.PREF_RADAR_FREQ, v).apply() }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 1 — called from onCreate (no menu items exist yet)
    // ─────────────────────────────────────────────────────────────────────────

    fun setupToolbarMenus() {
        DebugLogger.i(TAG, "setupToolbarMenus() — deferring click wiring until onMenuInflated()")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 2 — called from onCreateOptionsMenu AFTER menuInflater.inflate()
    // (legacy single-row path — kept for compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    fun onMenuInflated(menu: Menu) {
        DebugLogger.i(TAG, "onMenuInflated() — ${menu.size()} top-level items")
        toolbar.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Top-bar click: '${item.title}' id=0x${item.itemId.toString(16)}")
            when (item.itemId) {
                R.id.menu_top_weather    -> { menuEventListener.onWeatherRequested(); true }
                R.id.menu_top_transit    -> { showTransitMenu(toolbar);    true }
                R.id.menu_top_cams       -> { showCamsMenu(toolbar);       true }
                R.id.menu_top_aircraft   -> { showAircraftMenu(toolbar);   true }
                R.id.menu_top_radar      -> { showRadarMenu(toolbar);      true }
                R.id.menu_top_poi        -> { showPoiMenu(toolbar);        true }
                R.id.menu_top_utility    -> { showUtilityMenu(toolbar);    true }
                R.id.menu_top_find       -> { menuEventListener.onFindRequested();   true }
                R.id.menu_top_goto       -> { menuEventListener.onGoToLocationRequested(); true }
                else -> {
                    DebugLogger.w(TAG, "No sub-menu for id=0x${item.itemId.toString(16)}")
                    false
                }
            }
        }
        DebugLogger.i(TAG, "Toolbar click listener wired")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SLIM TOOLBAR — Weather + Home + spacer + DarkMode + Alerts + Grid + About
    // ─────────────────────────────────────────────────────────────────────────

    data class SlimToolbarRefs(
        val weatherIcon: ImageView,
        val alertsIcon: ImageView,
        val gridButton: ImageView,
        val statusLine: TextView,
        val alertsBadge: TextView? = null,
        val layersBadge: TextView? = null
    )

    /**
     * Wire slim toolbar icons with click listeners.
     * Weather → onWeatherRequested, Home → onHomeRequested, Alerts → onAlertsRequested,
     * Grid → showGridDropdown, About → onAboutRequested.
     */
    fun setupSlimToolbar(
        weatherIcon: ImageView,
        alertsIcon: ImageView,
        gridButton: ImageView,
        statusLine: TextView,
        darkModeIcon: ImageView? = null,
        homeIcon: ImageView? = null,
        aboutIcon: ImageView? = null,
        alertsBadge: TextView? = null,
        layersBadge: TextView? = null
    ): SlimToolbarRefs {
        weatherIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        alertsIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)

        weatherIcon.setOnClickListener { menuEventListener.onWeatherRequested() }
        alertsIcon.setOnClickListener { menuEventListener.onAlertsRequested() }
        gridButton.setOnClickListener { showGridDropdown(it) }

        // Home icon — tap: go home, long-press: set/clear home
        homeIcon?.let { icon ->
            val homeSet = prefs.getBoolean(MenuPrefs.PREF_HOME_SET, false)
            icon.imageTintList = ColorStateList.valueOf(
                if (homeSet) Color.parseColor("#4DB6AC") else Color.WHITE
            )
            icon.setOnClickListener { menuEventListener.onHomeRequested() }
            icon.setOnLongClickListener {
                val isSet = prefs.getBoolean(MenuPrefs.PREF_HOME_SET, false)
                if (isSet) {
                    prefs.edit()
                        .remove(MenuPrefs.PREF_HOME_LAT)
                        .remove(MenuPrefs.PREF_HOME_LON)
                        .putBoolean(MenuPrefs.PREF_HOME_SET, false)
                        .apply()
                    icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    DebugLogger.i(TAG, "Home location cleared")
                    menuEventListener.onHomeLongPressed(false)
                } else {
                    menuEventListener.onHomeLongPressed(true)
                }
                true
            }
        }

        // About icon — show app info
        aboutIcon?.let { icon ->
            icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            icon.setOnClickListener { menuEventListener.onAboutRequested() }
        }

        // Dark mode toggle icon
        darkModeIcon?.let { icon ->
            val isDark = prefs.getBoolean(MenuPrefs.PREF_DARK_MODE, false)
            icon.alpha = if (isDark) 1.0f else 0.4f
            icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            icon.setOnClickListener {
                val newState = !prefs.getBoolean(MenuPrefs.PREF_DARK_MODE, false)
                prefs.edit().putBoolean(MenuPrefs.PREF_DARK_MODE, newState).apply()
                icon.alpha = if (newState) 1.0f else 0.4f
                DebugLogger.i(TAG, "Dark mode toggled → $newState")
                menuEventListener.onDarkModeToggled(newState)
            }
        }

        DebugLogger.i(TAG, "setupSlimToolbar() — icons wired (Weather, Home, DarkMode, Alerts, Grid, About)")
        return SlimToolbarRefs(weatherIcon, alertsIcon, gridButton, statusLine, alertsBadge, layersBadge)
    }

    /** Update the weather alert badge count on the alerts icon. */
    fun updateAlertBadge(badge: TextView?, count: Int) {
        badge ?: return
        if (count > 0) {
            badge.text = count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    /** Update the active layer count badge on the grid/menu icon. */
    fun updateLayerBadge(badge: TextView?, count: Int) {
        badge ?: return
        if (count > 0) {
            badge.text = count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    /** Count how many data layers are currently enabled. */
    fun computeActiveLayerCount(): Int {
        var count = 0
        if (prefs.getBoolean(MenuPrefs.PREF_AIRCRAFT_DISPLAY, true)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_MBTA_TRAINS, false)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_MBTA_SUBWAY, false)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_MBTA_BUSES, false)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_RADAR_ON, false)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_METAR_DISPLAY, false)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_WEBCAMS_ON, true)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_TFR_OVERLAY, false)) count++
        if (prefs.getBoolean(MenuPrefs.PREF_CAMERA_OVERLAY, false)) count++
        return count
    }

    /**
     * Show a PopupWindow dropdown with 8 labeled buttons in a 2×4 grid.
     * Row 1: Transit, Webcams, Aircraft, Radar
     * Row 2: POI, Utility, Find, Go To
     */
    fun showGridDropdown(anchor: View) {
        val inflater = LayoutInflater.from(context)
        val panel = inflater.inflate(R.layout.grid_dropdown_panel, null)
        val row1 = panel.findViewById<LinearLayout>(R.id.gridRow1)
        val row2 = panel.findViewById<LinearLayout>(R.id.gridRow2)
        val row3 = panel.findViewById<LinearLayout>(R.id.gridRow3)
        val row4 = panel.findViewById<LinearLayout>(R.id.gridRow4)

        val popup = PopupWindow(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true

        data class GridBtn(val iconRes: Int, val label: String, val onClick: (View) -> Unit)

        val row1Btns = listOf(
            GridBtn(R.drawable.ic_transit_rail, "Transit") { popup.dismiss(); showTransitMenu(anchor) },
            GridBtn(R.drawable.ic_camera,       "Webcams") { popup.dismiss(); showCamsMenu(anchor) },
            GridBtn(R.drawable.ic_aircraft,     "Aircraft") { popup.dismiss(); showAircraftMenu(anchor) },
            GridBtn(R.drawable.ic_radar,        "Radar") { popup.dismiss(); showRadarMenu(anchor) }
        )
        val row2Btns = listOf(
            GridBtn(R.drawable.ic_poi,           "POI") { popup.dismiss(); showPoiMenu(anchor) },
            GridBtn(R.drawable.ic_debug,         "Utility") { popup.dismiss(); showUtilityMenu(anchor) },
            GridBtn(R.drawable.ic_search,        "Find") { popup.dismiss(); menuEventListener.onFindRequested() },
            GridBtn(R.drawable.ic_goto_location, "Go To") { popup.dismiss(); menuEventListener.onGoToLocationRequested() }
        )

        fun addGridButtons(row: LinearLayout, btns: List<GridBtn>) {
            for (btn in btns) {
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    val dp8 = dp(8)
                    setPadding(dp8, dp8, dp8, dp8)
                    val ripple = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                    setBackgroundResource(ripple.resourceId)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener(btn.onClick)
                }
                val icon = ImageView(context).apply {
                    setImageResource(btn.iconRes)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                }
                val label = TextView(context).apply {
                    text = btn.label
                    textSize = 10f
                    setTextColor(Color.parseColor("#CCFFFFFF"))
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = dp(2)
                    }
                }
                cell.addView(icon)
                cell.addView(label)
                row.addView(cell)
            }
        }

        val row3Btns = listOf(
            GridBtn(R.drawable.ic_social,  "Social")  { popup.dismiss(); menuEventListener.onSocialRequested() },
            GridBtn(R.drawable.ic_chat,    "Chat")    { popup.dismiss(); menuEventListener.onChatRequested() },
            GridBtn(R.drawable.ic_profile, "Profile") { popup.dismiss(); menuEventListener.onProfileRequested() },
            GridBtn(R.drawable.ic_legend,  "Legend")  { popup.dismiss(); menuEventListener.onLegendRequested() }
        )

        val row4Btns = listOf(
            GridBtn(R.drawable.ic_tour,   "Tours")  { popup.dismiss(); menuEventListener.onTourRequested() },
            GridBtn(R.drawable.ic_events, "Events") { popup.dismiss(); menuEventListener.onEventsRequested() }
        )

        addGridButtons(row1, row1Btns)
        addGridButtons(row2, row2Btns)
        addGridButtons(row3, row3Btns)
        addGridButtons(row4, row4Btns)

        popup.showAsDropDown(anchor)
        DebugLogger.i(TAG, "showGridDropdown() — 14 buttons shown")
    }

    /** Density-independent pixel helper. */
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    // =========================================================================
    // PUBLIC TRANSIT
    // =========================================================================

    private fun showTransitMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_transit)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Transit: '${item.title}'")
            when (item.itemId) {
                R.id.menu_mbta_stations ->
                    toggleBinary(item, MenuPrefs.PREF_MBTA_STATIONS) { menuEventListener.onMbtaStationsToggled(it) }

                R.id.menu_mbta_bus_stops ->
                    toggleBinary(item, MenuPrefs.PREF_MBTA_BUS_STOPS) { menuEventListener.onMbtaBusStopsToggled(it) }

                R.id.menu_mbta_trains ->
                    toggleBinary(item, MenuPrefs.PREF_MBTA_TRAINS) { menuEventListener.onMbtaTrainsToggled(it) }

                R.id.menu_mbta_trains_frequency ->
                    showSliderDialog("Commuter Rail Frequency (sec)", 30, 300,
                        prefs.getInt(MenuPrefs.PREF_MBTA_TRAINS_FREQ, 60)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_MBTA_TRAINS_FREQ, v).apply()
                        menuEventListener.onMbtaTrainsFrequencyChanged(v)
                    }

                R.id.menu_mbta_subway ->
                    toggleBinary(item, MenuPrefs.PREF_MBTA_SUBWAY) { menuEventListener.onMbtaSubwayToggled(it) }

                R.id.menu_mbta_subway_frequency ->
                    showSliderDialog("Subway Frequency (sec)", 30, 300,
                        prefs.getInt(MenuPrefs.PREF_MBTA_SUBWAY_FREQ, 60)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_MBTA_SUBWAY_FREQ, v).apply()
                        menuEventListener.onMbtaSubwayFrequencyChanged(v)
                    }

                R.id.menu_mbta_buses ->
                    toggleBinary(item, MenuPrefs.PREF_MBTA_BUSES) { menuEventListener.onMbtaBusesToggled(it) }

                R.id.menu_mbta_buses_frequency ->
                    showSliderDialog("Bus Frequency (sec)", 30, 300,
                        prefs.getInt(MenuPrefs.PREF_MBTA_BUSES_FREQ, 60)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_MBTA_BUSES_FREQ, v).apply()
                        menuEventListener.onMbtaBusesFrequencyChanged(v)
                    }

                R.id.menu_national_alerts ->
                    toggleBinary(item, MenuPrefs.PREF_NAT_ALERTS) { menuEventListener.onNationalAlertsToggled(it) }

                R.id.menu_national_alerts_frequency ->
                    showSliderDialog("Emergency Alert Frequency (min)", 1, 15,
                        prefs.getInt(MenuPrefs.PREF_NAT_ALERTS_FREQ, 5)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_NAT_ALERTS_FREQ, v).apply()
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
            R.id.menu_mbta_stations   to MenuPrefs.PREF_MBTA_STATIONS,
            R.id.menu_mbta_bus_stops  to MenuPrefs.PREF_MBTA_BUS_STOPS,
            R.id.menu_mbta_trains     to MenuPrefs.PREF_MBTA_TRAINS,
            R.id.menu_mbta_subway     to MenuPrefs.PREF_MBTA_SUBWAY,
            R.id.menu_mbta_buses      to MenuPrefs.PREF_MBTA_BUSES,
            R.id.menu_national_alerts to MenuPrefs.PREF_NAT_ALERTS
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
                    toggleBinary(item, MenuPrefs.PREF_WEBCAMS_ON) { menuEventListener.onWebcamToggled(it) }

                R.id.menu_webcam_categories ->
                    showWebcamCategoryDialog()

                else -> {
                    DebugLogger.w(TAG, "CAMs: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("cams_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu, R.id.menu_webcams to MenuPrefs.PREF_WEBCAMS_ON)
        popup.show()
    }

    /** Windy webcam categories (matches Windy API v3). */
    private val WEBCAM_CATEGORIES = arrayOf(
        "traffic", "city", "village", "beach", "coast", "port",
        "lake", "river", "mountain", "forest", "landscape", "indoor",
        "airport", "building", "square", "observatory", "meteo", "sportArea"
    )

    private fun showWebcamCategoryDialog() {
        val saved = prefs.getStringSet(MenuPrefs.PREF_WEBCAM_CATEGORIES, setOf("traffic")) ?: setOf("traffic")
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
                prefs.edit().putStringSet(MenuPrefs.PREF_WEBCAM_CATEGORIES, selected).apply()
                DebugLogger.i(TAG, "Webcam categories ${if (newState) "select all" else "deselect all"}")
                menuEventListener.onWebcamCategoriesChanged(selected)
                dialog.dismiss()
            }
            .setPositiveButton("OK") { _, _ ->
                val selected = WEBCAM_CATEGORIES.filterIndexed { i, _ -> checked[i] }.toSet()
                prefs.edit().putStringSet(MenuPrefs.PREF_WEBCAM_CATEGORIES, selected).apply()
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
                    toggleBinary(item, MenuPrefs.PREF_AIRCRAFT_DISPLAY) { menuEventListener.onAircraftDisplayToggled(it) }

                R.id.menu_aircraft_frequency ->
                    showSliderDialog("Aircraft Update Frequency (sec)", 30, 300,
                        prefs.getInt(MenuPrefs.PREF_AIRCRAFT_FREQ, 60)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_AIRCRAFT_FREQ, v).apply()
                        menuEventListener.onAircraftFrequencyChanged(v)
                    }

                R.id.menu_auto_follow_aircraft -> {
                    val newState = !prefs.getBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, false)
                    prefs.edit().putBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, newState).apply()
                    item.isChecked = newState
                    DebugLogger.i(TAG, "toggleBinary '${MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT}' → $newState")
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
            R.id.menu_aircraft_display to MenuPrefs.PREF_AIRCRAFT_DISPLAY
        )
        // Auto-follow defaults to OFF, sync manually
        popup.menu.findItem(R.id.menu_auto_follow_aircraft)?.isChecked =
            prefs.getBoolean(MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, false)
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
                    toggleBinary(item, MenuPrefs.PREF_RADAR_ON) { menuEventListener.onRadarToggled(it) }

                R.id.menu_radar_visibility ->
                    showSliderDialog("Radar Visibility (0–100)", 0, 100,
                        prefs.getInt(MenuPrefs.PREF_RADAR_VISIBILITY, 70)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_RADAR_VISIBILITY, v).apply()
                        menuEventListener.onRadarVisibilityChanged(v)
                    }

                R.id.menu_radar_frequency ->
                    showSliderDialog("Radar Update (minutes)", 5, 15,
                        radarUpdateMinutes) { v ->
                        radarUpdateMinutes = v
                        menuEventListener.onRadarFrequencyChanged(v)
                    }

                R.id.menu_radar_animate ->
                    toggleBinary(item, MenuPrefs.PREF_RADAR_ANIMATE) { menuEventListener.onRadarAnimateToggled(it) }

                R.id.menu_radar_anim_speed ->
                    showSliderDialog("Animation Speed (ms)", 300, 2000,
                        prefs.getInt(MenuPrefs.PREF_RADAR_ANIM_SPEED, MenuPrefs.DEFAULT_RADAR_ANIM_SPEED)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_RADAR_ANIM_SPEED, v).apply()
                        menuEventListener.onRadarAnimSpeedChanged(v)
                    }

                R.id.menu_metar_display ->
                    toggleBinary(item, MenuPrefs.PREF_METAR_DISPLAY) { menuEventListener.onMetarDisplayToggled(it) }

                R.id.menu_metar_frequency ->
                    showSliderDialog("METAR Update Frequency (min)", 1, 10,
                        prefs.getInt(MenuPrefs.PREF_METAR_FREQ, 5)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_METAR_FREQ, v).apply()
                        menuEventListener.onMetarFrequencyChanged(v)
                    }

                else -> {
                    DebugLogger.w(TAG, "Radar: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("radar_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_radar_toggle to MenuPrefs.PREF_RADAR_ON,
            R.id.menu_radar_animate to MenuPrefs.PREF_RADAR_ANIMATE,
            R.id.menu_metar_display to MenuPrefs.PREF_METAR_DISPLAY
        )
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
            if (item.itemId == R.id.menu_poi_all_on) {
                enableAllPois(popup.menu)
                return@setOnMenuItemClickListener true
            }
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

    /** Enable all 16 POI categories at once and notify the listener for each. */
    private fun enableAllPois(menu: Menu) {
        DebugLogger.i(TAG, "enableAllPois — turning on all 16 categories")
        for ((resId, cat) in menuIdToCategory) {
            prefs.edit().putBoolean(cat.prefKey, true).apply()
            menu.findItem(resId)?.isChecked = true
            menuEventListener.onPoiLayerToggled(cat.id, true)
        }
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
                    toggleBinary(item, MenuPrefs.PREF_RECORD_GPS) { menuEventListener.onGpsRecordingToggled(it) }

                R.id.menu_util_build_story   -> menuEventListener.onBuildStoryRequested()
                R.id.menu_util_analyze_today -> menuEventListener.onAnalyzeTodayRequested()
                R.id.menu_util_anomalies     -> menuEventListener.onTravelAnomaliesRequested()
                R.id.menu_util_email_gpx     -> menuEventListener.onEmailGpxRequested()
                R.id.menu_util_populate_pois -> {
                    val running = prefs.getBoolean(MenuPrefs.PREF_POPULATE_POIS, false)
                    val newState = !running
                    prefs.edit().putBoolean(MenuPrefs.PREF_POPULATE_POIS, newState).apply()
                    DebugLogger.i(TAG, "Populate POIs → $newState")
                    menuEventListener.onPopulatePoisToggled(newState)
                }

                R.id.menu_util_probe_10km    -> menuEventListener.onProbe10kmRequested()
                R.id.menu_util_fill_probe    -> menuEventListener.onFillProbeRequested()
                R.id.menu_util_debug_log     -> menuEventListener.onDebugLogRequested()

                R.id.menu_util_gps_mode ->
                    toggleBinary(item, MenuPrefs.PREF_GPS_MODE) { menuEventListener.onGpsModeToggled(it) }

                R.id.menu_util_silent_fill_debug ->
                    toggleBinary(item, MenuPrefs.PREF_SILENT_FILL_DEBUG) { menuEventListener.onSilentFillDebugToggled(it) }

                R.id.menu_util_legend -> menuEventListener.onLegendRequested()

                else -> {
                    DebugLogger.w(TAG, "Utility: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("utility_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_util_record_gps          to MenuPrefs.PREF_RECORD_GPS,
            R.id.menu_util_gps_mode            to MenuPrefs.PREF_GPS_MODE,
            R.id.menu_util_silent_fill_debug   to MenuPrefs.PREF_SILENT_FILL_DEBUG
        )
        // Update populate title to reflect running state
        val popRunning = prefs.getBoolean(MenuPrefs.PREF_POPULATE_POIS, false)
        popup.menu.findItem(R.id.menu_util_populate_pois)?.title =
            if (popRunning) "\u2316 Populate POIs (active)" else "Populate POIs"
        popup.show()
    }

    // =========================================================================
    // ALERTS / GEOFENCE
    // =========================================================================

    fun showAlertsMenu(anchor: View) {
        val popup = buildPopup(anchor, R.menu.menu_alerts)
        popup.setOnMenuItemClickListener { item ->
            DebugLogger.i(TAG, "Alerts: '${item.title}'")
            when (item.itemId) {
                R.id.menu_tfr_overlay ->
                    toggleBinary(item, MenuPrefs.PREF_TFR_OVERLAY) { menuEventListener.onTfrOverlayToggled(it) }

                R.id.menu_camera_overlay ->
                    toggleBinary(item, MenuPrefs.PREF_CAMERA_OVERLAY) { menuEventListener.onCameraOverlayToggled(it) }

                R.id.menu_school_overlay ->
                    toggleBinary(item, MenuPrefs.PREF_SCHOOL_OVERLAY) { menuEventListener.onSchoolOverlayToggled(it) }

                R.id.menu_flood_overlay ->
                    toggleBinary(item, MenuPrefs.PREF_FLOOD_OVERLAY) { menuEventListener.onFloodOverlayToggled(it) }

                R.id.menu_crossing_overlay ->
                    toggleBinary(item, MenuPrefs.PREF_CROSSING_OVERLAY) { menuEventListener.onCrossingOverlayToggled(it) }

                R.id.menu_alert_sound ->
                    toggleBinary(item, MenuPrefs.PREF_ALERT_SOUND) { menuEventListener.onAlertSoundToggled(it) }

                R.id.menu_alert_distance ->
                    showSliderDialog("Alert Distance (NM)", 1, 20,
                        prefs.getInt(MenuPrefs.PREF_ALERT_DISTANCE, 5)) { v ->
                        prefs.edit().putInt(MenuPrefs.PREF_ALERT_DISTANCE, v).apply()
                        menuEventListener.onAlertDistanceChanged(v)
                    }

                R.id.menu_manage_databases ->
                    menuEventListener.onDatabaseManagerRequested()

                else -> {
                    DebugLogger.w(TAG, "Alerts: unhandled id=0x${item.itemId.toString(16)}")
                    menuEventListener.onStubAction("alerts_unknown:0x${item.itemId.toString(16)}")
                }
            }
            true
        }
        syncCheckStates(popup.menu,
            R.id.menu_tfr_overlay to MenuPrefs.PREF_TFR_OVERLAY,
            R.id.menu_camera_overlay to MenuPrefs.PREF_CAMERA_OVERLAY,
            R.id.menu_school_overlay to MenuPrefs.PREF_SCHOOL_OVERLAY,
            R.id.menu_flood_overlay to MenuPrefs.PREF_FLOOD_OVERLAY,
            R.id.menu_crossing_overlay to MenuPrefs.PREF_CROSSING_OVERLAY,
            R.id.menu_alert_sound to MenuPrefs.PREF_ALERT_SOUND
        )
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
        MenuPrefs.PREF_AIRCRAFT_DISPLAY, MenuPrefs.PREF_AUTO_FOLLOW_AIRCRAFT, MenuPrefs.PREF_POPULATE_POIS, MenuPrefs.PREF_MBTA_BUS_STOPS,
        MenuPrefs.PREF_ALERT_SOUND, MenuPrefs.PREF_CAMERA_OVERLAY, MenuPrefs.PREF_SCHOOL_OVERLAY, MenuPrefs.PREF_FLOOD_OVERLAY, MenuPrefs.PREF_CROSSING_OVERLAY,
        MenuPrefs.PREF_RADAR_ANIMATE, MenuPrefs.PREF_DARK_MODE -> false
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

}
