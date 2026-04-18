/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Low-zoom "you are in Salem" ball. The bundled tile archive only covers
 * zoom 16+ — zooming out past that shows a blank map and strips all visual
 * context. This overlay paints a bright red disc centered on downtown
 * Salem whenever the user is below the bundled-tile zoom so the map still
 * communicates location at a glance.
 *
 * Drawn in screen-space (fixed dp radius) rather than metric radius so the
 * ball stays a consistent, unmissable size across the full zoom-out range
 * (z3 — the map's configured minZoom — up to z15).
 */
class SalemLocationBallOverlay(
    private val center: GeoPoint = SalemBounds.SAMANTHA_STATUE,
    private val hideAtOrAboveZoom: Double = 16.0,
    private val radiusDp: Float = 40f
) : Overlay() {

    private val fillPaint = Paint().apply {
        color = Color.argb(200, 229, 57, 53)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        color = Color.argb(255, 127, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val reusedPoint = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (mapView.zoomLevelDouble >= hideAtOrAboveZoom) return
        mapView.projection.toPixels(center, reusedPoint)
        val density = mapView.resources.displayMetrics.density
        val radiusPx = radiusDp * density
        val cx = reusedPoint.x.toFloat()
        val cy = reusedPoint.y.toFloat()
        canvas.drawCircle(cx, cy, radiusPx, fillPaint)
        canvas.drawCircle(cx, cy, radiusPx, strokePaint)
    }
}
