package com.example.locationmapapp.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.locationmapapp.R
import com.example.locationmapapp.util.DebugLogger

/**
 * MarkerIconHelper
 *
 * Converts VectorDrawable resources into BitmapDrawable instances sized
 * and tinted for OSMDroid Marker use. Results are cached by (resId, sizeDp, colorInt)
 * to avoid repeated rasterisation on every marker add.
 *
 * Design intent: icons appear as clean, minimal glyphs directly on the map surface —
 * no filled circles, no drop shadows. The icon IS the marker.
 */
object MarkerIconHelper {

    private const val TAG = "MarkerIconHelper"

    // Cache key: resId|sizePx|color
    private val cache = HashMap<String, BitmapDrawable>()

    // ── Category → (drawableRes, tintColor) ───────────────────────────────────
    private val CATEGORY_MAP = mapOf(
        "gps"           to Pair(R.drawable.ic_gps,           Color.parseColor("#37474F")),
        "metar"         to Pair(R.drawable.ic_metar,         Color.parseColor("#1B5E20")),
        "earthquake"    to Pair(R.drawable.ic_earthquake,    Color.parseColor("#B71C1C")),
        "gas_station"   to Pair(R.drawable.ic_gas_station,   Color.parseColor("#E65100")),
        "fuel"          to Pair(R.drawable.ic_gas_station,   Color.parseColor("#E65100")),
        "restaurant"    to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "cafe"          to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "fast_food"     to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "park"          to Pair(R.drawable.ic_park,          Color.parseColor("#2E7D32")),
        "nature_reserve" to Pair(R.drawable.ic_park,          Color.parseColor("#2E7D32")),
        "transit_rail"  to Pair(R.drawable.ic_transit_rail,  Color.parseColor("#0277BD")),
        "bus_stop"      to Pair(R.drawable.ic_bus,           Color.parseColor("#1565C0")),
        "bus_station"   to Pair(R.drawable.ic_bus,           Color.parseColor("#1565C0")),
        "camera"        to Pair(R.drawable.ic_camera,        Color.parseColor("#455A64")),
        "civic"         to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "government"    to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "townhall"      to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "search"        to Pair(R.drawable.ic_search,        Color.parseColor("#4A148C")),
        "weather_alert" to Pair(R.drawable.ic_weather_alert, Color.parseColor("#006064")),
        "record_gps"    to Pair(R.drawable.ic_record_gps,    Color.parseColor("#C62828")),
    )

    // Default fallback
    private val DEFAULT = Pair(R.drawable.ic_poi, Color.parseColor("#6A1B9A"))

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Get a marker icon for a named category.
     * @param sizeDp  Icon size in density-independent pixels (default 28dp — visible but not obstructing)
     */
    fun forCategory(context: Context, category: String, sizeDp: Int = 28): BitmapDrawable {
        val (resId, color) = CATEGORY_MAP[category.lowercase()] ?: DEFAULT
        return get(context, resId, sizeDp, color)
    }

    /**
     * Get a marker icon directly from a drawable resource, with optional tint.
     */
    fun get(context: Context, resId: Int, sizeDp: Int = 28, tintColor: Int? = null): BitmapDrawable {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt()
        val key = "$resId|$px|${tintColor ?: 0}"
        cache[key]?.let { return it }

        return try {
            val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
            tintColor?.let { DrawableCompat.setTint(drawable, it) }
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            val result = BitmapDrawable(context.resources, bitmap)
            cache[key] = result
            result
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to rasterize icon resId=$resId", e)
            // Return a 1x1 transparent fallback rather than crashing
            BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
    }

    /**
     * Tiny colored dot for POI markers: filled circle with a darker center point.
     * Fixed 5x5 dp (scales with density) — minimal footprint at any zoom level.
     */
    fun dot(context: Context, category: String): BitmapDrawable {
        val (_, color) = CATEGORY_MAP[category.lowercase()] ?: DEFAULT
        return dot(context, color)
    }

    fun dot(context: Context, color: Int): BitmapDrawable {
        val key = "dot|$color"
        cache[key]?.let { return it }

        val sizePx = (5 * context.resources.displayMetrics.density).toInt().coerceAtLeast(5)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer circle — semi-transparent fill
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 160
        canvas.drawCircle(cx, cy, cx, paint)

        // Center dot — fully opaque
        paint.alpha = 255
        canvas.drawCircle(cx, cy, cx * 0.35f, paint)

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    fun clearCache() = cache.clear()
}
