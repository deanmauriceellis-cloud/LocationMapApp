/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

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
import com.example.locationmapapp.R
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.views.overlay.Marker
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityWeather.kt"

// =========================================================================
// WEATHER TOOLBAR ICON + DIALOG
// =========================================================================

/**
 * Update the Weather toolbar icon to reflect current conditions.
 * When alerts exist, draws the icon inside a red rounded-rect border.
 */
internal fun MainActivity.updateWeatherToolbarIcon(data: com.example.locationmapapp.data.model.WeatherData?) {
    if (data == null) return

    val current = data.current
    val iconRes = if (current != null) {
        WeatherIconHelper.drawableForCode(current.iconCode, current.isDaytime)
    } else {
        R.drawable.ic_wx_default
    }

    val hasAlerts = data.alerts.isNotEmpty()

    // Update ImageView (two-row toolbar)
    val iv = weatherIconView
    if (iv != null) {
        if (!hasAlerts) {
            iv.setImageResource(iconRes)
            iv.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            iv.background = run {
                val ripple = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                ContextCompat.getDrawable(this, ripple.resourceId)
            }
        } else {
            // Draw icon with red alert border
            val size = (24 * resources.displayMetrics.density).toInt()
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D32F2F")
                style = Paint.Style.STROKE
                strokeWidth = 2f * resources.displayMetrics.density
            }
            val inset = borderPaint.strokeWidth / 2f
            val r = 3f * resources.displayMetrics.density
            canvas.drawRoundRect(RectF(inset, inset, size - inset, size - inset), r, r, borderPaint)
            val drawable = ContextCompat.getDrawable(this, iconRes)
            if (drawable != null) {
                val pad = (4 * resources.displayMetrics.density).toInt()
                drawable.setBounds(pad, pad, size - pad, size - pad)
                drawable.draw(canvas)
            }
            iv.setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, bmp))
            iv.imageTintList = null
        }
        return
    }

    // Fallback: update MenuItem if ImageView not available
    val menuItem = weatherMenuItem ?: return
    if (!hasAlerts) {
        menuItem.setIcon(iconRes)
    } else {
        val size = (24 * resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D32F2F")
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
        }
        val inset = borderPaint.strokeWidth / 2f
        val r = 3f * resources.displayMetrics.density
        canvas.drawRoundRect(RectF(inset, inset, size - inset, size - inset), r, r, borderPaint)
        val drawable = ContextCompat.getDrawable(this, iconRes)
        if (drawable != null) {
            val pad = (4 * resources.displayMetrics.density).toInt()
            drawable.setBounds(pad, pad, size - pad, size - pad)
            drawable.draw(canvas)
        }
        menuItem.icon = android.graphics.drawable.BitmapDrawable(resources, bmp)
    }
}

/**
 * showWeatherDialog() — rich weather information dialog.
 * Shows current conditions, 48-hour hourly forecast strip,
 * 7-day daily outlook, and location-specific alerts.
 */
