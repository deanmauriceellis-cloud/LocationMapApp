/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.graphics.Canvas
import com.example.wickedsalemwitchcitytour.BuildConfig
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module BillboardMarker.kt"

/**
 * Marker subclass that exposes the protected anchor / alpha / bearing fields so
 * [TiltContainer]'s pass-2 billboard renderer can stamp the icon upright at the
 * tilt-mapped screen position with the same visual centering it would have had
 * in flat mode.
 *
 * S239 — early-returns from [draw] when [tiltActive] is set so the base
 * Marker.draw call doesn't paint the icon flat into MapView's display list at
 * tilt > 0°. TiltContainer's pass-2 owns the upright rendering instead.
 *
 * S251 — added [forceFlatDraw] debug bypass + 1Hz draw-count instrumentation.
 * If [forceFlatDraw] is true, the tilt early-return is skipped and base draw
 * always runs. Used to diagnose "markers in overlay list but invisible" reports.
 */
class BillboardMarker(mv: MapView) : Marker(mv) {
    /** Anchor U (0..1) — fraction of icon width past which the position sits. */
    fun billboardAnchorU(): Float = mAnchorU

    /** Anchor V (0..1) — fraction of icon height past which the position sits. */
    fun billboardAnchorV(): Float = mAnchorV

    /** Marker alpha (0..1). */
    fun billboardAlpha(): Float = mAlpha

    override fun draw(canvas: Canvas, pj: Projection) {
        if (BuildConfig.DEBUG) {
            if (tiltActive && !forceFlatDraw) skipped.incrementAndGet() else drawn.incrementAndGet()
            maybeFlush()
        }
        if (tiltActive && !forceFlatDraw) return
        super.draw(canvas, pj)
    }

    companion object {
        @Volatile @JvmStatic var tiltActive: Boolean = false

        /**
         * S251 — when true, the [tiltActive] early-return is bypassed and base
         * draw always runs. Default true in debug so operators can confirm
         * whether tilt suppression is the cause of "invisible markers" reports.
         * Release leaves it false (release also strips the entire field via R8
         * because BuildConfig.DEBUG is false everywhere it's read).
         */
        @Volatile @JvmStatic var forceFlatDraw: Boolean = BuildConfig.DEBUG

        // ── 1Hz draw-count instrumentation (DEBUG only) ─────────────────────
        private val drawn = java.util.concurrent.atomic.AtomicInteger(0)
        private val skipped = java.util.concurrent.atomic.AtomicInteger(0)
        @Volatile private var lastFlushMs: Long = 0L

        private fun maybeFlush() {
            val now = System.currentTimeMillis()
            if (now - lastFlushMs < 1000L) return
            synchronized(this) {
                if (now - lastFlushMs < 1000L) return
                val d = drawn.getAndSet(0)
                val s = skipped.getAndSet(0)
                lastFlushMs = now
                if (d == 0 && s == 0) return
                DebugLogger.d(
                    "BillboardDraw",
                    "drawn=$d skipped=$s tiltActive=$tiltActive forceFlatDraw=$forceFlatDraw"
                )
            }
        }
    }
}
