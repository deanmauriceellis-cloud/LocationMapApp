/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin
import kotlin.random.Random

/**
 * World-anchored whitecap overlay (S172 final v3).
 *
 * At init time, scans a regular lat/lon grid (~33 m steps) across the
 * water-polygon bbox and keeps only grid cells that fall inside any water
 * polygon. The result is a flat list of (lat, lon, phaseOffset) anchors
 * locked to world coordinates.
 *
 * Each frame: iterate the anchors, project each to screen, skip those
 * outside the viewport. Surviving anchors render a small wavy white "~"
 * crest stroke whose intensity follows a continuous sin pulse with a
 * deterministic per-anchor phase offset.
 *
 * Why this shape: anchors at fixed lat/lon means whitecaps **pan with
 * the map** (no detached "screen overlay" feel), and the dense grid
 * ensures plenty are visible at any zoom. No clipPath rebuild per
 * frame — the in-water filter happens once, at init.
 */
class AnimatedWaterOverlay(
    private val polygons: List<WickedPolygon>,
) : MapOverlay {

    private val anchors: FloatArray  // packed [lat0, lon0, phase0, lat1, lon1, phase1, ...]
    private val anchorCount: Int

    private val crestPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(255, 255, 255, 255)
        isAntiAlias = true
    }
    private val crestPath = Path()

    // Per-anchor short-burst duty cycle: each anchor lights up for ~2.5s
    // out of every 8s (~31% duty). At any moment plenty of crests visible
    // in viewport, never all of them, never the same ones.
    private val activeMs = 2_500L
    private val cycleMs = 8_000L

    init {
        val started = android.os.SystemClock.uptimeMillis()

        // ~110 m grid step at Salem latitude. Coarser than first attempt,
        // which ANR'd. Plenty for "always something visible."
        val stepLat = 0.001
        val stepLon = 0.0013

        // Skip absurdly large polygons (the Atlantic Ocean polygon is
        // ~0.148 deg² and almost entirely off-screen anyway). Anything
        // bigger than 0.01 deg² (~110 km²) is excluded — covers any
        // realistic user view, leaves the open ocean unanimated.
        val maxBboxArea = 0.01

        // Per-polygon bboxes for fast first-pass exclusion.
        val polyCount = polygons.size
        val pMinLat = DoubleArray(polyCount)
        val pMaxLat = DoubleArray(polyCount)
        val pMinLon = DoubleArray(polyCount)
        val pMaxLon = DoubleArray(polyCount)
        val polyKeep = BooleanArray(polyCount)
        var keptCount = 0
        var combinedMinLat = Double.POSITIVE_INFINITY
        var combinedMaxLat = Double.NEGATIVE_INFINITY
        var combinedMinLon = Double.POSITIVE_INFINITY
        var combinedMaxLon = Double.NEGATIVE_INFINITY
        for ((idx, poly) in polygons.withIndex()) {
            var pmnLat = Double.POSITIVE_INFINITY
            var pmxLat = Double.NEGATIVE_INFINITY
            var pmnLon = Double.POSITIVE_INFINITY
            var pmxLon = Double.NEGATIVE_INFINITY
            for ((lat, lon) in poly.outerRing) {
                if (lat < pmnLat) pmnLat = lat
                if (lat > pmxLat) pmxLat = lat
                if (lon < pmnLon) pmnLon = lon
                if (lon > pmxLon) pmxLon = lon
            }
            pMinLat[idx] = pmnLat; pMaxLat[idx] = pmxLat
            pMinLon[idx] = pmnLon; pMaxLon[idx] = pmxLon
            val bboxArea = (pmxLat - pmnLat) * (pmxLon - pmnLon)
            if (bboxArea <= maxBboxArea) {
                polyKeep[idx] = true
                keptCount++
                if (pmnLat < combinedMinLat) combinedMinLat = pmnLat
                if (pmxLat > combinedMaxLat) combinedMaxLat = pmxLat
                if (pmnLon < combinedMinLon) combinedMinLon = pmnLon
                if (pmxLon > combinedMaxLon) combinedMaxLon = pmxLon
            }
        }

        val builder = ArrayList<Float>(4000)
        val rng = Random(0xCAFEBABE)
        if (keptCount > 0 && combinedMinLat.isFinite()) {
            var lat = combinedMinLat
            while (lat <= combinedMaxLat) {
                var lon = combinedMinLon
                while (lon <= combinedMaxLon) {
                    var inside = false
                    for (i in polygons.indices) {
                        if (!polyKeep[i]) continue
                        if (lat < pMinLat[i] || lat > pMaxLat[i] ||
                            lon < pMinLon[i] || lon > pMaxLon[i]) continue
                        if (pointInRing(lat, lon, polygons[i].outerRing)) {
                            inside = true
                            break
                        }
                    }
                    if (inside) {
                        // Jitter each anchor within its grid cell so the
                        // resulting pattern looks scattered, not gridded.
                        val jLat = (lat + rng.nextDouble(-stepLat * 0.5, stepLat * 0.5))
                        val jLon = (lon + rng.nextDouble(-stepLon * 0.5, stepLon * 0.5))
                        val hash = (jLat * 1_000_000.0).toLong() xor
                            (jLon * 1_000_000.0).toLong() * 31L
                        val phase = ((hash and 0x3FFFFFFF).toFloat() / 0x3FFFFFFF.toFloat())
                        builder.add(jLat.toFloat())
                        builder.add(jLon.toFloat())
                        builder.add(phase)
                    }
                    lon += stepLon
                }
                lat += stepLat
            }
        } else {
            android.util.Log.w(
                "WickedMap.Water",
                "no eligible water polygons (all > $maxBboxArea deg² bbox)",
            )
        }

        anchorCount = builder.size / 3
        anchors = FloatArray(builder.size)
        for (i in builder.indices) anchors[i] = builder[i]

        val elapsed = android.os.SystemClock.uptimeMillis() - started
        android.util.Log.i(
            "WickedMap.Water",
            "anchors built — count=$anchorCount step=${stepLat}lat/${stepLon}lon polysKept=$keptCount/${polygons.size} elapsed=${elapsed}ms",
        )
    }

    fun anchorCount(): Int = anchorCount
    fun polygonCount(): Int = polygons.size

    private var lastTimeMs = 0L

    // S243 — verbose per-frame stats. 1Hz summary emit only.
    private var frameCount = 0L
    private var lastSummaryAtMs = 0L

    override fun tick(frameTimeMs: Long) {
        lastTimeMs = frameTimeMs
    }

    override fun draw(canvas: Canvas, camera: CameraState) {
        if (anchorCount == 0) return
        val now = lastTimeMs
        val piConst = Math.PI

        val w = camera.viewportW.toFloat()
        val h = camera.viewportH.toFloat()
        val margin = 16f

        var dutyCulled = 0
        var intensityCulled = 0
        var viewportCulled = 0
        var drawn = 0

        var i = 0
        while (i < anchors.size) {
            val lat = anchors[i].toDouble()
            val lon = anchors[i + 1].toDouble()
            val phase = anchors[i + 2]
            i += 3

            // Per-anchor duty cycle: phase 0..1 maps to phaseOffset in ms.
            // cycleTime is where we are in the 16s cycle for this anchor.
            // Skip if outside the 1.5s active window.
            val phaseOffsetMs = (phase * cycleMs.toFloat()).toLong()
            val cycleTime = (now + phaseOffsetMs) % cycleMs
            if (cycleTime >= activeMs) { dutyCulled++; continue }
            val activePhase = cycleTime / activeMs.toDouble()
            val s = sin(activePhase * piConst)
            val intensity = (s * s).toFloat()
            if (intensity < 0.06f) { intensityCulled++; continue }

            val proj = camera.project(lat, lon)
            val cx = proj[0]
            val cy = proj[1]
            if (cx < -margin || cx > w + margin || cy < -margin || cy > h + margin) { viewportCulled++; continue }

            crestPaint.alpha = (intensity * 235).toInt().coerceIn(0, 255)

            val len = 13f + intensity * 6f
            val bend = 3.5f + intensity * 2.0f
            crestPath.rewind()
            crestPath.moveTo(cx - len, cy)
            crestPath.quadTo(cx - len * 0.5f, cy - bend, cx, cy)
            crestPath.quadTo(cx + len * 0.5f, cy + bend, cx + len, cy)
            canvas.drawPath(crestPath, crestPaint)
            drawn++
        }
        frameCount++

        if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG &&
            now - lastSummaryAtMs >= 1_000L) {
            lastSummaryAtMs = now
            com.example.locationmapapp.util.DebugLogger.d(
                "WaterAnchors",
                "frame=$frameCount anchors=$anchorCount drawn=$drawn dutyCulled=$dutyCulled " +
                    "intensityCulled=$intensityCulled viewportCulled=$viewportCulled " +
                    "viewport=${w.toInt()}x${h.toInt()}",
            )
        }
    }

    private fun pointInRing(
        lat: Double,
        lon: Double,
        ring: List<Pair<Double, Double>>,
    ): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val (latI, lonI) = ring[i]
            val (latJ, lonJ) = ring[j]
            if ((latI > lat) != (latJ > lat)) {
                val xIntersect = (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI
                if (lon < xIntersect) inside = !inside
            }
            j = i
        }
        return inside
    }
}
