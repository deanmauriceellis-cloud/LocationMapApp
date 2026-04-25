/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * S172 interim bridge: hosts WickedMap [MapOverlay]s (whitecaps, fireflies)
 * inside an osmdroid [MapView] until the full migration to [WickedMapView]
 * lands.
 *
 * Builds a [CameraState] each frame from the MapView's center / zoom / size,
 * forwards tick + draw to each wrapped overlay, and self-invalidates so the
 * MapView keeps redrawing while the overlays are alive.
 *
 * Limitations of the bridge (acceptable for S172, fixed by full migration):
 *  - Rotation: when [MapView.getMapOrientation] is non-zero (heading-up tour
 *    mode) particles are projected in the unrotated mercator frame and then
 *    drawn on osmdroid's rotated canvas. This rotates animations *with* the
 *    map, which matches the "alive map" intent. The geographic anchor stays
 *    correct because both the tiles and the overlay see the same canvas
 *    rotation.
 */
class WickedAnimationOverlay(
    private val overlays: List<MapOverlay>,
    /** Target frame interval in ms. ~33ms = 30fps; cheap enough to avoid ANRs. */
    private val frameIntervalMs: Long = 33L,
) : Overlay() {

    private var nextFrameAtMs = 0L

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (overlays.isEmpty()) return

        val center = mapView.mapCenter
        val camera = CameraState(
            centerLat = center.latitude,
            centerLon = center.longitude,
            zoom = mapView.zoomLevelDouble,
            viewportW = mapView.width,
            viewportH = mapView.height,
        )
        if (camera.viewportW <= 0 || camera.viewportH <= 0) return

        val now = android.os.SystemClock.uptimeMillis()
        for (overlay in overlays) {
            overlay.tick(now)
            overlay.draw(canvas, camera)
        }

        // Throttle: only schedule the next invalidate after frameIntervalMs.
        // postInvalidateOnAnimation() every vsync fires too aggressively when
        // we have ~10k+ particles to walk per frame; the UI thread queue
        // backs up and we trip the ANR watchdog.
        nextFrameAtMs = now + frameIntervalMs
        mapView.postInvalidateDelayed(frameIntervalMs)
    }
}
