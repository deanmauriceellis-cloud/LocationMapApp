/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.locationmapapp.util.DebugLogger
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S226: per-POI haunt-effect overlay. Reads admin-configured haunt rows from
 * [com.example.wickedsalemwitchcitytour.content.model.SalemPoi] and fires a
 * 1-second sprite peek on the map near each haunt POI when the user is in
 * range. Two-band cadence: outside outer_range = silent; outer band fires at
 * outer_interval_s; inner band fires at inner_interval_s (closer = more often).
 *
 * Sprites live in `assets/sprites/<id>.webp` (S170 bake — 16-frame WebPs that
 * we read as their first frame for a static peek; rotation/animation tuning
 * is a follow-up sprint).
 *
 * Wires into the existing [WickedAnimationOverlay] bridge alongside the
 * water/firefly/grass overlays — admin opt-in only (NULL haunt_sprite_id =
 * no haunt).
 */
class SpriteOverlay(private val context: Context) : MapOverlay {

    /** Admin-configured haunt for one POI. Mirrors the haunt_* columns. */
    data class HauntConfig(
        val poiId: String,
        val lat: Double,
        val lng: Double,
        val spriteId: String,
        val outerRangeM: Int,
        val outerIntervalS: Int,
        val innerRangeM: Int,
        val innerIntervalS: Int,
        val enabled: Boolean,
        /** S227 — per-fire dance length, seconds. NULL = [DEFAULT_DURATION_MS]. */
        val durationS: Float? = null,
    )

    private val assetManager = context.assets
    // Each sprite is 16 frames at 22.5° heading increments (S170 bake split
    // into per-frame WebPs at S227). Cycling through frames during the dance
    // makes the character appear to spin/turn while the size bob makes it
    // look like it's coming closer or moving away.
    private val spriteFrames = HashMap<String, Array<Bitmap?>?>()

    @Volatile private var haunts: List<HauntConfig> = emptyList()

    private val lastFireMs = HashMap<String, Long>()
    private val activeSwoops = ArrayList<ActiveSwoop>()

    @Volatile private var userLat: Double = Double.NaN
    @Volatile private var userLng: Double = Double.NaN
    @Volatile private var nowMs: Long = 0L

    private val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

    /** Update the user's GPS position. Called from the location observer. */
    fun setUserLocation(lat: Double, lng: Double) {
        userLat = lat
        userLng = lng
    }

    /** Replace the haunt list. Call after loading POIs from Room. */
    fun setHaunts(list: List<HauntConfig>) {
        haunts = list.toList()
        // Drop fire-state for POIs we no longer track.
        val keep = haunts.map { it.poiId }.toSet()
        lastFireMs.keys.retainAll(keep)
    }

    fun hauntCount(): Int = haunts.size
    fun activeSwoopCount(): Int = activeSwoops.size

