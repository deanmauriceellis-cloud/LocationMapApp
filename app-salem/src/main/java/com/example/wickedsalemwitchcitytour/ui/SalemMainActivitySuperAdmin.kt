/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * S249 — SuperAdmin mode UI: fourth top-bar button (debug-only, R8-stripped)
 * that toggles the runtime override (`SuperAdminMode.allowNetwork`) so the
 * parked V1+ external services (weather / MBTA / aircraft / METAR / webcams /
 * radar) become reachable for post-V1 dev verification on the Lenovo. Mirrors
 * the field-edit toggle pattern at SalemMainActivityFieldEdit.kt.
 *
 * Three-tier gate to flip:
 *   - Compile-time `BuildDefaults.SUPER_ADMIN_ENABLED` — controls toolbar
 *     visibility AND seeds `SuperAdminMode.setAvailable(true)` at startup.
 *     False in release; R8 strips this whole call site + the wireSuperAdminToolbar
 *     extension's body.
 *   - Runtime `SuperAdminMode` singleton — operator toggles via this icon.
 *   - Manifest `android.permission.INTERNET` — re-added in
 *     `app-salem/src/debug/AndroidManifest.xml` for debug builds only.
 *
 * Session-scoped — resets to OFF on every cold start. No SharedPreferences.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.SuperAdminMode
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.wickedsalemwitchcitytour.R

private const val TAG = "SuperAdminUI"

/**
 * Wire the fourth top-bar icon (`toolbarSuperAdminIcon`) to a session-scoped
 * ON/OFF toggle. Called once from [SalemMainActivity.onCreate] when
 * [BuildDefaults.SUPER_ADMIN_ENABLED] is true. R8 strips the whole call
 * (and this function's body) from retail builds.
 */
internal fun SalemMainActivity.wireSuperAdminToolbar() {
    // Seed the runtime override availability flag. In release this resolves
    // to setAvailable(false) at compile time and never lets the operator
    // enable network access — defense in depth alongside the manifest-tier
    // strip.
    SuperAdminMode.setAvailable(BuildDefaults.SUPER_ADMIN_ENABLED)

    val wrap = findViewById<View?>(R.id.toolbarSuperAdminWrap) ?: return
    val icon = findViewById<View?>(R.id.toolbarSuperAdminIcon) ?: return

    wrap.visibility = View.VISIBLE
    refreshSuperAdminIconTint()

    icon.setOnClickListener {
        val nowOn = !SuperAdminMode.isEnabled()
        SuperAdminMode.setEnabled(nowOn)
        refreshSuperAdminIconTint()
        // S250 — flip slim-toolbar Weather/Alerts icon visibility (the 9-dot
        // grid dropdown rebuilds itself on each open, so it picks up the new
        // state automatically). onCreateOptionsMenu is stubbed so we don't
        // call invalidateOptionsMenu here — would be a no-op.
        appBarMenuManager.reapplySuperAdminVisibility()
        // S251 — re-kick deferred one-shot fetches that may have silently
        // bailed in onStart() before the operator turned SuperAdmin on. The
        // refresh-loop fetches (trains/subway/buses) self-recover on the next
        // 30s tick, but stations + bus-stops are one-shots and would otherwise
        // stay empty for the whole session.
        if (nowOn) kickDeferredSuperAdminFetches()
        val msg = if (nowOn) {
            "SuperAdmin: ON — V1+ services unlocked (weather / MBTA / aircraft / radar / webcams). LAN cache-proxy required."
        } else {
            "SuperAdmin: OFF — back to V1 offline."
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        DebugLogger.i(TAG, "SuperAdmin toggled → $nowOn")
    }
}

/**
 * S251 — re-fire the one-shot fetches that respect their pref defaults but
 * bailed silently when called in onStart() before SuperAdmin was on. Logs
 * exactly what it did so the stream shows the recovery chain.
 */
internal fun SalemMainActivity.kickDeferredSuperAdminFetches() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val stationsPref = prefs.getBoolean(MenuPrefs.PREF_MBTA_STATIONS, true)
    val busStopsPref = prefs.getBoolean(MenuPrefs.PREF_MBTA_BUS_STOPS, false)
    DebugLogger.i(
        TAG,
        "kickDeferredSuperAdminFetches — stationsPref=$stationsPref " +
                "stationsCached=${stationMarkers.size} busStopsPref=$busStopsPref " +
                "busStopsCached=${allBusStops.size}"
    )
    if (stationsPref && stationMarkers.isEmpty()) {
        DebugLogger.i(TAG, "kicking deferred fetchMbtaStations()")
        transitViewModel.fetchMbtaStations()
    }
    if (busStopsPref && allBusStops.isEmpty()) {
        DebugLogger.i(TAG, "kicking deferred fetchMbtaBusStops()")
        transitViewModel.fetchMbtaBusStops()
    }
}

internal fun SalemMainActivity.refreshSuperAdminIconTint() {
    val icon = findViewById<View?>(R.id.toolbarSuperAdminIcon) ?: return
    val color = if (SuperAdminMode.isEnabled()) 0xFFE53935.toInt() else 0xFFFFFFFF.toInt()
    (icon as? android.widget.ImageView)?.setColorFilter(color)
}
