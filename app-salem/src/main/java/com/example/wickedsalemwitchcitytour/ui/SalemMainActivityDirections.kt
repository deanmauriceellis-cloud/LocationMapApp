/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.tour.WalkingRoute
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SalemMainActivityDirections.kt"

private const val DIR_ROUTE_COLOR = "#C9A84C"    // Salem gold
private const val DIR_ROUTE_BORDER = "#8A7234"   // Darker gold
private const val DIR_COMPLETED_COLOR = "#66C9A84C" // Faded gold

// Walking directions overlays
internal var directionsPolyline: Polyline? = null
internal var directionsBorderPolyline: Polyline? = null
internal val directionsTurnMarkers = mutableListOf<Marker>()
internal var directionsInfoView: View? = null

// ═════════════════════════════════════════════════════════════════════════════
// WALKING ROUTE OBSERVER
// ═════════════════════════════════════════════════════════════════════════════

internal fun SalemMainActivity.observeWalkingRoute() {
    lifecycleScope.launch {
        tourViewModel.walkingRoute.collectLatest { route ->
            if (route != null) {
                drawWalkingRoute(route)
                showDirectionsInfo(route)
            } else {
                clearDirectionsOverlays()
                removeDirectionsInfo()
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// DRAW WALKING ROUTE ON MAP
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.drawWalkingRoute(route: WalkingRoute) {
    clearDirectionsOverlays()

    if (route.polyline.isEmpty()) return

    // Border polyline (wider, darker)
    val border = Polyline().apply {
        setPoints(route.polyline)
        outlinePaint.color = Color.parseColor(DIR_ROUTE_BORDER)
        outlinePaint.strokeWidth = 10f
        outlinePaint.isAntiAlias = true
        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
    }
    binding.mapView.overlays.add(border)
    directionsBorderPolyline = border

    // Main route polyline
    val routeLine = Polyline().apply {
        setPoints(route.polyline)
        outlinePaint.color = Color.parseColor(DIR_ROUTE_COLOR)
        outlinePaint.strokeWidth = 6f
        outlinePaint.isAntiAlias = true
        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
    }
    binding.mapView.overlays.add(routeLine)
    directionsPolyline = routeLine

    // Turn markers at key instruction nodes
    for (instruction in route.instructions) {
        if (instruction.text.isBlank()) continue
        // Only show significant turns (skip "continue" instructions)
        if (instruction.text.lowercase().contains("continue")) continue

        val marker = Marker(binding.mapView).apply {
            position = instruction.location
            title = instruction.text
            snippet = "%.0f m".format(instruction.distanceM)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = MarkerIconHelper.dot(this@drawWalkingRoute, Color.parseColor(DIR_ROUTE_COLOR))
        }
        binding.mapView.overlays.add(marker)
        directionsTurnMarkers.add(marker)
    }

    binding.mapView.invalidate()
    DebugLogger.i("Directions", "Drew route: %.1fkm, %dmin, ${route.instructions.size} turns".format(
        route.distanceKm, route.durationMinutes
    ))
}

internal fun SalemMainActivity.clearDirectionsOverlays() {
    directionsPolyline?.let { binding.mapView.overlays.remove(it) }
    directionsPolyline = null
    directionsBorderPolyline?.let { binding.mapView.overlays.remove(it) }
    directionsBorderPolyline = null
    for (m in directionsTurnMarkers) binding.mapView.overlays.remove(m)
    directionsTurnMarkers.clear()
    binding.mapView.invalidate()
}

// ═════════════════════════════════════════════════════════════════════════════
// DIRECTIONS INFO BAR — distance, time, dismiss
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showDirectionsInfo(route: WalkingRoute) {
    removeDirectionsInfo()

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#E82D1B4E"))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Route info
    val infoLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    infoLayout.addView(TextView(this).apply {
        text = "Walking: %.1f km \u2022 ~%d min".format(route.distanceKm, route.durationMinutes)
        textSize = 13f
        setTextColor(Color.parseColor("#C9A84C"))
        setTypeface(null, Typeface.BOLD)
    })
    infoLayout.addView(TextView(this).apply {
        text = "${route.instructions.size} turns"
        textSize = 11f
        setTextColor(Color.parseColor("#B8AFA0"))
    })
    bar.addView(infoLayout)

    // Turn list button
    bar.addView(TextView(this).apply {
        text = "Turns"
        textSize = 12f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#44FFFFFF"))
            cornerRadius = dp(4).toFloat()
        }
        background = bg
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(4) }
        setOnClickListener { showTurnByTurnDialog(route) }
    })

    // Close button
    bar.addView(TextView(this).apply {
        text = "\u2715"
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(4) }
        setOnClickListener { tourViewModel.clearDirections() }
    })

    val parent = binding.mapView.parent as? ViewGroup ?: return
    parent.addView(bar, 0) // Add at top
    directionsInfoView = bar
}

internal fun SalemMainActivity.removeDirectionsInfo() {
    directionsInfoView?.let {
        (it.parent as? ViewGroup)?.removeView(it)
    }
    directionsInfoView = null
}

// ═════════════════════════════════════════════════════════════════════════════
// TURN-BY-TURN INSTRUCTIONS DIALOG
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showTurnByTurnDialog(route: WalkingRoute) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1E1E2A"))
    }

    // Header
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(8))
    }
    headerRow.addView(TextView(this).apply {
        text = "Walking Directions"
        textSize = 18f
        setTextColor(Color.parseColor("#C9A84C"))
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    headerRow.addView(TextView(this).apply {
        text = "%.1f km \u2022 ~%d min".format(route.distanceKm, route.durationMinutes)
        textSize = 12f
        setTextColor(Color.parseColor("#B8AFA0"))
    })
    headerRow.addView(TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    })
    root.addView(headerRow)

    // Turn list
    val scroll = ScrollView(this)
    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(12))
    }

    for ((i, instruction) in route.instructions.withIndex()) {
        if (instruction.text.isBlank()) continue

        val turnIcon = when {
            instruction.text.lowercase().contains("left") -> "\u2B05"
            instruction.text.lowercase().contains("right") -> "\u27A1"
            instruction.text.lowercase().contains("arrive") -> "\uD83C\uDFC1"
            instruction.text.lowercase().contains("start") -> "\uD83D\uDEB6"
            else -> "\u2B06"
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        row.addView(TextView(this).apply {
            text = turnIcon
            textSize = 18f
            setPadding(0, 0, dp(10), 0)
        })

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textLayout.addView(TextView(this).apply {
            text = instruction.text
            textSize = 13f
            setTextColor(Color.parseColor("#F5F0E8"))
        })
        textLayout.addView(TextView(this).apply {
            text = "%.0f m".format(instruction.distanceM)
            textSize = 11f
            setTextColor(Color.parseColor("#B8AFA0"))
        })
        row.addView(textLayout)

        // Tap to center on this turn
        row.setOnClickListener {
            dialog.dismiss()
            binding.mapView.controller.animateTo(instruction.location, 18.0, 800L)
        }

        listLayout.addView(row)

        // Divider
        if (i < route.instructions.size - 1) {
            listLayout.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = dp(2); bottomMargin = dp(2) }
            })
        }
    }

    scroll.addView(listLayout)
    root.addView(scroll)

    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// "WALK HERE" — trigger from any POI detail
// ═════════════════════════════════════════════════════════════════════════════

/** Get walking directions from current GPS to a specific location. */
internal fun SalemMainActivity.walkTo(destination: GeoPoint) {
    val current = lastGpsPoint
    if (current == null) {
        Toast.makeText(this, "Waiting for GPS fix…", Toast.LENGTH_SHORT).show()
        return
    }
    tourViewModel.getDirectionsTo(destination)
    Toast.makeText(this, "Calculating route…", Toast.LENGTH_SHORT).show()
}
