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
    private const val MAX_CACHE_SIZE = 500

    // LRU cache: access-order LinkedHashMap evicts oldest entries beyond MAX_CACHE_SIZE
    private val cache = object : LinkedHashMap<String, BitmapDrawable>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BitmapDrawable>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    // ── Category → (drawableRes, tintColor) ───────────────────────────────────
    private val CATEGORY_MAP = mapOf(
        // ── System markers ──────────────────────────────────────────────────
        "gps"            to Pair(R.drawable.ic_gps,           Color.parseColor("#37474F")),
        "metar"          to Pair(R.drawable.ic_metar,         Color.parseColor("#1B5E20")),
        "transit_rail"   to Pair(R.drawable.ic_transit_rail,  Color.parseColor("#0277BD")),
        "bus_stop"       to Pair(R.drawable.ic_bus,           Color.parseColor("#1565C0")),
        "bus_station"    to Pair(R.drawable.ic_bus,           Color.parseColor("#1565C0")),
        "camera"         to Pair(R.drawable.ic_camera,        Color.parseColor("#455A64")),
        "search"         to Pair(R.drawable.ic_search,        Color.parseColor("#4A148C")),
        "weather_alert"  to Pair(R.drawable.ic_weather_alert, Color.parseColor("#006064")),
        "record_gps"     to Pair(R.drawable.ic_record_gps,    Color.parseColor("#C62828")),
        "aircraft"       to Pair(R.drawable.ic_aircraft,      Color.parseColor("#1565C0")),
        "crosshair"      to Pair(R.drawable.ic_crosshair,     Color.parseColor("#FF6D00")),

        // ── Food & Drink (#BF360C) ──────────────────────────────────────────
        "restaurant"     to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "fast_food"      to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "cafe"           to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "bar"            to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "pub"            to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "ice_cream"      to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),

        // ── Fuel & Charging (#E65100) ───────────────────────────────────────
        "fuel"               to Pair(R.drawable.ic_gas_station, Color.parseColor("#E65100")),
        "gas_station"        to Pair(R.drawable.ic_gas_station, Color.parseColor("#E65100")),
        "charging_station"   to Pair(R.drawable.ic_gas_station, Color.parseColor("#E65100")),

        // ── Transit (#0277BD) — POI markers ─────────────────────────────────
        "station"        to Pair(R.drawable.ic_transit_rail,  Color.parseColor("#0277BD")),

        // ── Civic & Gov (#1A237E) ───────────────────────────────────────────
        "civic"          to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "government"     to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "townhall"       to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "courthouse"     to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "post_office"    to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),

        // ── Parks & Rec (#2E7D32) ───────────────────────────────────────────
        "park"               to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "nature_reserve"     to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "playground"         to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "pitch"              to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "swimming_pool"      to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),

        // ── Shopping (#F57F17) ──────────────────────────────────────────────
        "supermarket"        to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "convenience"        to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "mall"               to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "department_store"   to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "clothes"            to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),

        // ── Healthcare (#D32F2F) ────────────────────────────────────────────
        "hospital"       to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "pharmacy"       to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "clinic"         to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "dentist"        to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "doctors"        to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "veterinary"     to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),

        // ── Education (#5D4037) ─────────────────────────────────────────────
        "school"         to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "library"        to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "college"        to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "university"     to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),

        // ── Lodging (#7B1FA2) ───────────────────────────────────────────────
        "hotel"          to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),
        "motel"          to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),
        "hostel"         to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),

        // ── Parking (#455A64) ───────────────────────────────────────────────
        "parking"        to Pair(R.drawable.ic_poi,           Color.parseColor("#455A64")),

        // ── Finance (#00695C) ───────────────────────────────────────────────
        "bank"           to Pair(R.drawable.ic_poi,           Color.parseColor("#00695C")),
        "atm"            to Pair(R.drawable.ic_poi,           Color.parseColor("#00695C")),

        // ── Places of Worship (#4E342E) ─────────────────────────────────────
        "place_of_worship" to Pair(R.drawable.ic_poi,         Color.parseColor("#4E342E")),

        // ── Tourism & History (#FF6F00) ─────────────────────────────────────
        "museum"         to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "attraction"     to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "viewpoint"      to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "memorial"       to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "monument"       to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),

        // ── Emergency Svc (#B71C1C) ─────────────────────────────────────────
        "police"         to Pair(R.drawable.ic_poi,           Color.parseColor("#B71C1C")),
        "fire_station"   to Pair(R.drawable.ic_poi,           Color.parseColor("#B71C1C")),

        // ── Auto Services (#37474F) ─────────────────────────────────────────
        "car_repair"     to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "car_wash"       to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "car_rental"     to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "tyres"          to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),

        // ── Entertainment (#00838F) ─────────────────────────────────────────
        "fitness_centre" to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "sports_centre"  to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "golf_course"    to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "marina"         to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "stadium"        to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
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

    /**
     * Labeled POI marker for high zoom levels (≥18): category type above the dot, name below.
     * Layout (top to bottom): category label → dot → name label
     */
    fun labeledDot(context: Context, category: String, name: String): BitmapDrawable {
        val (_, color) = CATEGORY_MAP[category.lowercase()] ?: DEFAULT
        val density = context.resources.displayMetrics.density

        // Humanize category: "fast_food" → "Fast Food"
        val typeLabel = category.replace('_', ' ')
            .split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val nameLabel = if (name.isNotBlank()) name else ""

        val key = "ldot|$color|$typeLabel|$nameLabel"
        cache[key]?.let { return it }

        val dotSize = (6 * density).toInt().coerceAtLeast(6)

        val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 10 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.DKGRAY
            textSize = 10 * density
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val typeW = if (typeLabel.isNotEmpty()) typePaint.measureText(typeLabel).toInt() else 0
        val nameW = if (nameLabel.isNotEmpty()) namePaint.measureText(nameLabel).toInt() else 0
        val typeH = if (typeLabel.isNotEmpty()) (typePaint.textSize + 2 * density).toInt() else 0
        val nameH = if (nameLabel.isNotEmpty()) (namePaint.textSize + 2 * density).toInt() else 0
        val gap = (2 * density).toInt()

        val totalW = maxOf(dotSize, typeW, nameW) + (8 * density).toInt()
        val totalH = typeH + gap + dotSize + gap + nameH
        val cx = totalW / 2f

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Category type label (above dot)
        if (typeLabel.isNotEmpty()) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; alpha = 210; style = Paint.Style.FILL
            }
            val tw = typePaint.measureText(typeLabel)
            val padH = 3 * density
            val rect = RectF(cx - tw / 2 - padH, 0f, cx + tw / 2 + padH, typeH.toFloat())
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
            canvas.drawText(typeLabel, cx, typeH - 4 * density, typePaint)
        }

        // Dot
        val dotCy = typeH + gap + dotSize / 2f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; this.color = color; alpha = 160
        }
        canvas.drawCircle(cx, dotCy, dotSize / 2f, dotPaint)
        dotPaint.alpha = 255
        canvas.drawCircle(cx, dotCy, dotSize * 0.175f, dotPaint)

        // Name label (below dot)
        if (nameLabel.isNotEmpty()) {
            val nameTop = typeH + gap + dotSize + gap
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; alpha = 210; style = Paint.Style.FILL
            }
            val nw = namePaint.measureText(nameLabel)
            val padH = 3 * density
            val rect = RectF(cx - nw / 2 - padH, nameTop.toFloat(), cx + nw / 2 + padH, (nameTop + nameH).toFloat())
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
            canvas.drawText(nameLabel, cx, nameTop + nameH - 4 * density, namePaint)
        }

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /**
     * Icon with a small directional arrow on top, rotated to [bearingDeg].
     * The arrow sits just above the icon so direction of travel is visible at a glance.
     */
    fun withArrow(context: Context, resId: Int, sizeDp: Int, tintColor: Int, bearingDeg: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val iconPx = (sizeDp * density).toInt()
        val arrowSize = (8 * density).toInt()          // arrow height in px
        val gap = (2 * density).toInt()                // space between arrow and icon
        val totalW = iconPx + arrowSize * 2            // wide enough for rotated arrow
        val totalH = iconPx + arrowSize + gap

        val key = "arrow|$resId|$iconPx|${tintColor}|$bearingDeg"
        cache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw the base icon centered horizontally, at the bottom
        val iconLeft = (totalW - iconPx) / 2
        val iconTop = arrowSize + gap
        val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
        DrawableCompat.setTint(drawable, tintColor)
        drawable.setBounds(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
        drawable.draw(canvas)

        // Draw rotated arrow above the icon
        val arrowCx = totalW / 2f
        val arrowCy = arrowSize.toFloat()
        canvas.save()
        canvas.rotate(bearingDeg.toFloat(), arrowCx, arrowCy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(arrowCx, arrowCy - arrowSize * 0.7f)          // tip (points up = north = 0°)
            lineTo(arrowCx - arrowSize * 0.4f, arrowCy + arrowSize * 0.3f) // bottom-left
            lineTo(arrowCx + arrowSize * 0.4f, arrowCy + arrowSize * 0.3f) // bottom-right
            close()
        }
        canvas.drawPath(path, paint)
        canvas.restore()

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /**
     * Render an aircraft marker: rotated airplane icon pointing to [headingDeg],
     * callsign label, vertical rate indicator (arrow up/down/dash), and an optional
     * thick red circle when [spi] (Special Purpose Indicator / emergency) is active.
     *
     * Layout (top to bottom):
     *   callsign + vert indicator text
     *   rotated airplane icon (with optional red ring)
     */
    fun aircraftMarker(
        context: Context,
        headingDeg: Int,
        tintColor: Int,
        callsign: String?,
        verticalRate: Double?,   // m/s — positive=climb, negative=descend
        spi: Boolean
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val iconPx = (24 * density).toInt()
        val spiStroke = (3 * density).toInt()       // thick red ring
        val spiPad = if (spi) spiStroke + (2 * density).toInt() else 0
        val iconArea = iconPx + spiPad * 2           // icon + ring space

        // Vertical-rate indicator character
        val vertChar = when {
            verticalRate == null      -> ""
            verticalRate > 0.5        -> " \u2191"   // ↑ climbing
            verticalRate < -0.5       -> " \u2193"   // ↓ descending
            else                      -> " \u2014"   // — level
        }
        val label = (callsign?.ifBlank { null } ?: "") + vertChar

        // Measure text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textWidth = if (label.isNotEmpty()) textPaint.measureText(label).toInt() + (4 * density).toInt() else 0
        val textHeight = if (label.isNotEmpty()) (textPaint.textSize + 4 * density).toInt() else 0
        val textGap = if (label.isNotEmpty()) (2 * density).toInt() else 0

        val totalW = maxOf(iconArea, textWidth)
        val totalH = textHeight + textGap + iconArea

        val key = "acm|$headingDeg|$tintColor|${label}|$spi"
        cache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = totalW / 2f

        // ── Draw label (callsign + vert indicator) ──────────────────────────
        if (label.isNotEmpty()) {
            // Background pill
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 200
                style = Paint.Style.FILL
            }
            val tw = textPaint.measureText(label)
            val padH = 3 * density
            val padV = 1 * density
            val rect = RectF(cx - tw / 2 - padH, 0f, cx + tw / 2 + padH, textHeight.toFloat())
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
            canvas.drawText(label, cx, textHeight - 4 * density, textPaint)
        }

        // ── Draw SPI emergency ring ─────────────────────────────────────────
        val iconCx = cx
        val iconCy = textHeight + textGap + iconArea / 2f
        if (spi) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = spiStroke.toFloat()
            }
            val ringRadius = iconArea / 2f - spiStroke / 2f
            canvas.drawCircle(iconCx, iconCy, ringRadius, ringPaint)
        }

        // ── Draw rotated airplane icon ──────────────────────────────────────
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_aircraft)!!.mutate()
        DrawableCompat.setTint(drawable, tintColor)

        canvas.save()
        canvas.rotate(headingDeg.toFloat(), iconCx, iconCy)
        val halfIcon = iconPx / 2
        drawable.setBounds(
            (iconCx - halfIcon).toInt(), (iconCy - halfIcon).toInt(),
            (iconCx + halfIcon).toInt(), (iconCy + halfIcon).toInt()
        )
        drawable.draw(canvas)
        canvas.restore()

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    fun clearCache() = cache.clear()
}
