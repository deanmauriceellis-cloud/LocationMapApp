/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

/**
 * Per-blade grass overlay (S172 final v3 — "isolated tufts in wind").
 *
 * Same world-anchored grid pattern as [AnimatedWaterOverlay]: at init,
 * scan a regular lat/lon grid and keep only points that fall inside a
 * park polygon. Each anchor is a single blade-of-grass with its own
 * continuous sin pulse and a deterministic per-anchor phase offset.
 *
 * No polygon-wide rolling sweep. Each blade animates independently. The
 * eye sees small isolated tufts swaying — the way wind on a lawn looks,
 * not a coordinated wave.
 */
class RollingGrassOverlay(
    private val polygons: List<WickedPolygon>,
) : MapOverlay {

    private val anchors: FloatArray  // packed [lat0, lon0, phase0, lat1, lon1, phase1, ...]
    private val anchorCount: Int

    private val bladePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        // Vivid grass green that reads against the basemap's muted park fill.
        color = Color.argb(255, 60, 210, 80)
        isAntiAlias = true
    }

    // Per-blade short-burst duty cycle: each blade sways for ~1.5s out of
    // every 14s. Scattered isolated tufts, never the whole park at once.
    private val activeMs = 1_500L
    private val cycleMs = 14_000L

    init {
        val started = android.os.SystemClock.uptimeMillis()

        // ~110 m grid step at Salem latitude — same density as water anchors.
        val stepLat = 0.001
        val stepLon = 0.0013

        // Precompute polygon bboxes.
        val polyCount = polygons.size
        val pMinLat = DoubleArray(polyCount)
        val pMaxLat = DoubleArray(polyCount)
        val pMinLon = DoubleArray(polyCount)
        val pMaxLon = DoubleArray(polyCount)
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
            if (pmnLat < combinedMinLat) combinedMinLat = pmnLat
            if (pmxLat > combinedMaxLat) combinedMaxLat = pmxLat
            if (pmnLon < combinedMinLon) combinedMinLon = pmnLon
            if (pmxLon > combinedMaxLon) combinedMaxLon = pmxLon
        }

        val builder = ArrayList<Float>(2000)
        if (polyCount > 0 && combinedMinLat.isFinite()) {
            var lat = combinedMinLat
            while (lat <= combinedMaxLat) {
                var lon = combinedMinLon
                while (lon <= combinedMaxLon) {
                    var inside = false
                    for (i in polygons.indices) {
                        if (lat < pMinLat[i] || lat > pMaxLat[i] ||
                            lon < pMinLon[i] || lon > pMaxLon[i]) continue
                        if (pointInRing(lat, lon, polygons[i].outerRing)) {
                            inside = true
                            break
                        }
                    }
                    if (inside) {
                        val hash = (lat * 1_000_000.0).toLong() xor
                            (lon * 1_000_000.0).toLong() * 31L
                        val phase = ((hash and 0x3FFFFFFF).toFloat() / 0x3FFFFFFF.toFloat())
                        builder.add(lat.toFloat())
                        builder.add(lon.toFloat())
                        builder.add(phase)
                    }
                    lon += stepLon
                }
                lat += stepLat
            }
        }

        anchorCount = builder.size / 3
        anchors = FloatArray(builder.size)
        for (i in builder.indices) anchors[i] = builder[i]

        val elapsed = android.os.SystemClock.uptimeMillis() - started
        android.util.Log.i(
            "WickedMap.Grass",
            "anchors built — count=$anchorCount step=${stepLat}lat/${stepLon}lon polygons=$polyCount elapsed=${elapsed}ms",
        )
    }

    fun anchorCount(): Int = anchorCount
    fun polygonCount(): Int = polygons.size

    private var lastTimeMs = 0L

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

        var i = 0
        while (i < anchors.size) {
            val lat = anchors[i].toDouble()
            val lon = anchors[i + 1].toDouble()
            val phase = anchors[i + 2]
            i += 3

            val phaseOffsetMs = (phase * cycleMs.toFloat()).toLong()
            val cycleTime = (now + phaseOffsetMs) % cycleMs
            if (cycleTime >= activeMs) continue
            val activePhase = cycleTime / activeMs.toDouble()
            val s = sin(activePhase * piConst)
            val intensity = (s * s).toFloat()
            if (intensity < 0.05f) continue

            val proj = camera.project(lat, lon)
            val cx = proj[0]
            val cy = proj[1]
            if (cx < -margin || cx > w + margin || cy < -margin || cy > h + margin) continue

            bladePaint.alpha = (intensity * 230).toInt().coerceIn(0, 255)

            // Each blade is a short vertical-ish stroke that bends with a
            // wind angle modulated by intensity (peak intensity = max bend).
            val height = 6f + intensity * 4f
            val bend = (intensity - 0.5f) * 5f  // -2.5 → +2.5 across cycle
            canvas.drawLine(cx, cy, cx + bend, cy - height, bladePaint)
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
