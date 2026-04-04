/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.Color
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.overlay.TilesOverlay

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityRadar.kt"

// =========================================================================
// RADAR
// =========================================================================

fun SalemMainActivity.toggleRadar() {
    DebugLogger.i("SalemMainActivity", "toggleRadar() — currently ${if (radarTileOverlay != null) "ON" else "OFF"}")
    if (radarAnimating) stopRadarAnimation()
    if (radarTileOverlay != null) {
        binding.mapView.overlays.remove(radarTileOverlay); radarTileOverlay = null
        binding.mapView.invalidate()
        DebugLogger.i("SalemMainActivity", "Radar overlay removed")
    } else {
        addRadarOverlay()
    }
}

internal fun SalemMainActivity.addRadarOverlay() {
    try {
        // NWS NEXRAD composite radar via Iowa State Mesonet (no API key, no timestamp fetch)
        val src = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "NWS-NEXRAD", 0, 12, 256, ".png",
            arrayOf("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/")
        )
        val tp = org.osmdroid.tileprovider.MapTileProviderBasic(applicationContext)
        tp.tileSource = src
        radarTileOverlay = TilesOverlay(tp, applicationContext).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor       = Color.TRANSPARENT
            val alpha = radarAlphaPercent / 100f
            val matrix = android.graphics.ColorMatrix().apply { setScale(1f, 1f, 1f, alpha) }
            setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
        }
        binding.mapView.overlays.add(radarTileOverlay)
        binding.mapView.invalidate()
        DebugLogger.i("SalemMainActivity", "Radar overlay added (NWS NEXRAD)")
    } catch (e: Exception) {
        DebugLogger.e("SalemMainActivity", "Radar overlay FAILED: ${e.message}")
        toast("Radar overlay failed: ${e.message}")
    }
}

internal fun SalemMainActivity.buildDarkTileSource(): org.osmdroid.tileprovider.tilesource.XYTileSource {
    return org.osmdroid.tileprovider.tilesource.XYTileSource(
        "CartoDB-DarkMatter", 0, 19, 256, ".png",
        arrayOf(
            "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-b.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-c.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-d.global.ssl.fastly.net/dark_all/"
        )
    )
}

internal fun SalemMainActivity.refreshRadarOverlay() {
    if (radarTileOverlay != null) {
        binding.mapView.overlays.remove(radarTileOverlay); radarTileOverlay = null
        addRadarOverlay()
        DebugLogger.i("SalemMainActivity", "Radar tiles refreshed")
    }
}

internal fun SalemMainActivity.generateRadarTimestamps(count: Int): List<String> {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    // Round down to nearest 5 minutes
    val min = cal.get(java.util.Calendar.MINUTE)
    cal.set(java.util.Calendar.MINUTE, (min / 5) * 5)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)

    val timestamps = mutableListOf<String>()
    val fmt = java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    for (i in count - 1 downTo 0) {
        val t = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        t.timeInMillis = cal.timeInMillis - (i * 5 * 60 * 1000L)
        timestamps.add(fmt.format(t.time))
    }
    return timestamps
}

