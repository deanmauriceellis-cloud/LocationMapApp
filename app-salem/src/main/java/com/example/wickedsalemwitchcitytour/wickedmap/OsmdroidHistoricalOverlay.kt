/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.BuildConfig
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * S287 — historical-map raster overlay for the production osmdroid map.
 *
 * Reads tiles from `salem_tiles.sqlite` via [TileArchive] (one archive per
 * provider). The picker swaps the archive when the user picks a year; the
 * slider mutates [opacityAlpha].
 *
 * Why a custom Overlay instead of stacking a second `TilesOverlay`: the
 * existing osmdroid tile-provider plumbing is wired exclusively to the
 * Salem-Custom basemap. Standing up a second `MapTileProviderArray` against
 * the same sqlite (different `provider` column) is plumbing-heavy; a small
 * custom Overlay reading the same `TileArchive` we already use in WickedMap
 * is faster to land.
 */
class OsmdroidHistoricalOverlay(
    @Volatile var archive: TileArchive? = null,
    @Volatile var opacityAlpha: Int = 178,
) : Overlay() {

    private val paint = Paint().apply { isFilterBitmap = true; isDither = true }
    private val nwPt = Point()
    private val sePt = Point()
    private val dstRect = Rect()

    // Verbose-debug aggregation — emit a summary every 5s rather than per-frame.
    private var framesDrawn = 0L
    private var tilesAttempted = 0L
    private var tilesHit = 0L
    private var lastSummaryAtMs = 0L

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val arch = archive ?: return

        paint.alpha = opacityAlpha.coerceIn(0, 255)
        val z = mapView.zoomLevelDouble.toInt().coerceIn(14, 19)
        val proj = mapView.projection
        val bbox = proj.boundingBox

        val n = 1 shl z
        val xMin = lonToTileX(bbox.lonWest, n).coerceIn(0, n - 1)
        val xMax = lonToTileX(bbox.lonEast, n).coerceIn(0, n - 1)
        val yNorth = latToTileY(bbox.latNorth, n).coerceIn(0, n - 1)
        val ySouth = latToTileY(bbox.latSouth, n).coerceIn(0, n - 1)
        val yMin = minOf(yNorth, ySouth)
        val yMax = maxOf(yNorth, ySouth)

        var hits = 0
        var attempts = 0
        for (tx in xMin..xMax) {
            for (ty in yMin..yMax) {
                attempts++
                val bmp = arch.tile(z, tx, ty) ?: continue
                hits++
                val nwLon = tx.toDouble() / n * 360.0 - 180.0
                val nwLat = tileYToLat(ty, n)
                val seLon = (tx + 1).toDouble() / n * 360.0 - 180.0
                val seLat = tileYToLat(ty + 1, n)
                proj.toPixels(GeoPoint(nwLat, nwLon), nwPt)
                proj.toPixels(GeoPoint(seLat, seLon), sePt)
                dstRect.set(nwPt.x, nwPt.y, sePt.x, sePt.y)
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
        }

        framesDrawn++
        tilesAttempted += attempts
        tilesHit += hits
        if (BuildConfig.DEBUG) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastSummaryAtMs >= 5_000L) {
                lastSummaryAtMs = now
                DebugLogger.d(
                    "OsmdroidHistOv",
                    "z=$z xRange=[$xMin..$xMax] yRange=[$yMin..$yMax] " +
                        "frame=$framesDrawn attempts=$attempts hits=$hits " +
                        "cumAttempts=$tilesAttempted cumHits=$tilesHit " +
                        "alpha=${paint.alpha} archive=${arch.javaClass.simpleName}",
                )
            }
        }
    }

    private fun lonToTileX(lon: Double, n: Int): Int =
        ((lon + 180.0) / 360.0 * n).toInt()

    private fun latToTileY(lat: Double, n: Int): Int {
        val rad = Math.toRadians(lat)
        return ((1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * n).toInt()
    }

    private fun tileYToLat(y: Int, n: Int): Double {
        val t = Math.PI * (1.0 - 2.0 * y.toDouble() / n)
        return Math.toDegrees(Math.atan(Math.sinh(t)))
    }
}
