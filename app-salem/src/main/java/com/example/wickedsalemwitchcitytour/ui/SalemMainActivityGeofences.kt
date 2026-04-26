/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.locationmapapp.util.FeatureFlags
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.wickedsalemwitchcitytour.R
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityGeofences.kt"

// =========================================================================
// GEOFENCE OVERLAYS + ALERTS
// =========================================================================

internal fun SalemMainActivity.loadTfrsForVisibleArea() {
    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
    if (!prefs.getBoolean(MenuPrefs.PREF_TFR_OVERLAY, true)) return
    val bb = binding.mapView.boundingBox
    geofenceViewModel.loadTfrs(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
}

/** Load all enabled geofence zone types for the visible viewport. */
internal fun SalemMainActivity.loadGeofenceZonesForVisibleArea() {
    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
    val bb = binding.mapView.boundingBox
    val zoom = binding.mapView.zoomLevelDouble

    if (prefs.getBoolean(MenuPrefs.PREF_TFR_OVERLAY, true)) {
        geofenceViewModel.loadTfrs(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }
    if (prefs.getBoolean(MenuPrefs.PREF_CAMERA_OVERLAY, false) && zoom >= 10) {
        geofenceViewModel.loadCameras(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }
    if (prefs.getBoolean(MenuPrefs.PREF_SCHOOL_OVERLAY, false) && zoom >= 12) {
        geofenceViewModel.loadSchools(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }
    if (prefs.getBoolean(MenuPrefs.PREF_FLOOD_OVERLAY, false) && zoom >= 12) {
        geofenceViewModel.loadFloodZones(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }
    if (prefs.getBoolean(MenuPrefs.PREF_CROSSING_OVERLAY, false) && zoom >= 12) {
        geofenceViewModel.loadCrossings(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }
    // Load database zones (offline SQLite databases)
    if (geofenceViewModel.hasInstalledDatabases()) {
        geofenceViewModel.loadDatabaseZonesForVisibleArea(bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
    }
}

/** Debounced: reload geofence zones 500ms after scrolling/zooming stops. */
internal fun SalemMainActivity.scheduleGeofenceReload() {
    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
    val anyEnabled = prefs.getBoolean(MenuPrefs.PREF_TFR_OVERLAY, true)
        || prefs.getBoolean(MenuPrefs.PREF_CAMERA_OVERLAY, false)
        || prefs.getBoolean(MenuPrefs.PREF_SCHOOL_OVERLAY, false)
        || prefs.getBoolean(MenuPrefs.PREF_FLOOD_OVERLAY, false)
        || prefs.getBoolean(MenuPrefs.PREF_CROSSING_OVERLAY, false)
        || geofenceViewModel.hasInstalledDatabases()
    if (!anyEnabled) return
    geofenceReloadJob?.cancel()
    geofenceReloadJob = lifecycleScope.launch {
        delay(500)
        loadGeofenceZonesForVisibleArea()
    }
}

// ── TFR overlays ─────────────────────────────────────────────────────────

internal fun SalemMainActivity.renderTfrOverlays(zones: List<com.example.locationmapapp.data.model.TfrZone>) {
    clearOverlayList(tfrOverlays)
    renderZoneOverlays(zones, tfrOverlays, Color.argb(40, 244, 67, 54), Color.parseColor("#F44336"))
    DebugLogger.i("SalemMainActivity", "Rendered ${tfrOverlays.size} TFR overlays from ${zones.size} zones")
}

// ── Camera overlays ──────────────────────────────────────────────────────

internal fun SalemMainActivity.renderCameraOverlays(zones: List<com.example.locationmapapp.data.model.TfrZone>) {
    clearOverlayList(cameraOverlays)
    renderZoneOverlays(zones, cameraOverlays, Color.argb(50, 255, 152, 0), Color.parseColor("#FF9800"))
    DebugLogger.i("SalemMainActivity", "Rendered ${cameraOverlays.size} camera overlays from ${zones.size} zones")
}

// ── School overlays ──────────────────────────────────────────────────────

internal fun SalemMainActivity.renderSchoolOverlays(zones: List<com.example.locationmapapp.data.model.TfrZone>) {
    clearOverlayList(schoolOverlays)
    renderZoneOverlays(zones, schoolOverlays, Color.argb(40, 255, 193, 7), Color.parseColor("#FFC107"))
    DebugLogger.i("SalemMainActivity", "Rendered ${schoolOverlays.size} school overlays from ${zones.size} zones")
}

// ── Flood overlays ───────────────────────────────────────────────────────

internal fun SalemMainActivity.renderFloodOverlays(zones: List<com.example.locationmapapp.data.model.TfrZone>) {
    clearOverlayList(floodOverlays)
    for (zone in zones) {
        for (shape in zone.shapes) {
            if (shape.points.size < 3) continue
            val isHighRisk = zone.metadata["zoneCode"]?.let { it.startsWith("A") || it.startsWith("V") } == true
            val fillColor = if (isHighRisk) Color.argb(50, 33, 150, 243) else Color.argb(35, 33, 150, 243)
            val polygon = buildZonePolygon(zone, shape, fillColor, Color.parseColor("#2196F3"))
            val insertPos = minOf(1, binding.mapView.overlays.size)
            binding.mapView.overlays.add(insertPos, polygon)
            floodOverlays.add(polygon)
        }
    }
    binding.mapView.invalidate()
    DebugLogger.i("SalemMainActivity", "Rendered ${floodOverlays.size} flood overlays from ${zones.size} zones")
}

// ── Crossing overlays ────────────────────────────────────────────────────

internal fun SalemMainActivity.renderCrossingOverlays(zones: List<com.example.locationmapapp.data.model.TfrZone>) {
    clearOverlayList(crossingOverlays)
    renderZoneOverlays(zones, crossingOverlays, Color.argb(50, 33, 33, 33), Color.parseColor("#FFC107"))
    DebugLogger.i("SalemMainActivity", "Rendered ${crossingOverlays.size} crossing overlays from ${zones.size} zones")
}

// ── Database overlays ──────────────────────────────────────────────────

internal fun SalemMainActivity.renderDatabaseOverlays(zones: List<com.example.locationmapapp.data.model.TfrZone>) {
    clearOverlayList(databaseOverlays)
    for (zone in zones) {
        for (shape in zone.shapes) {
            if (shape.points.size < 3) continue
            val (fillColor, outlineColor) = when (zone.zoneType) {
                com.example.locationmapapp.data.model.ZoneType.MILITARY_BASE ->
                    Color.argb(40, 76, 175, 80) to Color.parseColor("#4CAF50")
                com.example.locationmapapp.data.model.ZoneType.NO_FLY_ZONE ->
                    Color.argb(40, 156, 39, 176) to Color.parseColor("#9C27B0")
                else ->
                    Color.argb(35, 158, 158, 158) to Color.parseColor("#9E9E9E")
            }
            val polygon = buildZonePolygon(zone, shape, fillColor, outlineColor)
            val insertPos = minOf(1, binding.mapView.overlays.size)
            binding.mapView.overlays.add(insertPos, polygon)
            databaseOverlays.add(polygon)
        }
    }
    binding.mapView.invalidate()
    DebugLogger.i("SalemMainActivity", "Rendered ${databaseOverlays.size} database overlays from ${zones.size} zones")
}

// ── Shared overlay helpers ───────────────────────────────────────────────

internal fun SalemMainActivity.renderZoneOverlays(
    zones: List<com.example.locationmapapp.data.model.TfrZone>,
    overlayList: MutableList<org.osmdroid.views.overlay.Polygon>,
    fillColor: Int,
    outlineColor: Int
) {
    for (zone in zones) {
        for (shape in zone.shapes) {
            if (shape.points.size < 3) continue
            val polygon = buildZonePolygon(zone, shape, fillColor, outlineColor)
            val insertPos = minOf(1, binding.mapView.overlays.size)
            binding.mapView.overlays.add(insertPos, polygon)
            overlayList.add(polygon)
        }
    }
    binding.mapView.invalidate()
}

internal fun SalemMainActivity.buildZonePolygon(
    zone: com.example.locationmapapp.data.model.TfrZone,
    shape: com.example.locationmapapp.data.model.TfrShape,
    fillColor: Int,
    outlineColor: Int
): org.osmdroid.views.overlay.Polygon {
    return org.osmdroid.views.overlay.Polygon(binding.mapView).apply {
        fillPaint.color = fillColor
        outlinePaint.color = outlineColor
        outlinePaint.strokeWidth = 3f
        title = zone.notam
        snippet = "${zone.type} — ${zone.description}".take(200)
        relatedObject = zone
        val geoPoints = shape.points.map { GeoPoint(it[1], it[0]) }
        points = geoPoints
        setOnClickListener { _, _, _ ->
            showZoneDetailDialog(zone)
            true
        }
    }
}

internal fun SalemMainActivity.clearOverlayList(list: MutableList<org.osmdroid.views.overlay.Polygon>) {
    for (overlay in list) {
        binding.mapView.overlays.remove(overlay)
    }
    list.clear()
    binding.mapView.invalidate()
}

internal fun SalemMainActivity.clearTfrOverlays() = clearOverlayList(tfrOverlays)
internal fun SalemMainActivity.clearCameraOverlays() = clearOverlayList(cameraOverlays)
internal fun SalemMainActivity.clearSchoolOverlays() = clearOverlayList(schoolOverlays)
internal fun SalemMainActivity.clearFloodOverlays() = clearOverlayList(floodOverlays)
internal fun SalemMainActivity.clearCrossingOverlays() = clearOverlayList(crossingOverlays)
internal fun SalemMainActivity.clearDatabaseOverlays() = clearOverlayList(databaseOverlays)

internal fun SalemMainActivity.clearAllGeofenceOverlays() {
    clearTfrOverlays(); clearCameraOverlays(); clearSchoolOverlays()
    clearFloodOverlays(); clearCrossingOverlays(); clearDatabaseOverlays()
}

// ── Database Manager Dialog ────────────────────────────────────────────

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showDatabaseManagerDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }

    // Title
    root.addView(TextView(this).apply {
        text = "Zone Databases"
        textSize = 20f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    })

    // Import button row
    root.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, dp(12))
        addView(TextView(this@showDatabaseManagerDialog).apply {
            text = "IMPORT .DB"
            textSize = 13f
            setTextColor(Color.parseColor("#64B5F6"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), dp(20), dp(4))
            setOnClickListener {
                dbImportLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3"))
            }
        })
        addView(TextView(this@showDatabaseManagerDialog).apply {
            text = "IMPORT CSV"
            textSize = 13f
            setTextColor(Color.parseColor("#64B5F6"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener {
                csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
            }
        })
    })

    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    val contentLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    // Loading indicator
    val loadingText = TextView(this).apply {
        text = "Loading catalog…"
        textSize = 14f
        setTextColor(Color.parseColor("#AAAAAA"))
        setPadding(0, dp(8), 0, dp(8))
    }
    contentLayout.addView(loadingText)

    scrollView.addView(contentLayout)
    root.addView(scrollView)

    val dialog = android.app.AlertDialog.Builder(this)
        .setView(root)
        .create()

    dialog.window?.apply {
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        val wlp = attributes
        wlp.width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        wlp.height = (resources.displayMetrics.heightPixels * 0.85).toInt()
        attributes = wlp
    }

    // Observe catalog
    val observer = object : androidx.lifecycle.Observer<List<com.example.locationmapapp.data.model.GeofenceDatabaseInfo>> {
        override fun onChanged(catalog: List<com.example.locationmapapp.data.model.GeofenceDatabaseInfo>) {
            contentLayout.removeAllViews()
            val installed = catalog.filter { it.installed }
            val available = catalog.filter { !it.installed }

            if (installed.isNotEmpty()) {
                contentLayout.addView(TextView(this@showDatabaseManagerDialog).apply {
                    text = "INSTALLED"
                    textSize = 13f
                    setTextColor(Color.parseColor("#4CAF50"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(4), 0, dp(8))
                })
                for (db in installed) {
                    contentLayout.addView(buildDatabaseCard(db, dialog))
                }
            }

            if (available.isNotEmpty()) {
                contentLayout.addView(TextView(this@showDatabaseManagerDialog).apply {
                    text = "AVAILABLE"
                    textSize = 13f
                    setTextColor(Color.parseColor("#64B5F6"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(12), 0, dp(8))
                })
                for (db in available) {
                    contentLayout.addView(buildDatabaseCard(db, dialog))
                }
            }

            if (catalog.isEmpty()) {
                contentLayout.addView(TextView(this@showDatabaseManagerDialog).apply {
                    text = "No databases available. Check proxy connection."
                    textSize = 14f
                    setTextColor(Color.parseColor("#AAAAAA"))
                    setPadding(0, dp(8), 0, dp(8))
                })
            }
        }
    }
    geofenceViewModel.geofenceCatalog.observe(this, observer)

    // Observe import results
    val importObserver = androidx.lifecycle.Observer<Pair<Boolean, String>?> { result ->
        if (result == null) return@Observer
        geofenceViewModel.clearImportResult()
        val (success, message) = result
        when {
            success -> toast(message)
            message.startsWith("DUPLICATE:") -> {
                val parts = message.split(":", limit = 3)
                val dupId = parts.getOrElse(1) { "" }
                val dupName = parts.getOrElse(2) { dupId }
                showOverwriteConfirmationDialog(dupId, dupName)
            }
            else -> toast("Import failed: $message")
        }
    }
    geofenceViewModel.importResult.observe(this, importObserver)

    dialog.setOnDismissListener {
        geofenceViewModel.geofenceCatalog.removeObserver(observer)
        geofenceViewModel.importResult.removeObserver(importObserver)
    }

    // Fetch catalog
    geofenceViewModel.fetchGeofenceCatalog()

    dialog.show()
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.buildDatabaseCard(
    db: com.example.locationmapapp.data.model.GeofenceDatabaseInfo,
    parentDialog: android.app.AlertDialog
): LinearLayout {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#252525"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }

        // Name
        addView(TextView(this@buildDatabaseCard).apply {
            text = db.name
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // Description
        addView(TextView(this@buildDatabaseCard).apply {
            text = db.description
            textSize = 12f
            setTextColor(Color.parseColor("#BBBBBB"))
            setPadding(0, dp(2), 0, dp(4))
        })

        // Stats row
        val sizeStr = if (db.fileSize > 1_048_576) "${db.fileSize / 1_048_576} MB"
                     else "${db.fileSize / 1024} KB"
        addView(TextView(this@buildDatabaseCard).apply {
            text = "${db.zoneCount} zones · $sizeStr · v${db.version} · ${db.source}"
            textSize = 11f
            setTextColor(Color.parseColor("#999999"))
        })

        // Action buttons
        val btnRow = LinearLayout(this@buildDatabaseCard).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        if (db.installed) {
            // Update button (if newer version available)
            if (db.version > db.installedVersion) {
                btnRow.addView(TextView(this@buildDatabaseCard).apply {
                    text = "UPDATE"
                    textSize = 13f
                    setTextColor(Color.parseColor("#64B5F6"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(4), dp(16), dp(4))
                    setOnClickListener {
                        geofenceViewModel.downloadGeofenceDatabase(db.id)
                        toast("Updating ${db.name}…")
                    }
                })
            } else {
                btnRow.addView(TextView(this@buildDatabaseCard).apply {
                    text = "UP TO DATE"
                    textSize = 13f
                    setTextColor(Color.parseColor("#4CAF50"))
                    setPadding(0, dp(4), dp(16), dp(4))
                })
            }

            // Export button
            btnRow.addView(TextView(this@buildDatabaseCard).apply {
                text = "EXPORT"
                textSize = 13f
                setTextColor(Color.parseColor("#4CAF50"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), dp(16), dp(4))
                setOnClickListener { exportGeofenceDatabase(db.id, db.name) }
            })

            // Delete button
            btnRow.addView(TextView(this@buildDatabaseCard).apply {
                text = "DELETE"
                textSize = 13f
                setTextColor(Color.parseColor("#FF5252"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    geofenceViewModel.deleteGeofenceDatabase(db.id)
                    clearDatabaseOverlays()
                    toast("Deleted ${db.name}")
                }
            })
        } else {
            // Download button
            val downloadBtn = TextView(this@buildDatabaseCard).apply {
                text = "DOWNLOAD"
                textSize = 13f
                setTextColor(Color.parseColor("#64B5F6"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    text = "DOWNLOADING…"
                    setTextColor(Color.parseColor("#999999"))
                    isEnabled = false
                    geofenceViewModel.downloadGeofenceDatabase(db.id)
                    toast("Downloading ${db.name}…")
                }
            }
            btnRow.addView(downloadBtn)

            // Observe download progress for this db
            geofenceViewModel.databaseDownloadProgress.observe(this@buildDatabaseCard) { progress ->
                if (progress != null && progress.first == db.id) {
                    downloadBtn.text = "DOWNLOADING ${progress.second}%"
                }
            }
        }

        addView(btnRow)
    }
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showCsvImportConfigDialog(uri: Uri) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        setPadding(dp(20), dp(16), dp(20), dp(16))
    }

    root.addView(TextView(this).apply {
        text = "Import CSV"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(16))
    })

    // Database Name
    root.addView(TextView(this).apply {
        text = "Database Name"
        textSize = 13f
        setTextColor(Color.parseColor("#AAAAAA"))
        setPadding(0, 0, 0, dp(4))
    })
    val nameInput = android.widget.EditText(this).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#666666"))
        hint = "My Custom Zones"
        textSize = 15f
        setBackgroundColor(Color.parseColor("#333333"))
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    root.addView(nameInput)

    // Zone Type spinner
    root.addView(TextView(this).apply {
        text = "Zone Type"
        textSize = 13f
        setTextColor(Color.parseColor("#AAAAAA"))
        setPadding(0, dp(12), 0, dp(4))
    })
    val zoneTypes = com.example.locationmapapp.data.model.ZoneType.values()
    val typeSpinner = android.widget.Spinner(this).apply {
        setBackgroundColor(Color.parseColor("#333333"))
        adapter = android.widget.ArrayAdapter(
            this@showCsvImportConfigDialog,
            android.R.layout.simple_spinner_dropdown_item,
            zoneTypes.map { it.name }
        )
        setSelection(zoneTypes.indexOf(com.example.locationmapapp.data.model.ZoneType.CUSTOM))
    }
    root.addView(typeSpinner)

    // Default Radius
    root.addView(TextView(this).apply {
        text = "Default Radius (meters)"
        textSize = 13f
        setTextColor(Color.parseColor("#AAAAAA"))
        setPadding(0, dp(12), 0, dp(4))
    })
    val radiusInput = android.widget.EditText(this).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#666666"))
        hint = "500"
        setText("500")
        textSize = 15f
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setBackgroundColor(Color.parseColor("#333333"))
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    root.addView(radiusInput)

    // Import button (click listener set after dialog creation for dismiss access)
    val importBtn = TextView(this).apply {
        text = "IMPORT"
        textSize = 15f
        setTextColor(Color.parseColor("#64B5F6"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(4))
        gravity = android.view.Gravity.CENTER
    }
    root.addView(importBtn)

    val dialog = android.app.AlertDialog.Builder(this).setView(root).create()
    dialog.window?.apply {
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        val wlp = attributes
        wlp.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        attributes = wlp
    }
    importBtn.setOnClickListener {
        val dbName = nameInput.text.toString().trim()
        if (dbName.isBlank()) {
            toast("Enter a database name")
            return@setOnClickListener
        }
        val dbId = dbName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val selectedType = zoneTypes[typeSpinner.selectedItemPosition]
        val radius = radiusInput.text.toString().toDoubleOrNull() ?: 500.0
        geofenceViewModel.importCsvAsGeofenceDatabase(contentResolver, uri, dbId, dbName, selectedType, radius)
        dialog.dismiss()
        toast("Importing CSV…")
    }
    dialog.show()
}

internal fun SalemMainActivity.showOverwriteConfirmationDialog(id: String, name: String) {
    android.app.AlertDialog.Builder(this)
        .setTitle("Database Exists")
        .setMessage("\"$name\" ($id) is already installed. Overwrite?")
        .setPositiveButton("Overwrite") { _, _ ->
            val uri = pendingImportUri
            if (uri != null) {
                geofenceViewModel.importGeofenceDatabase(contentResolver, uri, overwriteId = id)
            } else {
                toast("Import URI lost — try again")
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun SalemMainActivity.exportGeofenceDatabase(dbId: String, dbName: String) {
    val file = geofenceViewModel.getGeofenceDatabaseFile(dbId)
    if (file == null) {
        toast("Database file not found")
        return
    }
    try {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-sqlite3"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$dbName.db")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export $dbName"))
    } catch (e: Exception) {
        DebugLogger.e("SalemMainActivity", "Export failed: ${e.message}", e)
        toast("Export failed: ${e.message}")
    }
}

/** Show zone detail dialog — adapts content by zone type. */
@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showZoneDetailDialog(zone: com.example.locationmapapp.data.model.TfrZone) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }

    // Color bar — zone-type-aware
    val barColor = when (zone.zoneType) {
        com.example.locationmapapp.data.model.ZoneType.TFR -> "#D32F2F"
        com.example.locationmapapp.data.model.ZoneType.SPEED_CAMERA -> "#FF9800"
        com.example.locationmapapp.data.model.ZoneType.SCHOOL_ZONE -> "#FFC107"
        com.example.locationmapapp.data.model.ZoneType.FLOOD_ZONE -> "#2196F3"
        com.example.locationmapapp.data.model.ZoneType.RAILROAD_CROSSING -> "#FFC107"
        com.example.locationmapapp.data.model.ZoneType.MILITARY_BASE -> "#4CAF50"
        com.example.locationmapapp.data.model.ZoneType.NO_FLY_ZONE -> "#9C27B0"
        com.example.locationmapapp.data.model.ZoneType.CUSTOM -> "#9E9E9E"
    }
    root.addView(View(this).apply {
        setBackgroundColor(Color.parseColor(barColor))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
            bottomMargin = dp(12)
        }
    })

    // Header
    root.addView(TextView(this).apply {
        text = zone.notam
        textSize = 20f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
    })

    // Type
    val typeColor = when (zone.zoneType) {
        com.example.locationmapapp.data.model.ZoneType.TFR -> "#FF5252"
        com.example.locationmapapp.data.model.ZoneType.SPEED_CAMERA -> "#FFB74D"
        com.example.locationmapapp.data.model.ZoneType.SCHOOL_ZONE -> "#FFD54F"
        com.example.locationmapapp.data.model.ZoneType.FLOOD_ZONE -> "#64B5F6"
        com.example.locationmapapp.data.model.ZoneType.RAILROAD_CROSSING -> "#FFD54F"
        com.example.locationmapapp.data.model.ZoneType.MILITARY_BASE -> "#66BB6A"
        com.example.locationmapapp.data.model.ZoneType.NO_FLY_ZONE -> "#BA68C8"
        com.example.locationmapapp.data.model.ZoneType.CUSTOM -> "#BDBDBD"
    }
    if (zone.type.isNotBlank()) {
        root.addView(TextView(this).apply {
            text = zone.type
            textSize = 14f
            setTextColor(Color.parseColor(typeColor))
            setPadding(0, dp(4), 0, 0)
        })
    }

    // Description
    if (zone.description.isNotBlank()) {
        root.addView(TextView(this).apply {
            text = zone.description
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, dp(8), 0, 0)
        })
    }

    // Altitude ranges (TFR only)
    if (zone.zoneType == com.example.locationmapapp.data.model.ZoneType.TFR) {
        val altText = zone.shapes.joinToString("\n") { s ->
            "  ${s.floorAltFt} ft — ${s.ceilingAltFt} ft (${s.type})"
        }
        if (altText.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = "Altitude:"
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(12), 0, dp(2))
            })
            root.addView(TextView(this).apply {
                text = altText
                textSize = 12f
                setTextColor(Color.parseColor("#BBBBBB"))
            })
        }
    }

    // Info rows
    fun addInfoRow(label: String, value: String) {
        if (value.isBlank()) return
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0) }
        row.addView(TextView(this).apply {
            text = "$label: "
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(Color.WHITE)
        })
        root.addView(row)
    }

    // TFR-specific fields
    if (zone.zoneType == com.example.locationmapapp.data.model.ZoneType.TFR) {
        addInfoRow("Effective", zone.effectiveDate)
        addInfoRow("Expires", zone.expireDate)
        addInfoRow("Facility", zone.facility)
        addInfoRow("State", zone.state)
    }

    // Metadata fields for all zone types
    for ((key, value) in zone.metadata) {
        addInfoRow(key.replaceFirstChar { it.uppercase() }, value)
    }

    val dialog = android.app.Dialog(this)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    dialog.setContentView(root)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    dialog.show()
}

/** Backward-compatible wrapper. */
internal fun SalemMainActivity.showTfrDetailDialog(zone: com.example.locationmapapp.data.model.TfrZone) = showZoneDetailDialog(zone)

/** Update alerts icon color based on alert severity. */
internal fun SalemMainActivity.updateAlertsIcon(alerts: List<com.example.locationmapapp.data.model.GeofenceAlert>) {
    val iv = alertsIconView ?: return

    // Stop any running pulse animation
    alertPulseAnimation?.cancel()
    iv.clearAnimation()

    if (alerts.isEmpty()) {
        iv.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        return
    }

    val maxSeverity = alerts.maxByOrNull { it.severity.level }?.severity
        ?: com.example.locationmapapp.data.model.AlertSeverity.INFO

    val color = when (maxSeverity) {
        com.example.locationmapapp.data.model.AlertSeverity.INFO -> Color.parseColor("#2196F3")       // Blue
        com.example.locationmapapp.data.model.AlertSeverity.WARNING -> Color.parseColor("#FFC107")    // Yellow
        com.example.locationmapapp.data.model.AlertSeverity.CRITICAL -> Color.parseColor("#F44336")   // Red
        com.example.locationmapapp.data.model.AlertSeverity.EMERGENCY -> Color.parseColor("#F44336")  // Red + pulse
    }

    iv.imageTintList = android.content.res.ColorStateList.valueOf(color)

    // Pulsing animation for EMERGENCY
    if (maxSeverity == com.example.locationmapapp.data.model.AlertSeverity.EMERGENCY) {
        val pulse = android.view.animation.AlphaAnimation(1.0f, 0.3f).apply {
            duration = 500
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
        }
        alertPulseAnimation = pulse
        iv.startAnimation(pulse)
    }
}

/** Show a status line entry for critical geofence alerts. Color varies by zone type. */
@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showGeofenceAlertBanner(alert: com.example.locationmapapp.data.model.GeofenceAlert) {
    val alertLabel = when (alert.zoneType) {
        com.example.locationmapapp.data.model.ZoneType.TFR -> "TFR:"
        com.example.locationmapapp.data.model.ZoneType.SPEED_CAMERA -> "Camera:"
        com.example.locationmapapp.data.model.ZoneType.SCHOOL_ZONE -> "School:"
        com.example.locationmapapp.data.model.ZoneType.FLOOD_ZONE -> "Flood:"
        com.example.locationmapapp.data.model.ZoneType.RAILROAD_CROSSING -> "RR Crossing:"
        com.example.locationmapapp.data.model.ZoneType.MILITARY_BASE -> "Military:"
        com.example.locationmapapp.data.model.ZoneType.NO_FLY_ZONE -> "No-Fly:"
        com.example.locationmapapp.data.model.ZoneType.CUSTOM -> "Zone:"
    }
    val bgColor = when (alert.zoneType) {
        com.example.locationmapapp.data.model.ZoneType.TFR -> Color.parseColor("#DDD32F2F")
        com.example.locationmapapp.data.model.ZoneType.SPEED_CAMERA -> Color.parseColor("#DDE65100")
        com.example.locationmapapp.data.model.ZoneType.SCHOOL_ZONE -> Color.parseColor("#DDF57F17")
        com.example.locationmapapp.data.model.ZoneType.FLOOD_ZONE -> Color.parseColor("#DD1565C0")
        com.example.locationmapapp.data.model.ZoneType.RAILROAD_CROSSING -> Color.parseColor("#DD424242")
        com.example.locationmapapp.data.model.ZoneType.MILITARY_BASE -> Color.parseColor("#DD2E7D32")
        com.example.locationmapapp.data.model.ZoneType.NO_FLY_ZONE -> Color.parseColor("#DD7B1FA2")
        com.example.locationmapapp.data.model.ZoneType.CUSTOM -> Color.parseColor("#DD616161")
    }
    val dist = alert.distanceNm
    val distText = if (dist != null && dist > 0) " %.1fNM".format(dist) else ""
    val text = "\u26A0 $alertLabel ${alert.zoneName}$distText — ${alert.description.take(60)}"

    statusLineManager.set(StatusLineManager.Priority.GEOFENCE_ALERT, text, bgColor) {
        statusLineManager.clear(StatusLineManager.Priority.GEOFENCE_ALERT)
        val zone = findZoneById(alert.zoneId)
        if (zone != null) showZoneDetailDialog(zone)
    }
    DebugLogger.i("SalemMainActivity", "Geofence alert status: ${alert.alertType} ${alert.zoneName}")
}

/** Find a zone by ID across all zone type lists. */
internal fun SalemMainActivity.findZoneById(zoneId: String): com.example.locationmapapp.data.model.TfrZone? {
    return geofenceViewModel.tfrZones.value?.find { it.id == zoneId }
        ?: geofenceViewModel.cameraZones.value?.find { it.id == zoneId }
        ?: geofenceViewModel.schoolZones.value?.find { it.id == zoneId }
        ?: geofenceViewModel.floodZones.value?.find { it.id == zoneId }
        ?: geofenceViewModel.crossingZones.value?.find { it.id == zoneId }
}

@android.annotation.SuppressLint("SetJavaScriptEnabled")
internal fun SalemMainActivity.showWebcamPreviewDialog(webcam: com.example.locationmapapp.data.model.Webcam) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header: title + X button ──
    val titleText = TextView(this).apply {
        text = webcam.title
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "X"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(4), dp(4), dp(4))
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        addView(titleText)
        addView(closeBtn)
    }

    // ── Preview image ──
    val imageView = ImageView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(220)
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.parseColor("#222222"))
    }

    // ── Info text ──
    val catText = webcam.categories.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
    val infoText = TextView(this).apply {
        text = buildString {
            append(catText)
            webcam.lastUpdated?.let { append("\nLast updated: $it") }
            append("\nStatus: ${webcam.status}")
        }
        setPadding(dp(16), dp(8), dp(16), dp(8))
        textSize = 13f
        setTextColor(Color.parseColor("#CCCCCC"))
    }

    // ── View Live button ──
    // S180: V1 zero-network — webcam playerUrl is a remote video stream.
    // In V1, the View Live button is never created.
    val liveBtn = if (!FeatureFlags.V1_OFFLINE_ONLY && webcam.playerUrl.isNotBlank()) {
        android.widget.Button(this, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "View Live"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) }
            setOnClickListener {
                // Replace image + info + button with WebView
                val parent = this.parent as LinearLayout
                val idx = parent.indexOfChild(imageView)
                parent.removeView(imageView)
                parent.removeView(infoText)
                parent.removeView(this)
                val webView = android.webkit.WebView(this@showWebcamPreviewDialog).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(Color.BLACK)
                    loadUrl(webcam.playerUrl)
                }
                parent.addView(webView, idx)
                dialog.setOnDismissListener { webView.destroy() }
            }
        }
    } else null

    // ── Container ──
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        addView(header)
        addView(imageView)
        addView(infoText)
        liveBtn?.let { addView(it) }
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.9).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
    }
    dialog.show()

    // Load preview image async
    if (webcam.previewUrl.isNotBlank()) {
        lifecycleScope.launch {
            try {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .addInterceptor(com.example.locationmapapp.util.network.OfflineModeInterceptor())
                        .build()
                    val request = okhttp3.Request.Builder().url(webcam.previewUrl).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.let { android.graphics.BitmapFactory.decodeStream(it) }
                    } else null
                }
                if (bitmap != null && dialog.isShowing) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                DebugLogger.e("SalemMainActivity", "Webcam image load failed: ${e.message}")
            }
        }
    }
}

