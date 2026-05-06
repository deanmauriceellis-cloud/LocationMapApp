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
    )

    private val assetManager = context.assets
    private val spriteBitmaps = HashMap<String, Bitmap?>()

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
                    val bitmap = loadSprite(h.spriteId) ?: continue
                    activeSwoops.add(
                        ActiveSwoop(
                            lat = h.lat,
                            lng = h.lng,
                            bitmap = bitmap,
                            startMs = frameTimeMs,
                        )
                    )
                    lastFireMs[h.poiId] = frameTimeMs
                }
            }
        }

        if (activeSwoops.isNotEmpty()) {
            val it = activeSwoops.iterator()
            while (it.hasNext()) {
                val sw = it.next()
                if (frameTimeMs - sw.startMs > SWOOP_DURATION_MS) it.remove()
            }
        }
    }

    override fun draw(canvas: Canvas, camera: CameraState) {
        if (activeSwoops.isEmpty()) return

        for (sw in activeSwoops) {
            val elapsed = nowMs - sw.startMs
            val t = (elapsed.toFloat() / SWOOP_DURATION_MS).coerceIn(0f, 1f)
            // Bell-curve alpha: 0 → 1 → 0 over the second.
            val bell = sin(PI * t).toFloat()
            val alpha = (bell * 255f).toInt().coerceIn(0, 255)
            if (alpha <= 0) continue
            val scale = 0.6f + 0.4f * bell

            val proj = camera.project(sw.lat, sw.lng)
            // Float upward + small rightward drift so the sprite "moves" a bit
            // during its 1s peek instead of sitting still on top of the POI.
            val cx = proj[0] + 30f * t
            val cy = proj[1] - 80f - 40f * t

            val bw = sw.bitmap.width.toFloat()
            val bh = sw.bitmap.height.toFloat()
            // 16-frame WebP first-frame decode comes back at native sprite size
            // (~512x512). Scale to a fixed visible target then apply scale curve.
            val targetMax = TARGET_SIZE_PX
            val srcAspect = bh / bw
            val drawW = targetMax * scale
            val drawH = drawW * srcAspect

            val left = cx - drawW / 2f
            val top = cy - drawH / 2f
            val srcRect = SRC_RECT_REUSABLE.apply {
                set(0, 0, sw.bitmap.width, sw.bitmap.height)
            }
            val dstRect = DST_RECT_REUSABLE.apply {
                set(left, top, left + drawW, top + drawH)
            }
            paint.alpha = alpha
            canvas.drawBitmap(sw.bitmap, srcRect, dstRect, paint)
        }
    }

    private fun loadSprite(id: String): Bitmap? {
        if (spriteBitmaps.containsKey(id)) return spriteBitmaps[id]
        val bm = try {
            assetManager.open("sprites/$id.webp").use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "failed to load sprite '$id': ${e.message}")
            null
        }
        spriteBitmaps[id] = bm
        if (bm != null) {
            DebugLogger.i(TAG, "loaded sprite '$id' = ${bm.width}x${bm.height}")
        }
        return bm
    }

    private data class ActiveSwoop(
        val lat: Double,
        val lng: Double,
        val bitmap: Bitmap,
        val startMs: Long,
    )

    companion object {
        private const val TAG = "SpriteOverlay"
        private const val SWOOP_DURATION_MS = 1000L
        private const val TARGET_SIZE_PX = 220f

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
