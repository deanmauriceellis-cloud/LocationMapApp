/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Whitecap overlay: scattered tiny white wave-tips on every water polygon in
 * the loaded PolygonLibrary. Per-polygon bounding-box culling keeps draw
 * cost proportional to *visible* polygons, not the full 500+ archive.
 *
 * Each cap has a fixed geo position seeded once and a phase offset so caps
 * don't all peak in lockstep. At draw time we project geo → screen and paint
 * a tiny curved arc at the current alpha. Reused Path / coords arrays avoid
 * GC pressure.
 */
class AnimatedWaterOverlay(
    private val polygons: List<WickedPolygon>,
) : MapOverlay {

    // Per-polygon precomputed geo bbox for culling.
    private val polyMinLat = DoubleArray(polygons.size)
    private val polyMaxLat = DoubleArray(polygons.size)
    private val polyMinLon = DoubleArray(polygons.size)
    private val polyMaxLon = DoubleArray(polygons.size)
    // Whether each polygon is currently visible. Refreshed each frame.
    private val polyVisible = BooleanArray(polygons.size)

    // Caps, with polyIdx so we can cull cheaply.
    private val caps: Array<Whitecap>
    private val capPolyIdx: IntArray

    private val clipPath = Path()
    private val wavePath = Path()

    private val capPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
        isAntiAlias = true
    }

    private val frameCount = 5
    private val frameDurationMs = 320L
    private var phase = 0.0
    private var lastTimeMs = 0L

    init {
        val rng = Random(0xCAFEBABE)
        val capList = mutableListOf<Whitecap>()
        val polyIdxList = mutableListOf<Int>()

        for ((idx, polygon) in polygons.withIndex()) {
            val ring = polygon.outerRing
            if (ring.size < 3) {
                polyMinLat[idx] = 0.0; polyMaxLat[idx] = 0.0
                polyMinLon[idx] = 0.0; polyMaxLon[idx] = 0.0
                continue
            }
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLon = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            for ((lat, lon) in ring) {
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }
            polyMinLat[idx] = minLat
            polyMaxLat[idx] = maxLat
            polyMinLon[idx] = minLon
            polyMaxLon[idx] = maxLon

            val area = (maxLat - minLat) * (maxLon - minLon)
            val target = (area * 600_000).toInt().coerceIn(6, 300)
            val maxTries = target * 12

            var added = 0
            var tries = 0
            while (added < target && tries < maxTries) {
                tries++
                val lat = rng.nextDouble(minLat, maxLat)
                val lon = rng.nextDouble(minLon, maxLon)
                if (!pointInRing(lat, lon, ring)) continue
                capList.add(
                    Whitecap(
                        lat = lat,
                        lon = lon,
                        phaseOffset = rng.nextDouble(),
                        angleRad = rng.nextDouble(-0.4, 0.4),
                        sizePx = rng.nextDouble(5.0, 13.0).toFloat(),
                        speed = rng.nextDouble(0.4, 1.0).toFloat(),
                    )
                )
                polyIdxList.add(idx)
                added++
            }
        }
        caps = capList.toTypedArray()
        capPolyIdx = polyIdxList.toIntArray()
    }

    fun whitecapCount(): Int = caps.size
    fun polygonCount(): Int = polygons.size

    override fun tick(frameTimeMs: Long) {
        if (lastTimeMs == 0L) lastTimeMs = frameTimeMs
        val dt = frameTimeMs - lastTimeMs
        lastTimeMs = frameTimeMs
        val cycleMs = frameCount * frameDurationMs.toDouble()
        phase = (phase + dt / cycleMs) % 1.0
    }

    override fun draw(canvas: Canvas, camera: CameraState) {
        if (caps.isEmpty()) return

        // Compute camera viewport in geo coords, then mark visible polygons.
        val viewBbox = computeViewportGeoBbox(camera)
        val vMinLat = viewBbox[0]; val vMaxLat = viewBbox[1]
        val vMinLon = viewBbox[2]; val vMaxLon = viewBbox[3]
        var anyVisible = false
        for (i in polygons.indices) {
            val visible = polyMaxLat[i] >= vMinLat && polyMinLat[i] <= vMaxLat &&
                polyMaxLon[i] >= vMinLon && polyMinLon[i] <= vMaxLon
            polyVisible[i] = visible
            if (visible) anyVisible = true
        }
        if (!anyVisible) return

        // Build clip path from visible polygons only.
        clipPath.rewind()
        for (i in polygons.indices) {
            if (!polyVisible[i]) continue
            val ring = polygons[i].outerRing
            if (ring.size < 3) continue
            var first = true
            for ((lat, lon) in ring) {
                val xy = camera.project(lat, lon)
                if (first) {
                    clipPath.moveTo(xy[0], xy[1])
                    first = false
                } else {
                    clipPath.lineTo(xy[0], xy[1])
                }
            }
            clipPath.close()
        }

        canvas.save()
        canvas.clipPath(clipPath)

        for (k in caps.indices) {
            if (!polyVisible[capPolyIdx[k]]) continue
            val cap = caps[k]
            val local = (phase * cap.speed + cap.phaseOffset) % 1.0
            val intensity = (sin(local * Math.PI * 2) * 0.5 + 0.5).let { s -> s * s }
            if (intensity < 0.06) continue

            val proj = camera.project(cap.lat, cap.lon)
            val cx = proj[0]; val cy = proj[1]
            if (cx < -20f || cx > camera.viewportW + 20f ||
                cy < -20f || cy > camera.viewportH + 20f) continue

            val drift = (local.toFloat() - 0.5f) * cap.sizePx * 0.6f
            val dx = cos(cap.angleRad).toFloat()
            val dy = sin(cap.angleRad).toFloat()
            val x0 = cx - cap.sizePx * 0.5f * dx + drift * dx
            val y0 = cy - cap.sizePx * 0.5f * dy + drift * dy
            val x1 = cx + cap.sizePx * 0.5f * dx + drift * dx
            val y1 = cy + cap.sizePx * 0.5f * dy + drift * dy
            val midX = (x0 + x1) * 0.5f - dy * 1.2f
            val midY = (y0 + y1) * 0.5f + dx * 1.2f

            capPaint.alpha = (intensity * 230).toInt().coerceIn(0, 255)
            wavePath.rewind()
            wavePath.moveTo(x0, y0)
            wavePath.quadTo(midX, midY, x1, y1)
            canvas.drawPath(wavePath, capPaint)
        }

        canvas.restore()
    }

    /** Compute the visible viewport's geographic bbox: [minLat, maxLat, minLon, maxLon]. */
    private fun computeViewportGeoBbox(camera: CameraState): DoubleArray {
        val z = camera.intZoom
        val pxPerTile = camera.pxPerTile
        val cTileX = camera.centerTileX
        val cTileY = camera.centerTileY
        val halfTilesX = camera.viewportW / 2.0 / pxPerTile
        val halfTilesY = camera.viewportH / 2.0 / pxPerTile
        val minTileX = cTileX - halfTilesX
        val maxTileX = cTileX + halfTilesX
        val minTileY = cTileY - halfTilesY
        val maxTileY = cTileY + halfTilesY
        val minLon = MercatorMath.tileXToLon(minTileX, z)
        val maxLon = MercatorMath.tileXToLon(maxTileX, z)
        val maxLat = MercatorMath.tileYToLat(minTileY, z)
        val minLat = MercatorMath.tileYToLat(maxTileY, z)
        // Pad slightly so caps near the edge don't pop.
        val padLat = (maxLat - minLat) * 0.05
        val padLon = (maxLon - minLon) * 0.05
        return doubleArrayOf(minLat - padLat, maxLat + padLat, minLon - padLon, maxLon + padLon)
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

    private data class Whitecap(
        val lat: Double,
        val lon: Double,
        val phaseOffset: Double,
        val angleRad: Double,
        val sizePx: Float,
        val speed: Float,
    )
}
