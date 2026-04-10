/*
 * WickedSalemApp v1.5
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
import com.example.wickedsalemwitchcitytour.content.model.NarrationPoint
import java.io.IOException
import kotlin.math.*

/**
 * Manages the proximity dock — horizontal row of nearby POI icon circles
 * at the bottom of the screen. Each icon is loaded from assets/poi-icons/{type}/
 * with a random variant selected per POI (consistent within session).
 *
 * Phase 9T: Salem Walking Tour Restructure
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
    var onPoiTapped: ((NarrationPoint) -> Unit)? = null

    /** Category → display color mapping */
    private val categoryColors = mapOf(
        "witch_museum" to "#6A1B9A", "witch_shop" to "#6A1B9A",
        "psychic" to "#AB47BC", "ghost_tour" to "#E040FB",
        "haunted_attraction" to "#D500F9", "historic_site" to "#8D6E63",
        "museum" to "#FF6F00", "public_art" to "#FF6F00",
        "cemetery" to "#4E342E", "tour" to "#E040FB",
        "attraction" to "#FF6F00", "park" to "#2E7D32",
        "place_of_worship" to "#4E342E", "visitor_info" to "#0277BD",
        "lodging" to "#7B1FA2", "hotel" to "#7B1FA2",
        "brewery" to "#BF360C", "bar" to "#BF360C",
        "restaurant" to "#BF360C", "cafe" to "#BF360C",
        "community_center" to "#1A237E", "government" to "#1A237E",
        "library" to "#5D4037", "shopping" to "#F57F17"
    )

    /** Map narration point type → poi-icons folder name */
    private val typeToFolder = mapOf(
        "witch_museum" to "witch_shop", "witch_shop" to "witch_shop",
        "psychic" to "psychic", "ghost_tour" to "ghost_tour",
        "haunted_attraction" to "haunted_attraction",
        "historic_site" to "historic_house", "museum" to "tourism_history",
        "public_art" to "tourism_history", "cemetery" to "tourism_history",
        "tour" to "ghost_tour", "attraction" to "entertainment",
        "park" to "parks_rec", "place_of_worship" to "worship",
        "visitor_info" to "civic", "lodging" to "lodging", "hotel" to "lodging",
        "brewery" to "food_drink", "bar" to "food_drink",
        "restaurant" to "food_drink", "cafe" to "food_drink",
        "community_center" to "civic", "government" to "civic",
        "library" to "education", "shopping" to "shopping"
    )

    /**
     * Update the dock with nearby narration points, sorted by distance.
     * @param userLat current user latitude
     * @param userLng current user longitude
     * @param points all narration points to consider
     * @param maxItems max icons to show (default 10)
     * @param radiusM max distance in meters (default 300)
     */
    fun update(userLat: Double, userLng: Double, points: List<NarrationPoint>,
               maxItems: Int = 10, radiusM: Double = 300.0) {
        // Calculate distances and filter
        val nearby = points.mapNotNull { p ->
            val dist = haversine(userLat, userLng, p.lat, p.lng)
            if (dist <= radiusM) Pair(p, dist) else null
        }.sortedBy { it.second }.take(maxItems)

        // Update label
        dockLabel.text = if (nearby.isEmpty()) "No POIs nearby" else "Nearby (${nearby.size})"

        // Clear and rebuild icon row
        iconRow.removeAllViews()

        if (nearby.isEmpty()) {
            dockContainer.visibility = View.GONE
            return
        }
        dockContainer.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(context)
        for ((point, distance) in nearby) {
            val item = inflater.inflate(R.layout.dock_icon_item, iconRow, false)

            // Load random icon from assets
            val iconView = item.findViewById<ImageView>(R.id.iconImage)
            loadPoiIcon(point, iconView)

            // Set category color border
            val border = item.findViewById<View>(R.id.iconBorder)
            val colorHex = categoryColors[point.type] ?: "#6A1B9A"
            val ring = border.background as? GradientDrawable
            ring?.setStroke(
                (2 * context.resources.displayMetrics.density).toInt(),
                Color.parseColor(colorHex)
            )

            // Name and distance
            item.findViewById<TextView>(R.id.iconName).text = point.name
            item.findViewById<TextView>(R.id.iconDistance).text = formatDistance(distance)

            // Tap handler
            item.setOnClickListener { onPoiTapped?.invoke(point) }

            iconRow.addView(item)
        }
    }

    /**
     * S112: Render a pre-sorted list of NarrationPoints (the narration queue
     * snapshot, in tier+distance order) directly into the dock. The dock
     * preserves the caller's order — no internal re-sorting — so the dock
     * order matches the audio play order exactly. Top of dock = next to play.
     *
     * @param items the queue snapshot, already filtered to <=50m and sorted
     *              by tier then distance by the caller
     * @param userLat current user latitude (for distance display)
     * @param userLng current user longitude (for distance display)
     * @param maxItems max icons to render (default 10)
     */
    fun updateFromQueue(items: List<NarrationPoint>, userLat: Double, userLng: Double,
                         maxItems: Int = 10) {
        val visibleItems = items.take(maxItems)

        dockLabel.text = if (visibleItems.isEmpty()) "No POIs in queue" else "Queue (${items.size})"

        iconRow.removeAllViews()

        if (visibleItems.isEmpty()) {
            // Hide the dock entirely when the queue is empty — the dock is
            // tied to the voice queue (S112), so an empty queue means
            // nothing pending to hear.
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
            val colorHex = categoryColors[point.type] ?: "#6A1B9A"
            val ring = border.background as? GradientDrawable
            ring?.setStroke(
                (2 * context.resources.displayMetrics.density).toInt(),
                Color.parseColor(colorHex)
            )

            item.findViewById<TextView>(R.id.iconName).text = point.name
            // Compute distance for display only — caller has already filtered/sorted
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

    /** Load a random icon from assets/poi-icons/{folder}/ for this POI */
    private fun loadPoiIcon(point: NarrationPoint, imageView: ImageView) {
        val folder = typeToFolder[point.type] ?: "tourism_history"

        // Get available icons for this folder (cached)
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

        // Assign a consistent random icon per POI (same within session)
        val iconFile = iconAssignments.getOrPut(point.id) {
            icons[abs(point.id.hashCode()) % icons.size]
        }

        try {
            context.assets.open("poi-icons/$folder/$iconFile").use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                imageView.setImageBitmap(bitmap)
                // Circular clip
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

    /** Clips an ImageView to a circle */
    private class CircularOutlineProvider : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}
