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
import android.view.MotionEvent
import android.widget.FrameLayout

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
 * Single-pass design (top-pinched trapezoid). Two-pass + wider-MapView attempts
 * to fill the upper-corner wedges with real tiles got reverted because the
 * inevitable seam where the two passes met (different perspective transforms
 * sharing an edge) read as a "strange division at horizon" and was worse than
 * just leaving the wedges as the container's background colour.
 *
 * Caveats — this is a one-file fake, not real 3D:
 *   • Same z-level tiles, just skewed → horizon pixelates / labels stretch.
 *   • Upper-corner wedges show the dark-purple TiltContainer background.
 *   • Markers/sprites tilt with the plane (no billboarding — the `setMatrix`
 *     trick that worked in software canvas didn't translate to the Lenovo's
 *     hardware-accelerated canvas).
 *   • No tile-LOD streaming, no OpenGL, no depth.
 *
 * Toggle: long-press the zoom-IN (+) button cycles 0° → 30° → 45° → 60° → 0°.
 * (Bound to + because the POI peek sheet covers the bottom of the right-edge
 * zoom slider where − sits.) Hard-stripped in release via
 * [BuildDefaults.TILT_3D_ENABLED] (= BuildConfig.DEBUG).
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

    fun setTiltDegrees(deg: Float) {
        val clamped = deg.coerceIn(0f, 75f)
        if (clamped == tiltDeg) return
        tiltDeg = clamped
        dirty = true
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
        dirty = true
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
        val transformed = MotionEvent.obtain(ev)
        transformed.transform(invMatrix)
        val handled = super.dispatchTouchEvent(transformed)
        transformed.recycle()
        return handled
    }
}