internal fun SalemMainActivity.startRadarAnimation() {
    if (radarAnimating) return
    DebugLogger.i("SalemMainActivity", "startRadarAnimation()")

    // Remove static radar overlay if present
    radarTileOverlay?.let { binding.mapView.overlays.remove(it) }
    radarTileOverlay = null
    radarScheduler.stop()

    // Read speed from prefs
    val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    radarAnimSpeedMs = prefs.getInt(MenuPrefs.PREF_RADAR_ANIM_SPEED, MenuPrefs.DEFAULT_RADAR_ANIM_SPEED)

    // Generate 7 timestamps (35 min of history)
    val timestamps = generateRadarTimestamps(7)
    DebugLogger.i("SalemMainActivity", "Radar animation: ${timestamps.size} frames, ${timestamps.first()} → ${timestamps.last()}")

    // Create overlay for each timestamp
    radarAnimFrames.clear()
    for (ts in timestamps) {
        try {
            val src = org.osmdroid.tileprovider.tilesource.XYTileSource(
                "NEXRAD-$ts", 0, 12, 256, ".png",
                arrayOf("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-$ts-900913/")
            )
            val tp = org.osmdroid.tileprovider.MapTileProviderBasic(applicationContext)
            tp.tileSource = src
            val overlay = TilesOverlay(tp, applicationContext).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor       = Color.TRANSPARENT
                val alpha = radarAlphaPercent / 100f
                val matrix = android.graphics.ColorMatrix().apply { setScale(1f, 1f, 1f, alpha) }
                setColorFilter(android.graphics.ColorMatrixColorFilter(matrix))
                isEnabled = false  // hidden initially
            }
            radarAnimFrames.add(overlay)
            binding.mapView.overlays.add(overlay)
        } catch (e: Exception) {
            DebugLogger.e("SalemMainActivity", "Radar anim frame error: ${e.message}")
        }
    }

    if (radarAnimFrames.isEmpty()) {
        DebugLogger.e("SalemMainActivity", "No radar animation frames created")
        toast("Radar animation failed")
        return
    }

    // Start animation loop
    radarAnimating = true
    radarAnimIndex = 0
    radarAnimHandler = android.os.Handler(android.os.Looper.getMainLooper())
    radarAnimRunnable = object : Runnable {
        override fun run() {
            if (!radarAnimating) return
            // Hide all frames
            for (frame in radarAnimFrames) frame.isEnabled = false
            // Show current frame
            radarAnimFrames[radarAnimIndex].isEnabled = true
            binding.mapView.invalidate()
            radarAnimIndex = (radarAnimIndex + 1) % radarAnimFrames.size
            radarAnimHandler?.postDelayed(this, radarAnimSpeedMs.toLong())
        }
    }
    radarAnimHandler?.post(radarAnimRunnable!!)

    // Show time range on status line
    val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    timeFmt.timeZone = java.util.TimeZone.getDefault()
    val parseFmt = java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.US)
    parseFmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val startLocal = timeFmt.format(parseFmt.parse(timestamps.first())!!)
    val endLocal = timeFmt.format(parseFmt.parse(timestamps.last())!!)
    statusLineManager.set(StatusLineManager.Priority.SILENT_FILL,
        "\u25B6 Radar $startLocal–$endLocal (${radarAnimSpeedMs}ms)") { stopRadarAnimation() }

    DebugLogger.i("SalemMainActivity", "Radar animation started: ${radarAnimFrames.size} frames @ ${radarAnimSpeedMs}ms")
}

internal fun SalemMainActivity.stopRadarAnimation() {
    if (!radarAnimating) return
    DebugLogger.i("SalemMainActivity", "stopRadarAnimation()")
    radarAnimating = false
    radarAnimRunnable?.let { radarAnimHandler?.removeCallbacks(it) }
    radarAnimRunnable = null
    radarAnimHandler = null

    // Remove all frame overlays
    for (frame in radarAnimFrames) {
        binding.mapView.overlays.remove(frame)
    }
    radarAnimFrames.clear()
    radarAnimIndex = 0

    // Clear status line
    statusLineManager.clear(StatusLineManager.Priority.SILENT_FILL)

    // Restore static radar if it was enabled
    val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(MenuPrefs.PREF_RADAR_ANIMATE, false).apply()
    if (prefs.getBoolean(MenuPrefs.PREF_RADAR_ON, true)) {
        addRadarOverlay()
        radarScheduler.start(appBarMenuManager.radarUpdateMinutes) { weatherViewModel.refreshRadar() }
    }

    binding.mapView.invalidate()
    DebugLogger.i("SalemMainActivity", "Radar animation stopped, static overlay restored")
}

