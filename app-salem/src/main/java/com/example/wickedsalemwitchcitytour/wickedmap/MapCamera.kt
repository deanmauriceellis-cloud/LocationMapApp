/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

/**
 * Camera state stored in geographic lat/lon + fractional zoom. Keeping the
 * canonical state in geo coords (not tile coords at some intZoom) avoids the
 * coordinate-system drift bug that bit us in v1 — where crossing an integer
 * zoom boundary during pinch-zoom silently rescaled the stored center by 2x
 * and parked the map in the middle of the Atlantic.
 *
 * Tile coords are derived on demand using the current integer zoom.
 */
class MapCamera(
    var centerLat: Double,
    var centerLon: Double,
    var zoom: Double,
    var viewportW: Int = 0,
    var viewportH: Int = 0,
    val minZoom: Double = 16.0,
    val maxZoom: Double = 19.0,
    val minLat: Double = 42.475,
    val maxLat: Double = 42.600,
    val minLon: Double = -70.960,
    val maxLon: Double = -70.760,
) {

    val intZoom: Int get() = zoom.toInt().coerceIn(0, 22)
    val zoomScale: Double get() = Math.pow(2.0, zoom - intZoom)

    /** Pan by screen pixels. */
    fun panPixels(dxPx: Float, dyPx: Float) = synchronized(this) {
        val pxPerTile = MercatorMath.TILE_SIZE * zoomScale
        val tx = MercatorMath.lonToTileX(centerLon, intZoom) - dxPx / pxPerTile
        val ty = MercatorMath.latToTileY(centerLat, intZoom) - dyPx / pxPerTile
        centerLon = MercatorMath.tileXToLon(tx, intZoom).coerceIn(minLon, maxLon)
        centerLat = MercatorMath.tileYToLat(ty, intZoom).coerceIn(minLat, maxLat)
    }

    /**
     * Apply a scale factor pivoting around the given screen point. Because
     * camera state is stored in lat/lon (zoom-independent), we only need to:
     *   (1) record the geo point under the pivot at current zoom
     *   (2) update zoom
     *   (3) shift centerLat/centerLon so the same geo point lands under the
     *       same screen pixel at the new zoom
     */
    fun scaleAround(scaleFactor: Float, pivotX: Float, pivotY: Float) = synchronized(this) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return@synchronized
        val newZoom = (zoom + log2(scaleFactor.toDouble())).coerceIn(minZoom, maxZoom)
        if (newZoom == zoom) return@synchronized

        // Geo point under the pivot at current zoom.
        val pivotLatLon = screenToLatLon(pivotX, pivotY)
        zoom = newZoom

        // After zoom change, re-project the pivot lat/lon to screen at new zoom,
        // then shift centerLat/centerLon so it lands at the original pivot pixel.
        val newCamTileX = MercatorMath.lonToTileX(centerLon, intZoom)
        val newCamTileY = MercatorMath.latToTileY(centerLat, intZoom)
        val pxPerTileNew = MercatorMath.TILE_SIZE * zoomScale

        val pivotTileXNew = MercatorMath.lonToTileX(pivotLatLon.second, intZoom)
        val pivotTileYNew = MercatorMath.latToTileY(pivotLatLon.first, intZoom)

        // Offset the pivot should have from center on screen:
        val targetOffsetX = (pivotX - viewportW / 2.0) / pxPerTileNew
        val targetOffsetY = (pivotY - viewportH / 2.0) / pxPerTileNew
        val adjustedCamTileX = pivotTileXNew - targetOffsetX
        val adjustedCamTileY = pivotTileYNew - targetOffsetY

        centerLon = MercatorMath.tileXToLon(adjustedCamTileX, intZoom).coerceIn(minLon, maxLon)
        centerLat = MercatorMath.tileYToLat(adjustedCamTileY, intZoom).coerceIn(minLat, maxLat)
    }

    private fun screenToLatLon(px: Float, py: Float): Pair<Double, Double> {
        val pxPerTile = MercatorMath.TILE_SIZE * zoomScale
        val camTileX = MercatorMath.lonToTileX(centerLon, intZoom)
        val camTileY = MercatorMath.latToTileY(centerLat, intZoom)
        val tx = camTileX + (px - viewportW / 2.0) / pxPerTile
        val ty = camTileY + (py - viewportH / 2.0) / pxPerTile
        return MercatorMath.tileYToLat(ty, intZoom) to MercatorMath.tileXToLon(tx, intZoom)
    }

    fun snapshot(): CameraState = synchronized(this) {
        CameraState(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoom,
            viewportW = viewportW,
            viewportH = viewportH,
        )
    }

    private fun log2(x: Double): Double = Math.log(x) / Math.log(2.0)
}

data class CameraState(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Double,
    val viewportW: Int,
    val viewportH: Int,
) {
    val intZoom: Int get() = zoom.toInt().coerceIn(0, 22)
    val pxPerTile: Double get() = MercatorMath.TILE_SIZE * Math.pow(2.0, zoom - intZoom)
    val centerTileX: Double get() = MercatorMath.lonToTileX(centerLon, intZoom)
    val centerTileY: Double get() = MercatorMath.latToTileY(centerLat, intZoom)

    /** Project a geo point to screen pixels under this camera. */
    fun project(lat: Double, lon: Double): FloatArray {
        val tx = MercatorMath.lonToTileX(lon, intZoom)
        val ty = MercatorMath.latToTileY(lat, intZoom)
        val px = viewportW / 2.0 + (tx - centerTileX) * pxPerTile
        val py = viewportH / 2.0 + (ty - centerTileY) * pxPerTile
        return floatArrayOf(px.toFloat(), py.toFloat())
    }
}