@SuppressLint("SetTextI18n")
internal fun MainActivity.showWeatherDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val rootScroll = android.widget.ScrollView(this).apply {
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    rootScroll.addView(root)

    // Loading state initially
    val loadingText = TextView(this).apply {
        text = "Loading weather..."
        setTextColor(Color.WHITE)
        textSize = 16f
        setPadding(0, dp(20), 0, dp(20))
    }
    root.addView(loadingText)

    val dialog = android.app.Dialog(this)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    dialog.setContentView(rootScroll)
    dialog.window?.apply {
        val dm = resources.displayMetrics
        setLayout((dm.widthPixels * 0.90).toInt(), (dm.heightPixels * 0.85).toInt())
        setBackgroundDrawableResource(android.R.color.transparent)
    }
    dialog.show()

    // Fetch weather async
    lifecycleScope.launch {
        val loc = viewModel.currentLocation.value?.point ?: GeoPoint(42.3601, -71.0589)
        val data = weatherViewModel.fetchWeatherDirectly(loc.latitude, loc.longitude)

        if (data == null) {
            loadingText.text = "Failed to load weather data."
            return@launch
        }

        // Update cached data for toolbar icon
        lastWeatherFetchTime = System.currentTimeMillis()

        root.removeAllViews()
        buildWeatherDialogContent(root, data, dp, dialog)
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.buildWeatherDialogContent(
    root: LinearLayout,
    data: com.example.locationmapapp.data.model.WeatherData,
    dp: (Int) -> Int,
    dialog: android.app.Dialog
) {
    val density = resources.displayMetrics.density

    // ── Header row ──────────────────────────────────────────────────────
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(8))
    }
    val headerIcon = ImageView(this).apply {
        val iconRes = if (data.current != null) WeatherIconHelper.drawableForCode(data.current.iconCode, data.current.isDaytime) else R.drawable.ic_wx_default
        setImageResource(iconRes)
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(0, 0, dp(8), 0) }
    }
    headerRow.addView(headerIcon)
    val headerTitle = TextView(this).apply {
        text = "Weather for ${data.location.city}, ${data.location.state}"
        setTextColor(Color.WHITE)
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    headerRow.addView(headerTitle)
    val closeBtn = TextView(this).apply {
        text = "X"
        setTextColor(Color.parseColor("#999999"))
        textSize = 18f
        setPadding(dp(8), 0, 0, 0)
        setOnClickListener { dialog.dismiss() }
    }
    headerRow.addView(closeBtn)
    root.addView(headerRow)

    // Divider helper
    fun addDivider() {
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#444444"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        })
    }

    addDivider()

    // ── Current conditions ──────────────────────────────────────────────
    val current = data.current
    if (current != null) {
        val currentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        // Large weather icon
        val wxIcon = ImageView(this).apply {
            setImageResource(WeatherIconHelper.drawableForCode(current.iconCode, current.isDaytime))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, 0, dp(12), 0) }
        }
        currentRow.addView(wxIcon)

        // Temperature + description column
        val tempCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tempText = TextView(this).apply {
            text = "${current.temperature ?: "?"}°F"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        tempCol.addView(tempText)
        val descText = TextView(this).apply {
            text = current.description
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
        }
        tempCol.addView(descText)
        // Feels like (wind chill or heat index)
        val feelsLike = current.windChill ?: current.heatIndex
        if (feelsLike != null && feelsLike != current.temperature) {
            val feelsText = TextView(this).apply {
                text = "Feels like ${feelsLike}°F"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 13f
            }
            tempCol.addView(feelsText)
        }
        currentRow.addView(tempCol)
        root.addView(currentRow)

        // Detail rows
        val detailLines = mutableListOf<String>()
        if (current.windDirection != null && current.windSpeed != null) {
            detailLines.add("Wind: ${current.windDirection} ${current.windSpeed} mph")
        }
        if (current.humidity != null) detailLines.add("Humidity: ${current.humidity}%")
        if (current.visibility != null) detailLines.add("Visibility: ${current.visibility} mi")
        if (current.dewpoint != null) detailLines.add("Dewpoint: ${current.dewpoint}°F")
        if (current.barometer != null) detailLines.add("Barometer: ${current.barometer} inHg")

        if (detailLines.isNotEmpty()) {
            // Show in two columns
            val row1 = detailLines.take(2).joinToString("   ")
            val row2 = if (detailLines.size > 2) detailLines.drop(2).take(2).joinToString("   ") else null
            val detail1 = TextView(this).apply {
                text = row1
                setTextColor(Color.parseColor("#BBBBBB"))
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
            }
            root.addView(detail1)
            if (row2 != null) {
                val detail2 = TextView(this).apply {
                    text = row2
                    setTextColor(Color.parseColor("#BBBBBB"))
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                }
                root.addView(detail2)
            }
        }
    }

    // ── Alerts section ──────────────────────────────────────────────────
    if (data.alerts.isNotEmpty()) {
        addDivider()
        for (alert in data.alerts) {
            val alertBg = when (alert.severity.lowercase()) {
                "extreme" -> "#4D1A1A"
                "severe" -> "#661A1A"
                "moderate" -> "#663D00"
                "minor" -> "#665500"
                else -> "#333333"
            }
            val alertContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor(alertBg))
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            }

            // Alert header (tap to expand)
            val alertHeader = TextView(this).apply {
                text = "\u26A0 ${alert.event}"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            alertContainer.addView(alertHeader)

            // Expandable detail (hidden by default)
            val alertDetail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(0, dp(6), 0, 0)
            }
            if (alert.headline.isNotBlank()) {
                alertDetail.addView(TextView(this).apply {
                    text = alert.headline
                    setTextColor(Color.parseColor("#DDDDDD"))
                    textSize = 12f
                    setPadding(0, 0, 0, dp(4))
                })
            }
            if (alert.description.isNotBlank()) {
                alertDetail.addView(TextView(this).apply {
                    text = alert.description.take(500)
                    setTextColor(Color.parseColor("#CCCCCC"))
                    textSize = 11f
                    setPadding(0, 0, 0, dp(4))
                })
            }
            if (alert.instruction.isNotBlank()) {
                alertDetail.addView(TextView(this).apply {
                    text = "What to do: ${alert.instruction.take(300)}"
                    setTextColor(Color.parseColor("#CCCCCC"))
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.ITALIC)
                    setPadding(0, 0, 0, dp(4))
                })
            }
            if (alert.expires.isNotBlank()) {
                val expFormatted = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                    val outFmt = java.text.SimpleDateFormat("EEE MMM d, h:mm a", java.util.Locale.US)
                    outFmt.format(sdf.parse(alert.expires)!!)
                } catch (_: Exception) { alert.expires }
                alertDetail.addView(TextView(this).apply {
                    text = "Expires: $expFormatted"
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 11f
                })
            }
            alertContainer.addView(alertDetail)

            // Toggle expand/collapse on tap
            alertContainer.setOnClickListener {
                alertDetail.visibility = if (alertDetail.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            root.addView(alertContainer)
        }
    }

    // ── 48-Hour Forecast Strip ──────────────────────────────────────────
    if (data.hourly.isNotEmpty()) {
        addDivider()
        root.addView(TextView(this).apply {
            text = "48-HOUR FORECAST"
            setTextColor(Color.parseColor("#999999"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })

        val scrollView = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val hourFmt = java.text.SimpleDateFormat("ha", java.util.Locale.US)

        for (hour in data.hourly.take(48)) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                val cellBg = if (hour.isDaytime) "#252525" else "#1E1E1E"
                setBackgroundColor(Color.parseColor(cellBg))
                setPadding(dp(6), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
            }

            // Time label
            val timeLabel = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                hourFmt.format(sdf.parse(hour.time)!!).lowercase()
            } catch (_: Exception) { hour.time.takeLast(8).take(5) }

            cell.addView(TextView(this).apply {
                text = timeLabel
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 10f
                gravity = android.view.Gravity.CENTER
            })

            // Weather icon
            cell.addView(ImageView(this).apply {
                setImageResource(WeatherIconHelper.drawableForCode(hour.iconCode, hour.isDaytime))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    gravity = android.view.Gravity.CENTER
                    setMargins(0, dp(3), 0, dp(3))
                }
            })

            // Temperature
            cell.addView(TextView(this).apply {
                text = "${hour.temperature}°"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            })

            // Precip probability
            if (hour.precipProbability > 0) {
                cell.addView(TextView(this).apply {
                    text = "${hour.precipProbability}%"
                    setTextColor(Color.parseColor("#64B5F6"))
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                })
            }

            strip.addView(cell)
        }
        scrollView.addView(strip)
        root.addView(scrollView)
    }

    // ── 7-Day Outlook ───────────────────────────────────────────────────
    if (data.daily.isNotEmpty()) {
        addDivider()
        root.addView(TextView(this).apply {
            text = "7-DAY OUTLOOK"
            setTextColor(Color.parseColor("#999999"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })

        // NWS daily forecast comes as day/night pairs — combine them
        val periods = data.daily
        var i = 0
        while (i < periods.size) {
            val dayPeriod = periods[i]
            val nightPeriod = if (i + 1 < periods.size && !periods[i + 1].isDaytime) periods[i + 1] else null

            // If first period is nighttime-only (Tonight), show it standalone
            if (!dayPeriod.isDaytime && i == 0) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(TextView(this).apply {
                    text = dayPeriod.name.take(5)
                    setTextColor(Color.parseColor("#CCCCCC"))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                row.addView(ImageView(this).apply {
                    setImageResource(WeatherIconHelper.drawableForCode(dayPeriod.iconCode, false))
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { setMargins(0, 0, dp(8), 0) }
                })
                row.addView(TextView(this).apply {
                    text = "—/${dayPeriod.temperature}°"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                row.addView(TextView(this).apply {
                    text = dayPeriod.shortForecast
                    setTextColor(Color.parseColor("#BBBBBB"))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (dayPeriod.precipProbability > 0) {
                    row.addView(TextView(this).apply {
                        text = " ${dayPeriod.precipProbability}%"
                        setTextColor(Color.parseColor("#64B5F6"))
                        textSize = 11f
                    })
                }
                root.addView(row)
                i++
                continue
            }

            // Day/Night pair
            val hi = if (dayPeriod.isDaytime) dayPeriod.temperature else nightPeriod?.temperature ?: dayPeriod.temperature
            val lo = if (dayPeriod.isDaytime) (nightPeriod?.temperature ?: "?") else dayPeriod.temperature
            val iconCode = if (dayPeriod.isDaytime) dayPeriod.iconCode else (nightPeriod?.iconCode ?: dayPeriod.iconCode)
            val forecast = if (dayPeriod.isDaytime) dayPeriod.shortForecast else (nightPeriod?.shortForecast ?: dayPeriod.shortForecast)
            val precip = maxOf(dayPeriod.precipProbability, nightPeriod?.precipProbability ?: 0)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            // Day name (abbreviated)
            val dayName = dayPeriod.name.let {
                when {
                    it.equals("Today", ignoreCase = true) -> "Today"
                    it.equals("Tonight", ignoreCase = true) -> "Tongt"
                    it.length > 5 -> it.take(3)
                    else -> it
                }
            }
            row.addView(TextView(this).apply {
                text = dayName
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            row.addView(ImageView(this).apply {
                setImageResource(WeatherIconHelper.drawableForCode(iconCode, dayPeriod.isDaytime))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { setMargins(0, 0, dp(8), 0) }
            })
            row.addView(TextView(this).apply {
                text = "${hi}°/${lo}°"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = forecast
                setTextColor(Color.parseColor("#BBBBBB"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (precip > 0) {
                row.addView(TextView(this).apply {
                    text = " ${precip}%"
                    setTextColor(Color.parseColor("#64B5F6"))
                    textSize = 11f
                })
            }
            root.addView(row)

            i += if (nightPeriod != null) 2 else 1
        }
    }

    // ── Footer ──────────────────────────────────────────────────────────
    addDivider()
    val fetchedFormatted = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val outFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        outFmt.format(sdf.parse(data.fetchedAt)!!)
    } catch (_: Exception) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val outFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
            outFmt.format(sdf.parse(data.fetchedAt)!!)
        } catch (_: Exception) { data.fetchedAt }
    }
    root.addView(TextView(this).apply {
        text = "Station: ${data.location.station} | Updated: $fetchedFormatted"
        setTextColor(Color.parseColor("#777777"))
        textSize = 11f
        gravity = android.view.Gravity.CENTER
        setPadding(0, dp(4), 0, 0)
    })
}

