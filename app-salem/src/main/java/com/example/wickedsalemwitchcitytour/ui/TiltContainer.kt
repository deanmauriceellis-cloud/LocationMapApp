/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Point
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.wickedsalemwitchcitytour.wickedmap.SpriteOverlay
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module TiltContainer.kt"

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

    // S237 — sprite overlay reference, set by SalemMainActivity right after the
    // SpriteOverlay is constructed. Used in the pass-2 billboard draw so haunt
    // sprites stay upright in the perspective scene instead of lying flat on
    // the ground plane.
    var spriteOverlayRef: SpriteOverlay? = null

    // Reused per-frame to avoid allocations in the hot path.
    private val billboardXY = FloatArray(2)
    private val billboardPt = Point()

    // S239 — MapListener that invalidates the tilt container on every scroll
    // and zoom event. Without this, View framework updates mv's RenderNode
    // properties (projection, scaleX from magnify-FAB animator) without
    // dirtying TiltContainer's display list, so pass-2 replays with stale
    // `fabScale` + stale `mv.projection` until something else forces a
    // re-record. Symptom: upright marker drifts after zoom or magnify, snaps
    // back when the operator nudges the map. Only registered while tilt > 0.
    // S242 — both callbacks route through [onMapStateChanged] so external
    // code (e.g. FAB-magnify animator) shares one entry point for the same
    // root cause.
    private val mapListener = object : org.osmdroid.events.MapListener {
        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
            onMapStateChanged()
            return false
        }
        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
            onMapStateChanged()
            return false
        }
    }
    private var mapListenerRegistered = false

    /**
     * S242 — single entry point for "MapView state changed under tilt, please
     * re-record pass-2." Used by the internal MapListener and by external
     * animators (FAB magnify) that mutate `mv.scaleX` without otherwise
     * dirtying the container's display list. No-op when tilt is off, since
     * pass-2 doesn't run at tilt=0 anyway.
     */
    fun onMapStateChanged() {
        if (tiltDeg > 0f) invalidate()
    }

    fun setTiltDegrees(deg: Float) {
        val clamped = deg.coerceIn(0f, 75f)
        if (clamped == tiltDeg) return
        Log.i(TAG, "setTiltDegrees: ${tiltDeg.toInt()}° → ${clamped.toInt()}°")
        val wasActive = tiltDeg > 0f
        val nowActive = clamped > 0f
        tiltDeg = clamped
        dirty = true
        applyMapExtension()
        // S239 — flip the BillboardMarker tilt flag on transition and invalidate
        // the MapView so its display list re-records with markers no-opping
        // their own draw (avoids the flat ghost-marker bug from S237/S238).
        if (wasActive != nowActive) {
            BillboardMarker.tiltActive = nowActive
            val mv = findMapView()
            mv?.invalidate()
            // S239 — only listen to map events while tilted. Avoids per-event
            // invalidate overhead at tilt=0 where pass-2 doesn't run anyway.
            if (mv != null) {
                if (nowActive && !mapListenerRegistered) {
                    mv.addMapListener(mapListener)
                    mapListenerRegistered = true
                } else if (!nowActive && mapListenerRegistered) {
                    mv.removeMapListener(mapListener)
                    mapListenerRegistered = false
                }
            }
        }
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
            val pinch = w * 0.5f * t
            val topY  = h * TOPY_FACTOR * t
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
        val mv = findMapView() ?: return
        val lp = mv.layoutParams as? MarginLayoutParams ?: return
        val containerH = height
        if (containerH <= 0) return  // not measured yet; onSizeChanged will retry

        if (tiltDeg <= 0f) {
            if (extraTopPx != 0) {
                lp.height = savedMapHeight
                lp.topMargin = savedMapTopMargin
                mv.layoutParams = lp
                clipChildren = savedClipChildren
                extraTopPx = 0
                if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
                    com.example.locationmapapp.util.DebugLogger.d(
                        TAG,
                        "applyMapExtension RESTORE: tilt=0° lp.height=${lp.height} topMargin=${lp.topMargin} extraTopPx=0",
                    )
                }
            }
            return
        }

        val targetExtra = (containerH * EXTRA_TOP_FRACTION).toInt()
        if (targetExtra == extraTopPx) return

        val mvHBefore = mv.height
        if (extraTopPx == 0) {
            // First entry into tilted state — capture flat-mode params for restore.
            savedMapHeight = lp.height
            savedMapTopMargin = lp.topMargin
            savedClipChildren = clipChildren
            clipChildren = false
        }
        extraTopPx = targetExtra
        // S233 — SYMMETRIC extension: half above, half below the natural
        // TiltContainer bounds (see KDoc). Tile-fetch uses the full MapView
        // bbox naturally; default View pivot lines up with operator.
        lp.height = containerH + targetExtra
        lp.topMargin = -targetExtra / 2
        mv.layoutParams = lp
        // S242 — force a measure/layout pass after extending. Setting
        // layoutParams alone leaves the new height pending until the next
        // layout cycle; if dispatchDraw fires on the same frame it sees the
        // unextended mv.height (=684 in the S238 debug strip) and pass-1
        // tile-fetch under-reaches. requestLayout() collapses the race.
        mv.requestLayout()
        if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
            com.example.locationmapapp.util.DebugLogger.d(
                TAG,
                "applyMapExtension EXTEND: tilt=${tiltDeg.toInt()}° containerH=$containerH " +
                    "targetExtra=${targetExtra}px lp.height=${lp.height} topMargin=${lp.topMargin} " +
                    "mv.height_before=$mvHBefore (post-requestLayout pending next measure)",
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (dirty) rebuildMatrix()
        if (tiltDeg <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val mv = findMapView()

        // S239 — markers no-op their own draw via BillboardMarker.tiltActive,
        // set on tilt-mode transition in setTiltDegrees. No strip/restore here:
        // markers stay in mv.overlays for hit-testing, but BillboardMarker.draw
        // early-returns so MapView's display list records no marker commands
        // under the tilt matrix. SpriteOverlay short-circuits the same way via
        // its tiltActiveSupplier.
        val saved = canvas.save()
        canvas.concat(tiltMatrix)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)

        // Pass 2 (canvas matrix back at TiltContainer identity): paint sprites
        // first (background atmosphere), then markers on top.
        if (mv != null) {
            spriteOverlayRef?.let { sprites ->
                drawBillboardedSprites(canvas, mv, sprites)
            }
            val markers = mv.overlays.filterIsInstance<Marker>()
            if (markers.isNotEmpty()) {
                drawBillboardedMarkers(canvas, mv, markers)
            }
        }
    }

    /**
     * Depth scale at a post-tilt screen y. Maps `dstY = h` (foreground) to
     * [BILLBOARD_NEAR_SCALE] and `dstY = topY` (horizon) to
     * [BILLBOARD_FAR_SCALE], linear between.
     */
    private fun depthScale(dstY: Float): Float {
        val h = height.toFloat()
        if (h <= 0f) return BILLBOARD_NEAR_SCALE
        val t = (tiltDeg / 90f).coerceIn(0f, 0.85f)
        val topY = h * TOPY_FACTOR * t  // matches rebuildMatrix
        val span = (h - topY).coerceAtLeast(1f)
        val frac = ((dstY - topY) / span).coerceIn(0f, 1f)
        return BILLBOARD_FAR_SCALE + (BILLBOARD_NEAR_SCALE - BILLBOARD_FAR_SCALE) * frac
    }

    private fun drawBillboardedMarkers(canvas: Canvas, mv: MapView, markers: List<Marker>) {
        val proj = mv.projection
        val mvLeft = mv.left
        val mvTop = mv.top
        val cw = width
        val ch = height
        // S237 fix — Projection.toPixels returns UN-rotated mvLocal coords. In
        // osmdroid's normal draw, MapView pre-rotates the canvas by
        // -mapOrientation around the map's geometric center; that's how POIs
        // land at the right visual position when heading-up rotation is on.
        // Pass-2 has no rotated canvas, so we must apply the same rotation
        // ourselves before adding mv.left/mv.top and projecting through the
        // tilt matrix.
        val mvCx = mv.width / 2f
        val mvCy = mv.height / 2f
        // S237 — rotation sign tested empirically against operator's heading-
        // up walk (POIs appeared 180° wrong with -mapOrientation; corrected to
        // +mapOrientation).
        val orientRad = Math.toRadians(mv.mapOrientation.toDouble())
        val cosO = kotlin.math.cos(orientRad).toFloat()
        val sinO = kotlin.math.sin(orientRad).toFloat()
        // S237 — FAB zoom (`mapView.scaleX/Y`) is a View-level transform applied
        // by FrameLayout.drawChild around mv center pivot. Pass-1 markers are
        // drawn under MapView's canvas, so they inherit the scale; pass-2 markers
        // draw on the TiltContainer canvas (outside the View transform), so we
        // must apply the same scale-around-mvCenter manually here AND scale icon
        // sizes by the same factor so they appear identical in scale to the
        // sheared pass-1 path's fab-magnified icons.
        val fabScale = mv.scaleX
        for (m in markers) {
            if (!m.isEnabled) continue
            val pos = m.position ?: continue
            val icon = m.icon ?: continue
            proj.toPixels(pos, billboardPt)
            val mvX = billboardPt.x
            val mvY = billboardPt.y
            // Apply mapOrientation rotation around mv geometric center, then
            // scale around the same center by fabScale (matches the View's
            // setScaleX/Y around the default pivot = view's geometric center).
            val relX = mvX - mvCx
            val relY = mvY - mvCy
            val rotX = (relX * cosO - relY * sinO) * fabScale
            val rotY = (relX * sinO + relY * cosO) * fabScale
            billboardXY[0] = rotX + mvCx + mvLeft
            billboardXY[1] = rotY + mvCy + mvTop
            val ctnX = billboardXY[0]
            val ctnY = billboardXY[1]
            // S238 — coarse pre-transform cull. With S238's softened perspective
            // (topY=0.6*t) the trapezoid covers most of the screen and the
            // remaining unfillable wedge is just two small corner triangles
            // — so allow markers anywhere in MapView's extended drawing rect
            // (mvLocal y ∈ [0, mv.height] → ctnY ∈ [mv.top, mv.bottom]).
            // The post-matrix screen-bounds cull at the end of this block
            // (with 300px slack) discards anything that doesn't project to a
            // visible pixel. Pre-S238 cull rejected ctnY<0 because the wedge
            // was largely unfilled and markers floated on `#2D1B4E`; that
            // condition no longer holds.
            if (ctnY < mvTop - SCREEN_CULL_PRE_PX || ctnY > mvTop + mv.height + SCREEN_CULL_PRE_PX ||
                ctnX < -SCREEN_CULL_PRE_PX || ctnX > cw + SCREEN_CULL_PRE_PX) continue
            tiltMatrix.mapPoints(billboardXY)
            val dx = billboardXY[0]
            val dy = billboardXY[1]
            // Off-screen cull with a generous slack so half-visible icons
            // near the edge still draw.
            if (dx < -SCREEN_CULL_POST_PX || dx > cw + SCREEN_CULL_POST_PX ||
                dy < -SCREEN_CULL_POST_PX || dy > ch + SCREEN_CULL_POST_PX) continue
            val scale = depthScale(dy) * fabScale
            val iw = icon.intrinsicWidth
            val ih = icon.intrinsicHeight
            if (iw <= 0 || ih <= 0) continue
            val drawW = (iw * scale).toInt().coerceAtLeast(1)
            val drawH = (ih * scale).toInt().coerceAtLeast(1)
            val anchorU: Float
            val anchorV: Float
            val markerAlpha: Float
            if (m is BillboardMarker) {
                anchorU = m.billboardAnchorU()
                anchorV = m.billboardAnchorV()
                markerAlpha = m.billboardAlpha()
            } else {
                anchorU = Marker.ANCHOR_CENTER
                anchorV = Marker.ANCHOR_BOTTOM
                markerAlpha = 1f
            }
            val left = (dx - anchorU * drawW).toInt()
            val top = (dy - anchorV * drawH).toInt()
            val alphaInt = (markerAlpha * 255f).toInt().coerceIn(0, 255)
            val savedAlpha = icon.alpha
            if (alphaInt != 255) icon.alpha = alphaInt
            val savedBounds = icon.copyBounds()
            icon.setBounds(left, top, left + drawW, top + drawH)
            icon.draw(canvas)
            icon.bounds = savedBounds
            if (alphaInt != 255) icon.alpha = savedAlpha
        }
    }

    private fun drawBillboardedSprites(canvas: Canvas, mv: MapView, sprites: SpriteOverlay) {
        sprites.drawBillboarded(
            canvas = canvas,
            mapView = mv,
            mvLeftPx = mv.left,
            mvTopPx = mv.top,
            tiltMapPoint = { xy -> tiltMatrix.mapPoints(xy) },
            depthScale = { dstY -> depthScale(dstY) },
        )
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
        // the wedge area. So 5.0 here gives ~2.5×containerH of wedge fill.
        // Tile cost: ~6× (1 + 5). Lenovo handles fine; Pixel 7a trivial.
        private const val EXTRA_TOP_FRACTION = 5.0f

        // Trapezoid top-edge factor: at max tilt the trapezoid's top sits at
        // dst y = h * TOPY_FACTOR * t. 0.6 keeps the perspective vanishing
        // point off-screen so the entire screen is fillable by tile content
        // via matrix extrapolation of the extended MapView, AND keeps z19
        // street/building detail legible (1.0 was too aggressive — distance
        // crushed to illegible gray).
        private const val TOPY_FACTOR = 0.6f

        // Billboard depth scaling. 1.4/0.95 spread keeps a gentle foreground
        // emphasis while distant markers stay big enough to read as upright.
        private const val BILLBOARD_NEAR_SCALE = 1.4f
        private const val BILLBOARD_FAR_SCALE = 0.95f

        // S242 — cull thresholds. Pre-cull (PRE_PX) rejects markers far outside
        // the extended MapView rect BEFORE the tilt matrix transform; post-cull
        // (POST_PX) rejects anything that projects far off the container screen.
        // Exposed `internal` so SpriteOverlay can mirror exactly the same
        // thresholds (S238 cull-loosen kept marker + sprite paths in sync by
        // hand; the constants make that intent explicit).
        internal const val SCREEN_CULL_PRE_PX  = 50f
        internal const val SCREEN_CULL_POST_PX = 300f
    }
}
