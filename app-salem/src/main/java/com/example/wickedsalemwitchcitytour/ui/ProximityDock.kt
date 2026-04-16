/*
 * WickedSalemWitchCityTour v1.5 — Phase 9U (Session 120)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import java.io.IOException
import kotlin.math.*

/**
 * Manages the proximity dock — horizontal row of nearby POI icon circles
 * at the bottom of the screen. Each icon is loaded from assets/poi-icons/{category}/
 * with a random variant selected per POI (consistent within session).
 *
 * Phase 9U: Unified POI table migration (NarrationPoint → SalemPoi).
 */
class ProximityDock(private val context: Context, private val rootView: View) {

    private val dockContainer: FrameLayout = rootView.findViewById(R.id.proximityDock)
    private val iconRow: LinearLayout = rootView.findViewById(R.id.dockIconRow)
    private val dockLabel: TextView = rootView.findViewById(R.id.dockLabel)

    /** Cache of POI ID → assigned icon asset path (consistent within session) */
    private val iconAssignments = mutableMapOf<String, String>()

    /** Available icon files per category, cached on first access */
    private val categoryIcons = mutableMapOf<String, List<String>>()

    /** Callback when a dock icon is tapped */
    var onPoiTapped: ((SalemPoi) -> Unit)? = null

    /** SalemPoi.category → display color mapping */
    private val categoryColors = mapOf(
        "WITCH_SHOP"          to "#6A1B9A",
        "PSYCHIC"             to "#AB47BC",
        "TOUR_COMPANIES"      to "#FF6F00",
        "HISTORICAL_BUILDINGS" to "#8D6E63",
        "ENTERTAINMENT"       to "#FF6F00",
        "PARKS_REC"           to "#2E7D32",
        "LODGING"             to "#7B1FA2",
        "FOOD_DRINK"          to "#BF360C",
        "SHOPPING"            to "#F57F17",
        "CIVIC"               to "#1A237E",
        "EDUCATION"           to "#5D4037",
        "WORSHIP"             to "#4E342E",
        "HEALTHCARE"          to "#00838F",
        "OFFICES"             to "#546E7A",
        "FINANCE"             to "#37474F",
        "AUTO_SERVICES"       to "#455A64",
    )

    /** SalemPoi.category → poi-icons folder name */
    private val categoryToFolder = mapOf(
        "WITCH_SHOP"          to "witch_shop",
        "PSYCHIC"             to "psychic",
        "TOUR_COMPANIES"      to "tour_companies",
        "HISTORICAL_BUILDINGS" to "historical_buildings",
        "FOOD_DRINK"          to "food_drink",
        "LODGING"             to "lodging",
        "ENTERTAINMENT"       to "entertainment",
        "PARKS_REC"           to "parks_rec",
        "CIVIC"               to "civic",
        "EDUCATION"           to "education",
        "SHOPPING"            to "shopping",
        "WORSHIP"             to "worship",
        "HEALTHCARE"          to "healthcare",
        "OFFICES"             to "offices",
    )

    /**
     * Update the dock with nearby POIs, sorted by distance.
     */
    fun update(userLat: Double, userLng: Double, points: List<SalemPoi>,
               maxItems: Int = 10, radiusM: Double = 300.0) {
        val nearby = points.mapNotNull { p ->
            val dist = haversine(userLat, userLng, p.lat, p.lng)
            if (dist <= radiusM) Pair(p, dist) else null
        }.sortedBy { it.second }.take(maxItems)

        dockLabel.text = if (nearby.isEmpty()) "No POIs nearby" else "Nearby (${nearby.size})"

        iconRow.removeAllViews()

        if (nearby.isEmpty()) {
            dockContainer.visibility = View.GONE
            return
        }
        dockContainer.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(context)
        for ((point, distance) in nearby) {
            val item = inflater.inflate(R.layout.dock_icon_item, iconRow, false)

            val iconView = item.findViewById<ImageView>(R.id.iconImage)
            loadPoiIcon(point, iconView)

            val border = item.findViewById<View>(R.id.iconBorder)
            val colorHex = categoryColors[point.category] ?: "#6A1B9A"
            val ring = border.background as? GradientDrawable
            ring?.setStroke(
                (2 * context.resources.displayMetrics.density).toInt(),
                Color.parseColor(colorHex)
            )

            item.findViewById<TextView>(R.id.iconName).text = point.name
            item.findViewById<TextView>(R.id.iconDistance).text = formatDistance(distance)

            item.setOnClickListener { onPoiTapped?.invoke(point) }

            iconRow.addView(item)
        }
    }

    /**
     * S112: Render a pre-sorted list of SalemPois (the narration queue
     * snapshot, in tier+distance order) directly into the dock.
     */
    fun updateFromQueue(items: List<SalemPoi>, userLat: Double, userLng: Double,
                         maxItems: Int = 10) {
        val visibleItems = items.take(maxItems)

        dockLabel.text = if (visibleItems.isEmpty()) "No POIs in queue" else "Queue (${items.size})"

        iconRow.removeAllViews()

        if (visibleItems.isEmpty()) {
            dockContainer.visibility = View.GONE
            return
        }
        dockContainer.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(context)
        for (point in visibleItems) {
            val item = inflater.inflate(R.layout.dock_icon_item, iconRow, false)

            val iconView = item.findViewById<ImageView>(R.id.iconImage)
            loadPoiIcon(point, iconView)

            val border = item.findViewById<View>(R.id.iconBorder)
            val colorHex = categoryColors[point.category] ?: "#6A1B9A"
            val ring = border.background as? GradientDrawable
            ring?.setStroke(
                (2 * context.resources.displayMetrics.density).toInt(),
                Color.parseColor(colorHex)
            )

            item.findViewById<TextView>(R.id.iconName).text = point.name
            val distance = if (userLat != 0.0 || userLng != 0.0) {
                haversine(userLat, userLng, point.lat, point.lng)
            } else 0.0
            item.findViewById<TextView>(R.id.iconDistance).text =
                if (distance > 0.0) formatDistance(distance) else ""

            item.setOnClickListener { onPoiTapped?.invoke(point) }

            iconRow.addView(item)
        }
    }

    fun show() { dockContainer.visibility = View.VISIBLE }
    fun hide() { dockContainer.visibility = View.GONE }

    /** Load icon from assets/poi-icons/{folder}/ for this POI */
    private fun loadPoiIcon(point: SalemPoi, imageView: ImageView) {
        val folder = categoryToFolder[point.category] ?: "historical_buildings"

        val icons = categoryIcons.getOrPut(folder) {
            try {
                context.assets.list("poi-icons/$folder")
                    ?.filter { it.endsWith(".png") }
                    ?.toList() ?: emptyList()
            } catch (e: IOException) {
                emptyList()
            }
        }

        if (icons.isEmpty()) return

        val iconFile = iconAssignments.getOrPut(point.id) {
            icons[abs(point.id.hashCode()) % icons.size]
        }

        try {
            context.assets.open("poi-icons/$folder/$iconFile").use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                imageView.setImageBitmap(bitmap)
                imageView.clipToOutline = true
                imageView.outlineProvider = CircularOutlineProvider()
            }
        } catch (e: IOException) {
            // Fallback — no image
        }
    }

    private fun formatDistance(meters: Double): String = when {
        meters < 100 -> "${meters.toInt()}m"
        meters < 1000 -> "${(meters / 10).toInt() * 10}m"
        else -> "${"%.1f".format(meters / 1000)}km"
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private class CircularOutlineProvider : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}
