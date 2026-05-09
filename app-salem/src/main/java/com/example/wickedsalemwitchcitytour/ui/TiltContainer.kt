/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import org.osmdroid.views.MapView

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TiltContainer.kt"

/**
 * S232 — debug-only matrix-tilt prototype. Wraps the osmdroid MapView so the
 * map gets a faux-3D forward-tilt look without leaving osmdroid.
 *
 * `dispatchDraw` concats a perspective `Matrix` (built via setPolyToPoly so the
 * top edge pinches inward toward a foreshortened horizon). `dispatchTouchEvent`
 * runs the inverse on incoming MotionEvents so map panning, pinch-zoom, and POI
 * taps still hit the correct underlying map coords.
 *
 * Single-pass design (top-pinched trapezoid). The S232 two-pass + wider-MapView
 * attempt to fill the wedges got reverted because the seam between the two
 * perspective transforms read as a "strange division at horizon."
 *
 * S233 — single-pass UPWARD MapView extension fills the wedges. While tilted,
 * the MapView child is grown taller (height += [EXTRA_TOP_FRACTION] * H) and
 * pulled up via a negative top-margin so its bottom still lines up with the
 * TiltContainer's bottom and the extra rows extend into negative TiltContainer
 * y-space. Combined with [setClipChildren] = false, the overflow renders. All
 * pixels still pass through the SAME tilt matrix (which maps src y < 0 to dst
 * y < topY, i.e. the upper-corner wedges) — no seam.
 *
 * The map's [MapView.setMapCenterOffset] is set to (0, +extra/2) so the
 * GPS-centered operator marker stays at TiltContainer y = H/2, which the
 * matrix maps to the same screen position as before (~25% from bottom at 60°).
 * The extra map rows above the operator are "further north on the ground,"
 * giving real Salem tiles under tour-route legs that extend into perspective.
 *
 * Caveats — this is a one-file fake, not real 3D:
 *   • Same z-level tiles, just skewed → horizon pixelates / labels stretch.
 *   • Upper-corner wedges show the dark-purple TiltContainer background.
 *   • Markers/sprites tilt with the plane (no billboarding — the `setMatrix`
 *     trick that worked in software canvas didn't translate to the Lenovo's
 *     hardware-accelerated canvas).
 *   • No tile-LOD streaming, no OpenGL, no depth.
 *
 * Toggle: tap the "3D" FAB above the GPS button to cycle
 * 0° → 30° → 45° → 60° → 0°. (S232 wired this to a long-press on the "+"
 * zoom button; S233 promoted it to a dedicated first-class control.) Gated
 * via [BuildDefaults.TILT_3D_ENABLED] which is now `true` in all build
 * types — V1-approved.
 */
class TiltContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var tiltDeg: Float = 0f
    private val tiltMatrix = Matrix()
    private val invMatrix = Matrix()
    private var dirty = true

    private var extraTopPx: Int = 0
    private var savedMapHeight: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var savedMapTopMargin: Int = 0
    private var savedClipChildren: Boolean = true

    fun setTiltDegrees(deg: Float) {
        val clamped = deg.coerceIn(0f, 75f)
        if (clamped == tiltDeg) return
        Log.i(TAG, "setTiltDegrees: ${tiltDeg.toInt()}° → ${clamped.toInt()}° " +
            "(container=${width}x$height, extraTopPx=$extraTopPx)")
        tiltDeg = clamped
        dirty = true
        applyMapExtension()
        invalidate()
    }

    fun getTiltDegrees(): Float = tiltDeg

    /** Exposed for [BillboardMarker] — read-only; do not mutate. */
    fun tiltMatrix(): Matrix {
        if (dirty) rebuildMatrix()
        return tiltMatrix
    }

    /** Exposed for [BillboardMarker] — read-only; do not mutate. */
    fun invTiltMatrix(): Matrix {
        if (dirty) rebuildMatrix()
        return invMatrix
    }

    private fun rebuildMatrix() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (tiltDeg <= 0f) {
            tiltMatrix.reset()
            invMatrix.reset()
        } else {
            val t = (tiltDeg / 90f).coerceIn(0f, 0.85f)
            // Top-pinched trapezoid. Bottom corners at screen corners, top
            // corners pinched inward. topY = 1.0*h*t lands the perspective-
            // corrected centroid at ~75% screen height (≈ 25% from bottom)
            // at max tilt:
            //   diagonals-intersection formula for top-pinched dst gives
            //     y = topY + 0.25 * (h - topY)   when pinch = 0.5*w*t
            //   so y = 0.75*h iff topY = 0.667*h at t = 0.667 (60°).
            val pinch = w * 0.5f * t
            val topY  = h * 1.0f * t
            val src = floatArrayOf(0f, 0f,  w, 0f,  w, h,  0f, h)
            val dst = floatArrayOf(pinch, topY,  w - pinch, topY,  w, h,  0f, h)
            tiltMatrix.setPolyToPoly(src, 0, dst, 0, 4)
            tiltMatrix.invert(invMatrix)
        }
        dirty = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.i(TAG, "onSizeChanged: ${oldw}x$oldh → ${w}x$h (tiltDeg=${tiltDeg.toInt()}°)")
        dirty = true
        // Re-apply on size change so a rotation / window-insets change rebuilds
        // the extra-height layout against the new container height.
        if (tiltDeg > 0f) applyMapExtension()
    }

    /**
     * Find the MapView child. Avoids `findViewById` so this works even when
     * the MapView's id changes; not a hot path so a linear scan is fine.
     * Excludes runtime-added panels (narration bar / tour HUD) — only the
     * actual MapView is resized.
     */
    private fun findMapView(): MapView? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is MapView) return c
        }
        return null
    }

    private fun applyMapExtension() {
        val mv = findMapView()
        if (mv == null) {
            Log.w(TAG, "applyMapExtension: no MapView child found, abort")
            return
        }
        val lp = mv.layoutParams as? MarginLayoutParams
        if (lp == null) {
            Log.w(TAG, "applyMapExtension: MapView lp is not MarginLayoutParams (${mv.layoutParams?.javaClass?.simpleName}), abort")
            return
        }
        val containerH = height
        if (containerH <= 0) {
            Log.i(TAG, "applyMapExtension: containerH=$containerH (not yet measured), defer to onSizeChanged")
            return
        }

        if (tiltDeg <= 0f) {
            if (extraTopPx != 0) {
                Log.i(TAG, "applyMapExtension RESTORE: lp.h=${lp.height}→$savedMapHeight, lp.top=${lp.topMargin}→$savedMapTopMargin, extraTopPx=$extraTopPx→0")
                lp.height = savedMapHeight
                lp.topMargin = savedMapTopMargin
                mv.layoutParams = lp
                clipChildren = savedClipChildren
                extraTopPx = 0
            } else {
                Log.i(TAG, "applyMapExtension RESTORE: already flat (extraTopPx=0), no-op")
            }
            return
        }

        val targetExtra = (containerH * EXTRA_TOP_FRACTION).toInt()
        if (targetExtra == extraTopPx) {
            Log.i(TAG, "applyMapExtension EXTEND: already applied (targetExtra=$targetExtra), no-op")
            return
        }

        if (extraTopPx == 0) {
            // First entry into tilted state — capture flat-mode params for restore.
            savedMapHeight = lp.height
            savedMapTopMargin = lp.topMargin
            savedClipChildren = clipChildren
            clipChildren = false
            Log.i(TAG, "applyMapExtension FIRST-ENTRY: saved lp.h=$savedMapHeight, lp.top=$savedMapTopMargin, clip=$savedClipChildren")
        }
        extraTopPx = targetExtra
        // S233 — SYMMETRIC extension: half above, half below the natural
        // TiltContainer bounds (see KDoc). Tile-fetch uses the full MapView
        // bbox naturally; default View pivot lines up with operator.
        lp.height = containerH + targetExtra
        lp.topMargin = -targetExtra / 2
        mv.layoutParams = lp
        Log.i(TAG, "applyMapExtension EXTEND: containerH=$containerH, targetExtra=$targetExtra, " +
            "lp.h→${lp.height}, lp.top→${lp.topMargin} (memory cost: MapView area = ${lp.height * width} px²)")
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (dirty) rebuildMatrix()
        if (tiltDeg <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val saved = canvas.save()
        canvas.concat(tiltMatrix)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dirty) rebuildMatrix()
        if (tiltDeg <= 0f) return super.dispatchTouchEvent(ev)

        // S233 — `MotionEvent.transform(Matrix)` mangles perspective matrices
        // (silently drops or mis-applies the bottom row), which inverted the
        // y direction for our top-pinched matrix. Symptom: drag-down made the
        // map go up. Fix: rebuild the event from scratch using
        // `Matrix.mapPoints` for each pointer, which DOES handle perspective
        // correctly. This also keeps "touched POI follows finger" feel under
        // perspective compression near the horizon.
        val pointerCount = ev.pointerCount
        val props = Array(pointerCount) { MotionEvent.PointerProperties() }
        val coords = Array(pointerCount) { MotionEvent.PointerCoords() }
        val pts = FloatArray(2)
        for (i in 0 until pointerCount) {
            ev.getPointerProperties(i, props[i])
            ev.getPointerCoords(i, coords[i])
            pts[0] = coords[i].x
            pts[1] = coords[i].y
            invMatrix.mapPoints(pts)
            coords[i].x = pts[0]
            coords[i].y = pts[1]
        }
        val transformed = MotionEvent.obtain(
            ev.downTime,
            ev.eventTime,
            ev.action,
            pointerCount,
            props,
            coords,
            ev.metaState,
            ev.buttonState,
            ev.xPrecision,
            ev.yPrecision,
            ev.deviceId,
            ev.edgeFlags,
            ev.source,
            ev.flags,
        )
        val handled = super.dispatchTouchEvent(transformed)
        transformed.recycle()
        return handled
    }

    companion object {
        private const val TAG = "TiltContainer"


        // How much extra height MapView gets while tilted, as a fraction of
        // the TiltContainer's height. SYMMETRIC distribution: half above the
        // natural top (fills wedges through perspective compression), half
        // below (off-screen, clipped by parent — but keeps MapView's
        // geometric center on the operator marker so no setMapCenterOffset
        // is needed and osmdroid's tile-fetch covers the full MapView bbox).
        //
        // Wedge fill quality: only the top half of EXTRA_TOP_FRACTION feeds
        // the wedge area. So 5.0 here gives ~2.5×containerH of wedge fill
        // (same effective fill as the asymmetric S233 first-attempt at 2.5).
        // Tile cost: ~6× (1 + 5). Lenovo handles fine; Pixel 7a trivial.
        private const val EXTRA_TOP_FRACTION = 5.0f
    }
}
