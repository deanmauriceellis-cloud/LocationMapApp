/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.wickedsalemwitchcitytour.R
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityDialogs.kt"

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showLegendDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Map Legend"
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), dp(12), dp(12), dp(8))
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(titleText)
        addView(closeBtn)
    }

    // ── Scrollable content ──
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), 0, dp(16), dp(16))
    }

    fun sectionHeader(title: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(6))
        })
    }

    fun legendRow(icon: android.graphics.drawable.Drawable, label: String, note: String? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val iconView = android.widget.ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(12)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
        row.addView(iconView)
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this@showLegendDialog).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        if (note != null) {
            textCol.addView(TextView(this@showLegendDialog).apply {
                text = note
                textSize = 11f
                setTextColor(Color.parseColor("#9E9E9E"))
            })
        }
        row.addView(textCol)
        content.addView(row)
    }

    fun colorDot(color: Int, sizeDp: Int = 16): android.graphics.drawable.BitmapDrawable {
        val px = dp(sizeDp)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; this.color = color; alpha = 180
        }
        c.drawCircle(px / 2f, px / 2f, px / 2f, p)
        p.alpha = 255
        c.drawCircle(px / 2f, px / 2f, px * 0.2f, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    fun colorRect(borderColor: Int, sizeDp: Int = 16): android.graphics.drawable.BitmapDrawable {
        val px = dp(sizeDp)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); this.color = borderColor
        }
        val r = dp(3).toFloat()
        c.drawRoundRect(android.graphics.RectF(dp(2).toFloat(), dp(2).toFloat(),
            (px - dp(2)).toFloat(), (px - dp(2)).toFloat()), r, r, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    fun gradientBar(): android.graphics.drawable.BitmapDrawable {
        val w = dp(32); val h = dp(16)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val colors = intArrayOf(
            Color.parseColor("#0000FF"), Color.parseColor("#00FF00"),
            Color.parseColor("#FFFF00"), Color.parseColor("#FF8C00"), Color.parseColor("#FF0000")
        )
        val grad = LinearGradient(0f, 0f, w.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
        c.drawRoundRect(android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), dp(3).toFloat(), dp(3).toFloat(), p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    fun colorLine(lineColor: Int): android.graphics.drawable.BitmapDrawable {
        val w = dp(32); val h = dp(16)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
            this.color = lineColor; strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(dp(2).toFloat(), h / 2f, (w - dp(2)).toFloat(), h / 2f, p)
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // YOUR LOCATION
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("YOUR LOCATION")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_gps, 24, Color.parseColor("#37474F")),
        "Your GPS Location")

    // ═══════════════════════════════════════════════════════════════════════
    // POINTS OF INTEREST
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("POINTS OF INTEREST")
    for (cat in com.example.wickedsalemwitchcitytour.ui.menu.PoiCategories.ALL) {
        legendRow(colorDot(cat.color), cat.label)
    }
    legendRow(colorDot(Color.TRANSPARENT, 1), "", "Names shown at zoom \u2265 18")

    // ═══════════════════════════════════════════════════════════════════════
    // WEATHER
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("WEATHER")
    legendRow(colorRect(Color.parseColor("#2E7D32")), "METAR — VFR", "Visual Flight Rules")
    legendRow(colorRect(Color.parseColor("#1565C0")), "METAR — MVFR", "Marginal VFR")
    legendRow(colorRect(Color.parseColor("#C62828")), "METAR — IFR", "Instrument Flight Rules")
    legendRow(colorRect(Color.parseColor("#AD1457")), "METAR — LIFR", "Low IFR")
    legendRow(gradientBar(), "Radar", "Precipitation intensity")

    // ═══════════════════════════════════════════════════════════════════════
    // TRANSIT VEHICLES
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("TRANSIT VEHICLES")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#6A1B9A")),
        "Commuter Rail", "Purple")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#C62828")),
        "Red Line")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#E65100")),
        "Orange Line")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#1565C0")),
        "Blue Line")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#2E7D32")),
        "Green Line")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_transit_rail, 24, Color.parseColor("#546E7A")),
        "Silver Line")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_bus, 24, Color.parseColor("#00695C")),
        "Bus", "Arrow shows direction of travel")

    // ═══════════════════════════════════════════════════════════════════════
    // TRANSIT STOPS
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("TRANSIT STOPS")
    legendRow(MarkerIconHelper.stationIcon(this, Color.parseColor("#37474F")),
        "Train Station")
    legendRow(MarkerIconHelper.busStopIcon(this, Color.parseColor("#00695C")),
        "Bus Stop", "Visible at zoom \u2265 15")

    // ═══════════════════════════════════════════════════════════════════════
    // AIRCRAFT
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("AIRCRAFT")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#78909C")),
        "Ground", "Gray")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#2E7D32")),
        "< 5,000 ft", "Green — low altitude")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#1565C0")),
        "5,000 – 20,000 ft", "Blue — mid altitude")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_aircraft, 24, Color.parseColor("#6A1B9A")),
        "> 20,000 ft", "Purple — high altitude")
    legendRow(colorRect(Color.RED), "Emergency (SPI)", "Special Purpose Indicator")
    legendRow(colorLine(Color.parseColor("#1565C0")), "Flight trail", "Altitude-colored polyline")

    // ═══════════════════════════════════════════════════════════════════════
    // CAMERAS
    // ═══════════════════════════════════════════════════════════════════════
    sectionHeader("CAMERAS")
    legendRow(MarkerIconHelper.get(this, R.drawable.ic_camera, 24, Color.parseColor("#455A64")),
        "Webcam", "Tap to preview, view live")

    // ── Assemble ──
    val scrollView = android.widget.ScrollView(this).apply {
        addView(content)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        addView(header)
        addView(scrollView)
    }

    dialog.setContentView(container)
    dialog.window?.let { win ->
        val dm = resources.displayMetrics
        win.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.85).toInt())
        win.setBackgroundDrawableResource(android.R.color.transparent)
        win.setGravity(android.view.Gravity.CENTER)
    }
    dialog.show()
}

