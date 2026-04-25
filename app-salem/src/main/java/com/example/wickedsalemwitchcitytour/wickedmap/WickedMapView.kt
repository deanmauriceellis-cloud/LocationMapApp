/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 2D map view: reads tiles from a TileArchive, handles pan/pinch/fling,
 * draws tiles + overlays on its own render thread. No osmdroid dependency.
 *
 * Verbose logging under tag "WickedMap" — enabled in debug builds. Every
 * gesture, camera change, surface lifecycle transition, thread exception,
 * and frame-timing anomaly is logged so expand/pinch crashes don't silently
 * disappear into void.
 */
class WickedMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    companion object {
        const val TAG = "WickedMap"
    }

    private val camera = MapCamera(
        centerLat = 42.5222,
        centerLon = -70.8967,
        zoom = 17.0,
    )

    private var archive: TileArchive? = null
    private val overlays = mutableListOf<MapOverlay>()
    private var renderThread: RenderThread? = null

    // Per-frame counters, reset each log window.
    private var frameCount = 0
    private var tileHits = 0
    private var tileMisses = 0
    private var lastLogMs = 0L

    private val tilePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = false
    }
    private val bgPaint = Paint().apply { color = Color.rgb(20, 16, 28) }

    private val scaleDetector = ScaleGestureDetector(context, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            Log.d(TAG, "scale begin: focus=(${d.focusX},${d.focusY}) zoom=${camera.zoom}")
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            val sf = d.scaleFactor
            if (!sf.isFinite() || sf <= 0f) {
                Log.w(TAG, "scale: bad factor=$sf, skipping")
                return true
            }
            val zBefore = camera.zoom
            camera.scaleAround(sf, d.focusX, d.focusY)
            val zAfter = camera.zoom
            Log.d(TAG, "scale: factor=$sf pivot=(${d.focusX},${d.focusY}) zoom $zBefore -> $zAfter")
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            Log.d(TAG, "scale end: zoom=${camera.zoom} center=(${camera.centerLat},${camera.centerLon})")
        }
    })

    private val gestureDetector = GestureDetector(context, object :
        GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent,
            distanceX: Float, distanceY: Float
        ): Boolean {
            camera.panPixels(-distanceX, -distanceY)
            return true
        }
    })

    init {
        holder.addCallback(this)
        Log.i(TAG, "WickedMapView init")
    }

    fun attachArchive(archive: TileArchive) {
        this.archive = archive
        Log.i(TAG, "attached TileArchive")
    }

    fun addOverlay(overlay: MapOverlay) {
        overlays.add(overlay)
        Log.i(TAG, "added overlay: ${overlay::class.simpleName}")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceCreated")
        renderThread = RenderThread(holder).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "surfaceChanged: ${width}x${height} format=$format")
        synchronized(camera) {
            camera.viewportW = width
            camera.viewportH = height
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceDestroyed")
        renderThread?.running = false
        renderThread?.join()
        renderThread = null
    }

    private inner class RenderThread(val holder: SurfaceHolder) : Thread("WickedMapRender") {
        @Volatile var running = true

        override fun run() {
            Log.i(TAG, "render thread started")
            setUncaughtExceptionHandler { _, ex ->
                Log.e(TAG, "render thread uncaught exception: ${ex.message}", ex)
            }
            val targetFrameMs = 1000L / 30
            while (running) {
                val frameStart = System.currentTimeMillis()
                if (holder.surface.isValid) {
                    val canvas = try {
                        holder.lockCanvas()
                    } catch (e: Exception) {
                        Log.e(TAG, "lockCanvas failed: ${e.message}", e)
                        null
                    }
                    if (canvas != null) {
                        try {
                            drawFrame(canvas, frameStart)
                        } catch (e: Throwable) {
                            Log.e(TAG, "drawFrame crashed: ${e.message}", e)
                        } finally {
                            try {
                                holder.unlockCanvasAndPost(canvas)
                            } catch (e: Exception) {
                                Log.e(TAG, "unlockCanvasAndPost failed: ${e.message}", e)
                            }
                        }
                    }
                }
                val elapsed = System.currentTimeMillis() - frameStart
                if (elapsed > 100L) {
                    Log.w(TAG, "slow frame: ${elapsed}ms (tileHits=$tileHits tileMisses=$tileMisses)")
                }
                val sleep = targetFrameMs - elapsed
                if (sleep > 0) try { sleep(sleep) } catch (_: InterruptedException) {}
            }
            Log.i(TAG, "render thread exiting")
        }
    }

    private fun drawFrame(canvas: Canvas, frameTimeMs: Long) {
        val snap = camera.snapshot()
        if (snap.viewportW == 0 || snap.viewportH == 0) return

        canvas.drawRect(0f, 0f, snap.viewportW.toFloat(), snap.viewportH.toFloat(), bgPaint)
        drawTiles(canvas, snap)

        for (overlay in overlays) {
            try {
                overlay.tick(frameTimeMs)
                overlay.draw(canvas, snap)
            } catch (e: Throwable) {
                Log.e(TAG, "overlay ${overlay::class.simpleName} crashed: ${e.message}", e)
            }
        }

        frameCount++
        if (lastLogMs == 0L) lastLogMs = frameTimeMs
        if (frameTimeMs - lastLogMs >= 5000L) {
            val secs = (frameTimeMs - lastLogMs) / 1000.0
            val fps = frameCount / secs
            Log.i(TAG, "5s stats: fps=${"%.1f".format(fps)} tileHits=$tileHits tileMisses=$tileMisses z=${snap.zoom}")
            frameCount = 0
            tileHits = 0
            tileMisses = 0
            lastLogMs = frameTimeMs
        }
    }

    private fun drawTiles(canvas: Canvas, snap: CameraState) {
        val arch = archive ?: return
        val z = snap.intZoom
        val pxPerTile = snap.pxPerTile
        if (!pxPerTile.isFinite() || pxPerTile <= 0.0) {
            Log.w(TAG, "drawTiles: bad pxPerTile=$pxPerTile at zoom=${snap.zoom}")
            return
        }

        val centerX = snap.centerTileX
        val centerY = snap.centerTileY
        val halfWinTilesX = snap.viewportW / 2.0 / pxPerTile + 1
        val halfWinTilesY = snap.viewportH / 2.0 / pxPerTile + 1
        val minTileX = floor(centerX - halfWinTilesX).toInt()
        val maxTileX = ceil(centerX + halfWinTilesX).toInt()
        val minTileY = floor(centerY - halfWinTilesY).toInt()
        val maxTileY = ceil(centerY + halfWinTilesY).toInt()

        // Guardrail — if someone manages to zoom way out, the tile grid could
        // become huge. Cap iteration.
        val spanX = maxTileX - minTileX + 1
        val spanY = maxTileY - minTileY + 1
        if (spanX > 256 || spanY > 256) {
            Log.w(TAG, "drawTiles: huge span ${spanX}x${spanY} at z=$z, clipping")
            return
        }

        val tileMax = (1 shl z)
        val dstRect = Rect()

        for (ty in minTileY..maxTileY) {
            if (ty < 0 || ty >= tileMax) continue
            for (tx in minTileX..maxTileX) {
                if (tx < 0 || tx >= tileMax) continue
                val bmp = arch.tile(z, tx, ty)
                if (bmp == null) {
                    tileMisses++
                    continue
                }
                tileHits++

                val screenX = (snap.viewportW / 2.0 + (tx - centerX) * pxPerTile).toFloat()
                val screenY = (snap.viewportH / 2.0 + (ty - centerY) * pxPerTile).toFloat()
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
    }
}
