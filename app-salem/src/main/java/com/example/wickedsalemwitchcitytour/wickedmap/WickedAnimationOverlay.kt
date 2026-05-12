/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import com.example.locationmapapp.util.DebugLogger
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

    /** Exposed for MapDebugDumper. Read-only view of the wrapped overlays. */
    fun innerOverlays(): List<MapOverlay> = overlays

    /**
     * Per-inner-overlay error suppression. Each inner overlay logs its first
     * draw exception once, then is skipped silently on subsequent frames so a
     * broken overlay doesn't bury the TCP collector under 30 fps of identical
     * stack traces. Cleared only on app restart.
     */
    private val failedOverlays = HashSet<Class<*>>()

    // S243 — extreme-verbose 1 Hz frame summary so the operator can see the
    // bridge is alive + which inner overlays drew + suppressed-error count.
    private var frameCounter = 0L
    private var lastSummaryAtMs = 0L
    private val summaryIntervalMs = 1_000L

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
            mapOrientationDeg = mapView.mapOrientation,
        )
        if (camera.viewportW <= 0 || camera.viewportH <= 0) return

        val now = android.os.SystemClock.uptimeMillis()
        val drewThisFrame = ArrayList<String>(overlays.size)
        for (overlay in overlays) {
            val cls = overlay.javaClass
            if (cls in failedOverlays) continue
            try {
                overlay.tick(now)
                overlay.draw(canvas, camera)
                drewThisFrame.add(cls.simpleName)
            } catch (t: Throwable) {
                failedOverlays.add(cls)
                DebugLogger.e(
                    "WickedAnimationOverlay",
                    "draw() failed for ${cls.simpleName} — suppressing future frames: ${t.message}",
                    t,
                )
            }
        }
        frameCounter++

        // 1 Hz summary — bridge heartbeat with inner-overlay roster + error count.
        if (now - lastSummaryAtMs >= summaryIntervalMs) {
            lastSummaryAtMs = now
            DebugLogger.d(
                "WickedAnimSummary",
                "frame=$frameCounter drew=$drewThisFrame failed=${failedOverlays.size} " +
                    "zoom=${"%.2f".format(camera.zoom)} " +
                    "center=${"%.5f".format(camera.centerLat)},${"%.5f".format(camera.centerLon)} " +
                    "orient=${"%.1f".format(camera.mapOrientationDeg)}° " +
                    "viewport=${camera.viewportW}x${camera.viewportH}",
            )
        }

        // Throttle: only schedule the next invalidate after frameIntervalMs.
        // postInvalidateOnAnimation() every vsync fires too aggressively when
        // we have ~10k+ particles to walk per frame; the UI thread queue
        // backs up and we trip the ANR watchdog.
        nextFrameAtMs = now + frameIntervalMs
        mapView.postInvalidateDelayed(frameIntervalMs)
    }
}
