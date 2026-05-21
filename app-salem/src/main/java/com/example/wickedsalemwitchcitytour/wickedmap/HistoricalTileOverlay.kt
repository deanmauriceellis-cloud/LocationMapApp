/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws a historical-map raster overlay on top of the modern WickedMap basemap.
 *
 * Backed by a dedicated [TileArchive] instance scoped to a single
 * `provider='Historical-YYYY'` row family inside `salem_tiles.sqlite`. Keeping
 * the historical archive separate from the basemap archive avoids LruCache
 * thrashing — both providers retain their own warm tile set.
 *
 * Paint alpha controls overlay transparency (0..255). Setting [archive] to
 * null disables drawing without unloading; useful for the "None" picker
 * selection in Phase 2.
 *
 * Paint order: WickedMapView.drawFrame() invokes this overlay after
 * drawTiles() (modern basemap) and before the animation overlay loop, so
 * historical paper sits between basemap and atmosphere — basemap stays the
 * structural reference, atmospherics still paint on top
 * (feedback_basemap_priority_over_animation.md).
 */
class HistoricalTileOverlay(
    @Volatile var archive: TileArchive? = null,
    @Volatile var opacityAlpha: Int = 178,   // 70% default per plan
) : MapOverlay {

    private val tilePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = false
    }

    private var hits = 0L
    private var misses = 0L
    private var lastSummaryAtMs = 0L

    override fun draw(canvas: Canvas, camera: CameraState) {
        val arch = archive ?: return
        if (opacityAlpha <= 0) return

        val z = camera.intZoom
        val pxPerTile = camera.pxPerTile
        if (!pxPerTile.isFinite() || pxPerTile <= 0.0) return

        val centerX = camera.centerTileX
        val centerY = camera.centerTileY
        val halfWinTilesX = camera.viewportW / 2.0 / pxPerTile + 1
        val halfWinTilesY = camera.viewportH / 2.0 / pxPerTile + 1
        val minTileX = floor(centerX - halfWinTilesX).toInt()
        val maxTileX = ceil(centerX + halfWinTilesX).toInt()
        val minTileY = floor(centerY - halfWinTilesY).toInt()
        val maxTileY = ceil(centerY + halfWinTilesY).toInt()

        val spanX = maxTileX - minTileX + 1
        val spanY = maxTileY - minTileY + 1
        if (spanX > 256 || spanY > 256) return

        val tileMax = (1 shl z)
        val dstRect = Rect()
        tilePaint.alpha = opacityAlpha.coerceIn(0, 255)

        for (ty in minTileY..maxTileY) {
            if (ty < 0 || ty >= tileMax) continue
            for (tx in minTileX..maxTileX) {
                if (tx < 0 || tx >= tileMax) continue
                val bmp = arch.tile(z, tx, ty)
                if (bmp == null) { misses++; continue }
                hits++

                val screenX = (camera.viewportW / 2.0 + (tx - centerX) * pxPerTile).toFloat()
                val screenY = (camera.viewportH / 2.0 + (ty - centerY) * pxPerTile).toFloat()
                val size = pxPerTile.toFloat()

                dstRect.set(
                    screenX.toInt(),
                    screenY.toInt(),
                    (screenX + size).toInt() + 1,
                    (screenY + size).toInt() + 1,
                )
                canvas.drawBitmap(bmp, null, dstRect, tilePaint)
            }
        }

        maybeLogSummary()
    }

    private fun maybeLogSummary() {
        if (!com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSummaryAtMs < 5_000L) return
        lastSummaryAtMs = now
        Log.d(
            "HistOverlay",
            "5s tile stats hits=$hits misses=$misses alpha=$opacityAlpha",
        )
        hits = 0; misses = 0
    }
}