    override fun tick(frameTimeMs: Long) {
        nowMs = frameTimeMs
        val uLat = userLat
        val uLng = userLng

        if (!uLat.isNaN() && !uLng.isNaN()) {
            for (h in haunts) {
                if (!h.enabled) continue
                if (h.outerRangeM <= 0) continue
                val distM = haversineMeters(uLat, uLng, h.lat, h.lng)
                if (distM > h.outerRangeM) {
                    lastFireMs.remove(h.poiId)
                    continue
                }
                val intervalMs =
                    if (h.innerRangeM > 0 && distM <= h.innerRangeM) {
                        max(1, h.innerIntervalS) * 1000L
                    } else {
                        max(1, h.outerIntervalS) * 1000L
                    }
                val last = lastFireMs[h.poiId] ?: 0L
                if (last == 0L || frameTimeMs - last >= intervalMs) {
                    val frames = loadSpriteFrames(h.spriteId) ?: continue
                    val durationMs = ((h.durationS ?: DEFAULT_DURATION_S) * 1000f)
                        .toLong()
                        .coerceAtLeast(MIN_DURATION_MS)
                    // Randomize each fire so the same POI dances differently
                    // every time. Pseudo-3D bob: depth oscillates between
                    // (1 - amplitude) "near" and (1 + amplitude) "far"; scale
                    // is the inverse so closer = bigger. Phase + cycle count
                    // + horizontal drift offsets all randomized. Bob cycle
                    // count scales with duration so a 2s dance gets ~2x as
                    // many bobs as a 1s dance (fixed cycles/sec, not fixed
                    // cycles/dance).
                    // Single upright facing — frame 11 is the S170-calibrated
                    // toward-viewer pose ("frame 15 faces me" w/ frameOffset=11
                    // in the prior demo). Fall back to first available frame
                    // if 11 is missing for this sprite.
                    val firstNonNull = frames.indexOfFirst { it != null }
                    val frameIdx = if (frames[FRONT_FRAME_INDEX] != null)
                        FRONT_FRAME_INDEX else firstNonNull
                    // Path: gentle elliptical drift around the POI over the
                    // dance — character translates in a slow loop. Radii are
                    // in screen pixels, so the sprite traces ~80-150 px around
                    // the POI marker. One full orbit per dance regardless of
                    // duration, so 3s = slow drift, 30s = much slower drift.
                    val sw = ActiveSwoop(
                        lat = h.lat,
                        lng = h.lng,
                        frames = frames,
                        startMs = frameTimeMs,
                        durationMs = durationMs,
                        bobAmplitude = randomInRange(0.06f, 0.14f),
                        bobCycles = randomInRange(1.5f, 2.5f),
                        phase = randomInRange(0f, (2 * PI).toFloat()),
                        pathRadiusPxX = randomInRange(70f, 140f),
                        pathRadiusPxY = randomInRange(50f, 100f),
                        pathPhase = randomInRange(0f, (2 * PI).toFloat()),
                        pathDirection = if (Math.random() < 0.5) 1 else -1,
                        frameIdx = frameIdx,
                    )
                    activeSwoops.add(sw)
                    lastFireMs[h.poiId] = frameTimeMs
                    DebugLogger.i(
                        TAG,
                        "fire ${h.poiId} sprite=${h.spriteId} duration=${durationMs}ms " +
                            "amp=%.2f orbit=(%.0f,%.0f) frame=%d".format(
                                sw.bobAmplitude, sw.pathRadiusPxX, sw.pathRadiusPxY,
                                sw.frameIdx
                            )
                    )
                }
            }
        }

        if (activeSwoops.isNotEmpty()) {
            val it = activeSwoops.iterator()
            while (it.hasNext()) {
                val sw = it.next()
                if (frameTimeMs - sw.startMs > sw.durationMs) it.remove()
            }
        }
    }

    override fun draw(canvas: Canvas, camera: CameraState) {
        if (activeSwoops.isEmpty()) return

        for (sw in activeSwoops) {
            val elapsed = nowMs - sw.startMs
            val t = (elapsed.toFloat() / sw.durationMs).coerceIn(0f, 1f)

            // Alpha envelope: short fade-in / fade-out at the edges of the
            // dance, full opacity through the middle. With a flat-top
            // trapezoid most of the dance plays at full alpha — operator
            // said the previous bell-curve made it feel like a 300ms flash.
            val fadeFraction = ALPHA_FADE_FRACTION
            val alphaF = when {
                t < fadeFraction -> t / fadeFraction
                t > 1f - fadeFraction -> (1f - t) / fadeFraction
                else -> 1f
            }
            val alpha = (alphaF * 255f).toInt().coerceIn(0, 255)
            if (alpha <= 0) continue

            // Gentle pseudo-3D depth: small near/far oscillation so the
            // character looks like it's drifting closer and farther. Lower
            // amplitude than before — operator wanted gentle, not pulsating.
            val depth = 1f + sw.bobAmplitude * sin(
                2.0 * PI * sw.bobCycles * t + sw.phase
            ).toFloat()
            val scale = (1f / depth).coerceIn(MIN_SCALE, MAX_SCALE)

            // Path: one elliptical orbit around the POI over the dance.
            // pathDirection (±1) flips clockwise/counter-clockwise so each
            // fire orbits a different way.
            val pathArg = (2.0 * PI * t * sw.pathDirection + sw.pathPhase).toFloat()
            val proj = camera.project(sw.lat, sw.lng)
            val cx = proj[0] + sw.pathRadiusPxX * cos(pathArg) * alphaF
            // Negative Y so the orbit floats above the POI marker rather
            // than dropping below it. Sit ~50 px above the POI center.
            val cy = proj[1] - 50f + sw.pathRadiusPxY * sin(pathArg) * alphaF

            val bm = sw.frames.getOrNull(sw.frameIdx) ?: continue

            val bw = bm.width.toFloat()
            val bh = bm.height.toFloat()
            val srcAspect = bh / bw
            val drawW = TARGET_SIZE_PX * scale
            val drawH = drawW * srcAspect

            val left = cx - drawW / 2f
            val top = cy - drawH / 2f
            val srcRect = SRC_RECT_REUSABLE.apply {
                set(0, 0, bm.width, bm.height)
            }
            val dstRect = DST_RECT_REUSABLE.apply {
                set(left, top, left + drawW, top + drawH)
            }
            paint.alpha = alpha
            // Always draw the sprite upright on screen, regardless of map
            // rotation. Osmdroid pre-rotates the canvas by mapOrientation in
            // heading-up mode; we cancel that rotation around this sprite's
            // screen anchor so the bitmap stays screen-aligned. Sprite is a
            // dumb dancing graphic — it has no concept of north.
            if (camera.mapOrientationDeg != 0f) {
                canvas.save()
                canvas.rotate(-camera.mapOrientationDeg, cx, cy)
                canvas.drawBitmap(bm, srcRect, dstRect, paint)
                canvas.restore()
            } else {
                canvas.drawBitmap(bm, srcRect, dstRect, paint)
            }
        }
    }

