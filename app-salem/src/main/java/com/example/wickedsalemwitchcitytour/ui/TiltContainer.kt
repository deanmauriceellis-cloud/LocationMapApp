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
import com.example.locationmapapp.util.GpuCapability
import android.graphics.Point
import android.graphics.Rect
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
    private var extraSidePx: Int = 0
    private var savedMapHeight: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var savedMapTopMargin: Int = 0
    private var savedMapWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var savedMapLeftMargin: Int = 0
    private var savedClipChildren: Boolean = true

    // S237 — sprite overlay reference, set by SalemMainActivity right after the
    // SpriteOverlay is constructed. Used in the pass-2 billboard draw so haunt
    // sprites stay upright in the perspective scene instead of lying flat on
    // the ground plane.
    var spriteOverlayRef: SpriteOverlay? = null

    // Reused per-frame to avoid allocations in the hot path.
    private val billboardXY = FloatArray(2)
    private val billboardPt = Point()
    // S306 (Tier 2, #22) — reused across the per-frame tilt marker loop so
    // copyBounds() doesn't allocate a fresh Rect per marker per frame.
    private val savedBoundsTmp = Rect()

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

    // S308 — adaptive software-layer tilt for weak/virtualized GPUs. When the GPU
    // can't composite the perspective-textured tile surface (GpuCapability not
    // GOOD), the whole container renders via a software (Skia/CPU) layer while
    // tilted. Container-sized (screen) bitmap, not the extended MapView, so it's
    // ~1 screen of ARGB — well under GL_MAX_TEXTURE_SIZE on any real display.
    private var softwareTiltActive = false
    private var softwareInvalidatePending = false

    // S308 — hoisted so it can be removeCallbacks'd on tilt-off / detach (an
    // inline lambda can't be cancelled — removeCallbacks of a fresh lambda is a
    // no-op). Without cancellation a callback dropped by a detach inside the
    // ~83ms coalesce window would strand softwareInvalidatePending=true and
    // freeze the software-layer view on a later re-tilt (weak GPU only).
    private val softwareInvalidateRunnable = Runnable {
        softwareInvalidatePending = false
        invalidate()
    }

    /**
     * S242 — single entry point for "MapView state changed under tilt, please
     * re-record pass-2." Used by the internal MapListener and by external
     * animators (FAB magnify) that mutate `mv.scaleX` without otherwise
     * dirtying the container's display list. No-op when tilt is off, since
     * pass-2 doesn't run at tilt=0 anyway.
     */
    fun onMapStateChanged() {
        if (tiltDeg <= 0f) return
        if (softwareTiltActive) {
            // S308 — the software-layer raster is CPU-bound; coalesce per-GPS-fix /
            // heading-up invalidations to a capped cadence so walk-sim under tilt
            // doesn't ANR/slideshow on weak hardware. Hardware path stays immediate.
            if (softwareInvalidatePending) return
            softwareInvalidatePending = true
            postDelayed(
                softwareInvalidateRunnable,
                (1000L / BuildDefaults.TILT_SOFTWARE_MAX_FPS).coerceAtLeast(1L),
            )
        } else {
            invalidate()
        }
    }

    // S308 — close the detach race: if the view detaches with a coalesced
    // software-path invalidate still queued, drop it and clear the gate so a
    // fresh attach + re-tilt starts clean (prevents a stranded pending flag on
    // weak GPUs). GOOD devices never set the flag, so this is a no-op for them.
    override fun onDetachedFromWindow() {
        removeCallbacks(softwareInvalidateRunnable)
        softwareInvalidatePending = false
        super.onDetachedFromWindow()
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
                applyTiltZoomClamp(mv, nowActive)
            }
            // S308 — on weak/virtualized GPUs (BlueStacks, emulators, …) the tilted
            // basemap tile composite corrupts to magenta because the tiles draw
            // THROUGH the setPolyToPoly perspective matrix on a HARDWARE canvas (the
            // GL texture-sample the driver fumbles). Force THIS container (the view
            // that owns the canvas.concat in dispatchDraw) to a software layer while
            // tilted so the whole composite rasterizes on Skia/CPU — no GL texture
            // sampling, corruption gone. GOOD mobile GPUs (Adreno/Mali/…) are never
            // flagged and stay byte-identical on the hardware fast path. The matrix,
            // EXTRA_TOP_FRACTION and all extension geometry are UNTOUCHED — coverage
            // preserved; this swaps only the rasterizer backend.
            val wantSoftware = nowActive && GpuCapability.needsSoftwareTilt()
            if (wantSoftware && !softwareTiltActive) {
                setLayerType(LAYER_TYPE_SOFTWARE, null)
                softwareTiltActive = true
            } else if (!wantSoftware && softwareTiltActive) {
                setLayerType(LAYER_TYPE_HARDWARE, null)
                softwareTiltActive = false
                // S308 — cancel any in-flight coalesced invalidate + clear the gate
                // so a later re-tilt isn't starved by a stale pending flag.
                removeCallbacks(softwareInvalidateRunnable)
                softwareInvalidatePending = false
            }
            if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
                com.example.locationmapapp.util.DebugLogger.d(
                    TAG,
                    "tilt-layer: nowActive=$nowActive verdict=${GpuCapability.verdict} " +
                        "needsSoftware=${GpuCapability.needsSoftwareTilt()} swActive=$softwareTiltActive " +
                        "renderer='${GpuCapability.renderer}' maxTex=${GpuCapability.maxTextureSize}",
                )
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
        val containerW = width
        if (containerH <= 0 || containerW <= 0) return  // not measured yet; onSizeChanged will retry

        if (tiltDeg <= 0f) {
            if (extraTopPx != 0 || extraSidePx != 0) {
                lp.height = savedMapHeight
                lp.topMargin = savedMapTopMargin
                lp.width = savedMapWidth
                lp.leftMargin = savedMapLeftMargin
                mv.layoutParams = lp
                clipChildren = savedClipChildren
                extraTopPx = 0
                extraSidePx = 0
                if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
                    com.example.locationmapapp.util.DebugLogger.d(
                        TAG,
                        "applyMapExtension RESTORE: tilt=0° lp.height=${lp.height} topMargin=${lp.topMargin} " +
                            "lp.width=${lp.width} leftMargin=${lp.leftMargin} extra(top,side)=(0,0)",
                    )
                }
            }
            return
        }

        val targetTop = (containerH * EXTRA_TOP_FRACTION).toInt()
        // S253 — tilt-proportional LATERAL overhang. The pinch matrix
        // (rebuildMatrix) carves triangular wedges off the LEFT and RIGHT
        // edges of the trapezoid for y in [topY, h]. EXTRA_TOP_FRACTION's
        // upward extension does not reach those side wedges. Extending the
        // MapView LATERALLY lets the perspective matrix extrapolate src
        // x<0 (and x>w) into them. Required overhang per side to fully
        // fill the wedge at its top tip (dst y = topY):
        //     Δ = (0.5·t / (1 - t)) · containerW
        // Capped at EXTRA_SIDE_CAP to bound tile-fetch cost at extreme
        // tilts. clamp on t at 0.85 (matches rebuildMatrix) prevents
        // division-by-zero / asymptote near 90°.
        val t = (tiltDeg / 90f).coerceIn(0f, 0.85f)
        val sideRatio = (0.5f * t / (1f - t).coerceAtLeast(0.0001f))
            .coerceAtMost(EXTRA_SIDE_CAP)
        val targetSide = (containerW * sideRatio).toInt()

        if (targetTop == extraTopPx && targetSide == extraSidePx) return

        val mvHBefore = mv.height
        val mvWBefore = mv.width
        if (extraTopPx == 0 && extraSidePx == 0) {
            // First entry into tilted state — capture flat-mode params for restore.
            savedMapHeight = lp.height
            savedMapTopMargin = lp.topMargin
            savedMapWidth = lp.width
            savedMapLeftMargin = lp.leftMargin
            savedClipChildren = clipChildren
            clipChildren = false
        }
        extraTopPx = targetTop
        extraSidePx = targetSide
        // S233 — SYMMETRIC vertical extension: half above, half below the
        // natural TiltContainer bounds (see KDoc). Tile-fetch uses the full
        // MapView bbox naturally; default View pivot lines up with operator.
        lp.height = containerH + targetTop
        lp.topMargin = -targetTop / 2
        // S253 — SYMMETRIC lateral extension: equal overhang on each side
        // via negative leftMargin keeps the MapView geometric center on
        // the TiltContainer center (matches the vertical-extension pivot).
        lp.width = containerW + 2 * targetSide
        lp.leftMargin = -targetSide
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
                "applyMapExtension EXTEND: tilt=${tiltDeg.toInt()}° " +
                    "container=${containerW}x${containerH} " +
                    "target(top,side)=(${targetTop}px,${targetSide}px) " +
                    "lp=${lp.width}x${lp.height} margin(top,left)=(${lp.topMargin},${lp.leftMargin}) " +
                    "mv_before=${mvWBefore}x${mvHBefore} (post-requestLayout pending next measure)",
            )
        }
    }

    /**
     * S306 (PerfStabilityReview Tier 1) — clamp tile-fetch zoom while tilted.
     * The bundled basemap maxes at z19 (no z20 tiles exist in salem_tiles.sqlite);
     * at z20 osmdroid overzooms by upscaling z19 AND allocating a throwaway
     * approximation bitmap per visible tile — multiplied across the ~13×
     * tilt-extended viewport, a major slice of the tilt OOM. z19 fills the
     * wedge IDENTICALLY with native tiles, so this is COVERAGE-NEUTRAL: it
     * removes only the redundant upscale layer, never a tile (the operator's
     * no-blank-wedge constraint is untouched). Restored to [MAP_MAX_OVERZOOM]
     * on tilt-off. Driven from [setTiltDegrees] — the single funnel for both
     * the 3D FAB and the cold-start saved-tilt restore — so neither path can
     * bypass it. The grown tile cache is released under real memory pressure
     * via SalemMainActivity.onTrimMemory (no clear-on-tilt-off, to avoid a
     * flat-map tile reflow flicker on every toggle). Retail DEFAULT_ZOOM is
     * 18, so the common case never reaches this clamp; only a user who
     * manually zoomed to z20 then tilts is nudged to z19 (one step wider,
     * never blank).
     */
    private fun applyTiltZoomClamp(mv: MapView, tiltOn: Boolean) {
        if (tiltOn) {
            if (mv.maxZoomLevel > TILT_MAX_FETCH_ZOOM) {
                mv.maxZoomLevel = TILT_MAX_FETCH_ZOOM
                if (mv.zoomLevelDouble > TILT_MAX_FETCH_ZOOM) {
                    mv.controller.setZoom(TILT_MAX_FETCH_ZOOM)
                }
            }
        } else if (mv.maxZoomLevel < MAP_MAX_OVERZOOM) {
            mv.maxZoomLevel = MAP_MAX_OVERZOOM
        }
        if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
            com.example.locationmapapp.util.DebugLogger.d(
                TAG,
                "applyTiltZoomClamp tiltOn=$tiltOn maxZoom=${mv.maxZoomLevel} zoom=${mv.zoomLevelDouble}",
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
            // S306 (Tier 2, #4/#8/#25) — iterate overlays in place; the old
            // filterIsInstance<Marker>() allocated a fresh ~580-element ArrayList
            // every frame while tilted (~36 MB/min of GC garbage on the OOM path).
            drawBillboardedMarkers(canvas, mv)
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

    private fun drawBillboardedMarkers(canvas: Canvas, mv: MapView) {
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
        for (o in mv.overlays) {
            if (o !is Marker || !o.isEnabled) continue
            val m: Marker = o
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
            icon.copyBounds(savedBoundsTmp)  // S306 — into the reused Rect (no per-marker alloc)
            icon.setBounds(left, top, left + drawW, top + drawH)
            icon.draw(canvas)
            icon.bounds = savedBoundsTmp
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

        // S306 — tile-fetch zoom ceiling while tilted (see applyTiltZoomClamp).
        // Matches the basemap's native max (no z20 tiles exist), so clamping
        // here removes osmdroid's z20 overzoom-approximation bitmaps without
        // changing what fills the wedge. Restored to MAP_MAX_OVERZOOM (=20) on
        // tilt-off. NOTE this is a memory clamp, NOT a coverage change.
        private const val TILT_MAX_FETCH_ZOOM = 19.0

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

        // S253 — cap on lateral overhang per side, as a fraction of
        // containerW. Required overhang to fully fill the side wedge is
        // 0.5·t/(1-t)·containerW, which goes asymptotic near max tilt
        // (75°). Cap at 1.5 means at most 4× total MapView width (1 + 2·1.5).
        // Combined with the 6× vertical extension, peak tile area is 24×
        // at extreme tilts — still well below the Lenovo's bundled-tile
        // bitmap budget at moderate zooms; if it chunks, dial down.
        // At tilt=42° (current debug-FAB default) sideRatio≈0.44 → mv.width≈1.88×
        // and total tile area ≈ 11.3× containerArea.
        private const val EXTRA_SIDE_CAP = 1.5f

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
