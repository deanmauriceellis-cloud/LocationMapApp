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
 * Firefly overlay: tiny bright yellow-green dots blinking inside polygons.
 * Real firefly bioluminescence is mostly off, with short bright flashes — we
 * model that with a sharp on/off cycle (most of the cycle dim, brief peak)
 * and a yellow-green color that pops against either land or grave-tile color.
 *
 * Reusable across kinds (cemeteries, parks, forests). Operator-facing config
 * lives in the Activity by passing a different polygon list and density
 * multiplier — internals are kind-agnostic.
 */
class FireflyOverlay(
    private val polygons: List<WickedPolygon>,
    /** Multiplier on the per-polygon density target; 1.0 is default. */
    private val densityScale: Double = 1.0,
) : MapOverlay {

    private val polyMinLat = DoubleArray(polygons.size)
    private val polyMaxLat = DoubleArray(polygons.size)
    private val polyMinLon = DoubleArray(polygons.size)
    private val polyMaxLon = DoubleArray(polygons.size)
    private val polyVisible = BooleanArray(polygons.size)

    private val flies: Array<Firefly>
    private val flyPolyIdx: IntArray

    private val clipPath = Path()

    private val haloPaint = Paint().apply {
        style = Paint.Style.FILL
        // Spectral cold green — will-o'-the-wisp / ghost orb color. Eerier
        // than warm firefly yellow.
        color = Color.argb(255, 165, 235, 195)
        isAntiAlias = true
    }
    private val corePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 230, 255, 240)
        isAntiAlias = true
    }

    private val frameCount = 5
    private val frameDurationMs = 350L

    // S172 round-3: per-cemetery duty cycle. Each cemetery animates for ~3s
    // then sits quiet for ~10s, with a randomized phase offset so cemeteries
    // don't all flash together. Operator directive: animations should not
    // continuously hammer the screen.
    private val cemeteryActiveMs = 3_000L
    private val cemeteryIdleMs = 10_000L
    private val cemeteryCycleMs = cemeteryActiveMs + cemeteryIdleMs
    private val cemeteryOffsetMs: LongArray = run {
        val r = kotlin.random.Random(0xF12EA110)
        LongArray(polygons.size) { r.nextLong(cemeteryCycleMs) }
    }
    private var phase = 0.0
    private var lastTimeMs = 0L

    init {
        val rng = Random(0xF12EF11E)
        val list = mutableListOf<Firefly>()
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
            // S172 ANR rollback: 6M / cap 700 was overdraw. 4M / cap 250 / floor
            // 30 stays angry but doesn't freeze the UI thread.
            val raw = area * 4_000_000 * densityScale
            val target = raw.toInt().coerceIn(30, 250)
            val maxTries = target * 12

            var added = 0
            var tries = 0
            while (added < target && tries < maxTries) {
                tries++
                val lat = rng.nextDouble(minLat, maxLat)
                val lon = rng.nextDouble(minLon, maxLon)
                if (!pointInRing(lat, lon, ring)) continue
                list.add(
                    Firefly(
                        lat = lat,
                        lon = lon,
                        phaseOffset = rng.nextDouble(),
                        driftAngleRad = rng.nextDouble(0.0, Math.PI * 2),
                        // Larger + brighter orbs for the "angry" read.
                        coreRadiusPx = rng.nextDouble(3.0, 5.5).toFloat(),
                        haloRadiusPx = rng.nextDouble(9.0, 16.0).toFloat(),
                        speed = rng.nextDouble(0.9, 1.6).toFloat(),
                        // Drift sized for ~1.5s cycle; not as far as before.
                        driftPx = rng.nextDouble(20.0, 50.0).toFloat(),
                    )
                )
                polyIdxList.add(idx)
                added++
            }
        }
        flies = list.toTypedArray()
        flyPolyIdx = polyIdxList.toIntArray()
    }

    fun fireflyCount(): Int = flies.size
    fun polygonCount(): Int = polygons.size

    override fun tick(frameTimeMs: Long) {
        if (lastTimeMs == 0L) lastTimeMs = frameTimeMs
        val dt = frameTimeMs - lastTimeMs
        lastTimeMs = frameTimeMs
        val cycleMs = frameCount * frameDurationMs.toDouble()
        phase = (phase + dt / cycleMs) % 1.0
    }

    override fun draw(canvas: Canvas, camera: CameraState) {
        if (flies.isEmpty()) return

        val z = camera.intZoom
        val pxPerTile = camera.pxPerTile
        val cTileX = camera.centerTileX
        val cTileY = camera.centerTileY
        val halfTilesX = camera.viewportW / 2.0 / pxPerTile
        val halfTilesY = camera.viewportH / 2.0 / pxPerTile
        val vMinLon = MercatorMath.tileXToLon(cTileX - halfTilesX, z)
        val vMaxLon = MercatorMath.tileXToLon(cTileX + halfTilesX, z)
        val vMaxLat = MercatorMath.tileYToLat(cTileY - halfTilesY, z)
        val vMinLat = MercatorMath.tileYToLat(cTileY + halfTilesY, z)

        // Per-cemetery viewport visibility. Each firefly within a visible
        // cemetery has its OWN independent pulse phase — no polygon-wide
        // burst gating. Many small isolated fireflies, each on its own
        // rhythm, is the right read for a haunted graveyard.
        var anyVisible = false
        for (i in polygons.indices) {
            val visible = polyMaxLat[i] >= vMinLat && polyMinLat[i] <= vMaxLat &&
                polyMaxLon[i] >= vMinLon && polyMinLon[i] <= vMaxLon
            polyVisible[i] = visible
            if (visible) anyVisible = true
        }
        if (!anyVisible) return

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

        for (k in flies.indices) {
            if (!polyVisible[flyPolyIdx[k]]) continue
            val f = flies[k]
            val local = (phase * f.speed + f.phaseOffset) % 1.0

            // S172 angry-orbs tuning: sharp flash curve. Linear sin keeps
            // orbs visible across most of the cycle; the squared-sin curve
            // we used previously kept too many fireflies dim. Sharp short
            // off-phase comes from the < 0.05 cull below.
            val s = sin(local * Math.PI * 2) * 0.5 + 0.5
            val intensity = s
            if (intensity < 0.05) continue

            val proj = camera.project(f.lat, f.lon)
            // Drift: small smooth motion, constant speed across cycle.
            val driftFrac = (local.toFloat() - 0.5f)
            val cx = proj[0] + cos(f.driftAngleRad).toFloat() * driftFrac * f.driftPx
            val cy = proj[1] + sin(f.driftAngleRad).toFloat() * driftFrac * f.driftPx
            if (cx < -10f || cx > camera.viewportW + 10f ||
                cy < -10f || cy > camera.viewportH + 10f) continue

            // Halo (yellow-green) at moderate alpha, bright white-yellow core
            // at high alpha for the "spark" look.
            haloPaint.alpha = (intensity * 200).toInt().coerceIn(0, 200)
            canvas.drawCircle(cx, cy, f.haloRadiusPx, haloPaint)
            corePaint.alpha = (intensity * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, f.coreRadiusPx, corePaint)
        }

        canvas.restore()
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

    private data class Firefly(
        val lat: Double,
        val lon: Double,
        val phaseOffset: Double,
        val driftAngleRad: Double,
        val coreRadiusPx: Float,
        val haloRadiusPx: Float,
        val speed: Float,
        val driftPx: Float,
    )
}