    private fun randomInRange(min: Float, max: Float): Float =
        min + (Math.random().toFloat() * (max - min))

    /**
     * Lazy-load all 16 heading frames for one sprite. Frames live at
     * `assets/sprites/<id>/00.webp` … `15.webp`. Missing frames are tolerated
     * (returns null cells) but at least one frame must load or we treat the
     * sprite as broken and refuse to spawn a swoop.
     */
    private fun loadSpriteFrames(id: String): Array<Bitmap?>? {
        spriteFrames[id]?.let { return it }
        if (spriteFrames.containsKey(id)) return null
        val frames = arrayOfNulls<Bitmap>(FRAME_COUNT)
        var loaded = 0
        for (i in 0 until FRAME_COUNT) {
            val path = "sprites/$id/%02d.webp".format(i)
            try {
                assetManager.open(path).use { input ->
                    val bm = BitmapFactory.decodeStream(input)
                    if (bm != null) {
                        frames[i] = bm
                        loaded++
                    }
                }
            } catch (_: Exception) { /* missing frame; tolerated */ }
        }
        val result = if (loaded > 0) frames else null
        spriteFrames[id] = result
        DebugLogger.i(TAG, "loaded sprite '$id' frames=$loaded/$FRAME_COUNT")
        return result
    }

    private data class ActiveSwoop(
        val lat: Double,
        val lng: Double,
        val frames: Array<Bitmap?>,
        val startMs: Long,
        val durationMs: Long,
        val bobAmplitude: Float,
        val bobCycles: Float,
        val phase: Float,
        /** Elliptical orbit radii around the POI, in screen pixels. */
        val pathRadiusPxX: Float,
        val pathRadiusPxY: Float,
        val pathPhase: Float,
        val pathDirection: Int,
        /** Fixed heading frame chosen at fire time — no per-frame spinning. */
        val frameIdx: Int,
    )

    companion object {
        private const val TAG = "SpriteOverlay"
        private const val DEFAULT_DURATION_S = 1.0f
        private const val MIN_DURATION_MS = 200L
        private const val TARGET_SIZE_PX = 220f
        private const val MIN_SCALE = 0.7f
        private const val MAX_SCALE = 1.4f
        private const val FRAME_COUNT = 16
        // S170 calibration (~/AI-Studio/map-sprites/demo.html, line 56):
        // `frameOffset = 11` was the calibrated value where the source frame
        // is the upright toward-viewer pose. We use that frame fixed across
        // all fires per operator: orientation stays put, size + path move.
        private const val FRONT_FRAME_INDEX = 11

        // Fraction of duration spent fading in (and again fading out). With
        // 0.15, a 2s dance fades for 0.3s on each end and plays full-alpha
        // for the middle 1.4s.
        private const val ALPHA_FADE_FRACTION = 0.15f

        private val SRC_RECT_REUSABLE = Rect()
        private val DST_RECT_REUSABLE = RectF()

        private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2.0)
            return r * 2 * asin(min(1.0, sqrt(a)))
        }
    }
}
