/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Paint
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * MapDebugDumper — on-demand snapshot of every Overlay attached to the
 * MapView. Emits one DebugLogger line per overlay so the dump arrives at
 * the TCP collector and the daily log file alongside everything else.
 *
 * Use case: operator sees a visual glitch (e.g. unexpected green polygon)
 * and triggers the dump via Utility menu → "Dump Map Overlays (Debug)".
 * Each emitted line includes the overlay class, item count where it makes
 * sense (Polygon points, FolderOverlay children), paint color, and bbox.
 */
object MapDebugDumper {

    private const val TAG = "MapDump"

    fun dumpAll(mapView: MapView) {
        dumpAll(mapView, reason = "manual")
    }

    /**
     * Event-triggered dump. Call from walk-sim start/stop, tour-leg change,
     * tilt enter/exit, etc., so the dump is correlatable with the event.
     */
    fun dumpNow(mapView: MapView, reason: String) {
        dumpAll(mapView, reason)
    }

    private fun dumpAll(mapView: MapView, reason: String) {
        val overlays = mapView.overlays
        DebugLogger.i(
            TAG,
            "==== map state dump START [reason=$reason] — overlays=${overlays.size} " +
                "zoom=${"%.2f".format(mapView.zoomLevelDouble)} " +
                "center=${"%.5f".format(mapView.mapCenter.latitude)}," +
                "${"%.5f".format(mapView.mapCenter.longitude)} " +
                "orientation=${"%.1f".format(mapView.mapOrientation)}° " +
                "viewport=${mapView.width}x${mapView.height} ====",
        )
        overlays.forEachIndexed { idx, overlay -> describe(idx, overlay) }
        DebugLogger.i(TAG, "==== map state dump END [reason=$reason] ====")
    }

    private fun describe(idx: Int, overlay: Overlay) {
        try {
            val line = StringBuilder()
            line.append("[$idx] ").append(overlay.javaClass.simpleName)
            line.append(" enabled=").append(overlay.isEnabled)

            when (overlay) {
                is Polygon -> describePolygon(line, overlay)
                is Polyline -> describePolyline(line, overlay)
                is Marker -> describeMarker(line, overlay)
                is FolderOverlay -> describeFolder(line, overlay)
                is WickedAnimationOverlay -> describeWickedBridge(line, overlay)
                else -> {
                    // bounds() exists on Overlay but can be null for some types
                    val b = overlay.bounds
                    if (b != null) line.append(" bounds=").append(bboxStr(b))
                }
            }
            DebugLogger.i(TAG, line.toString())
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "[$idx] dump failed for ${overlay.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun describePolygon(sb: StringBuilder, p: Polygon) {
        val pts = p.actualPoints
        sb.append(" points=").append(pts.size)
            .append(" fill=").append(colorHex(p.fillPaint))
            .append(" stroke=").append(colorHex(p.outlinePaint))
            .append(" strokeWidth=").append(p.outlinePaint?.strokeWidth ?: 0f)
        if (pts.isNotEmpty()) {
            val bbox = BoundingBox.fromGeoPoints(pts)
            sb.append(" bbox=").append(bboxStr(bbox))
        }
        if (!p.title.isNullOrBlank()) sb.append(" title='").append(p.title).append("'")
    }

    private fun describePolyline(sb: StringBuilder, p: Polyline) {
        val pts = p.actualPoints
        sb.append(" points=").append(pts.size)
            .append(" stroke=").append(colorHex(p.outlinePaint))
            .append(" strokeWidth=").append(p.outlinePaint?.strokeWidth ?: 0f)
        if (pts.isNotEmpty()) {
            val bbox = BoundingBox.fromGeoPoints(pts)
            sb.append(" bbox=").append(bboxStr(bbox))
        }
        if (!p.title.isNullOrBlank()) sb.append(" title='").append(p.title).append("'")
    }

    private fun describeMarker(sb: StringBuilder, m: Marker) {
        sb.append(" at=").append("%.5f".format(m.position.latitude))
            .append(",").append("%.5f".format(m.position.longitude))
        if (!m.title.isNullOrBlank()) sb.append(" title='").append(m.title).append("'")
    }

    private fun describeFolder(sb: StringBuilder, f: FolderOverlay) {
        val items = f.items
        sb.append(" children=").append(items.size)
        if (!f.name.isNullOrBlank()) sb.append(" name='").append(f.name).append("'")
        val classCounts = HashMap<String, Int>()
        items.forEach { classCounts.merge(it.javaClass.simpleName, 1) { a, b -> a + b } }
        if (classCounts.isNotEmpty()) sb.append(" kinds=").append(classCounts)
    }

    private fun describeWickedBridge(sb: StringBuilder, w: WickedAnimationOverlay) {
        val inner = w.innerOverlays()
        sb.append(" innerCount=").append(inner.size)
        val parts = inner.map { it.javaClass.simpleName + describeInner(it) }
        sb.append(" inner=").append(parts)
    }

    private fun describeInner(inner: MapOverlay): String = when (inner) {
        is RollingGrassOverlay -> "(anchors=${inner.anchorCount()},polygons=${inner.polygonCount()})"
        else -> ""
    }

    private fun colorHex(paint: Paint?): String {
        if (paint == null) return "null"
        val c = paint.color
        return "#%08X".format(c)
    }

    private fun bboxStr(b: BoundingBox): String =
        "[%.5f,%.5f → %.5f,%.5f]".format(b.latSouth, b.lonWest, b.latNorth, b.lonEast)
}
