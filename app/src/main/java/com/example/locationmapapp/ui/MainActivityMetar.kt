/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityMetar.kt"

/** METAR label zoom threshold — matches web app (labels at zoom ≥ 10, dots below). */
private const val METAR_LABEL_ZOOM = 10.0

internal fun MainActivity.addMetarMarker(station: com.example.locationmapapp.data.model.MetarStation) {
    val fltCatColor = when (station.flightCategory?.uppercase()) {
        "VFR"  -> Color.parseColor("#2E7D32")
        "MVFR" -> Color.parseColor("#1565C0")
        "IFR"  -> Color.parseColor("#C62828")
        "LIFR" -> Color.parseColor("#AD1457")
        else   -> Color.parseColor("#546E7A")
    }
    val showLabels = binding.mapView.zoomLevelDouble >= METAR_LABEL_ZOOM
    val m = Marker(binding.mapView).apply {
        position = GeoPoint(station.lat, station.lon)
        icon     = if (showLabels) buildMetarStationIcon(station, fltCatColor)
                   else MarkerIconHelper.dot(this@addMetarMarker, fltCatColor)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title    = "${station.stationId} — ${station.name ?: station.flightCategory ?: "?"}"
        snippet  = buildMetarSnippet(station)
        relatedObject = station
    }
    metarMarkers.add(m); binding.mapView.overlays.add(m); binding.mapView.invalidate()
}

internal fun MainActivity.buildMetarStationIcon(s: com.example.locationmapapp.data.model.MetarStation, color: Int): android.graphics.drawable.Drawable {
    val density = resources.displayMetrics.density
    val tempF = s.tempC?.let { "%.0f°".format(it * 9.0 / 5.0 + 32) } ?: "?"
    val spd = s.windSpeedKt
    val wind = if (spd != null && spd > 0) {
        val dir = s.windDirDeg?.let { windDirToArrow(it) } ?: ""
        val gust = s.windGustKt?.let { "G$it" } ?: ""
        "$dir${spd}$gust"
    } else "Calm"
    val wx = s.wxString ?: s.skyCover ?: ""

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        this.color = color
    }
    val smallPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9 * density
        typeface = android.graphics.Typeface.DEFAULT
        this.color = Color.parseColor("#333333")
    }

    val lines = listOf(tempF, wind, wx).filter { it.isNotBlank() }
    val lineHeight = textPaint.textSize + 2 * density
    val widths = lines.mapIndexed { i, line -> if (i == 0) textPaint.measureText(line) else smallPaint.measureText(line) }
    val maxW = (widths.maxOrNull() ?: 30f) + 8 * density
    val totalH = lineHeight * lines.size + 4 * density

    val bmp = android.graphics.Bitmap.createBitmap(maxW.toInt(), totalH.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    // Background
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.argb(200, 255, 255, 255)
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRoundRect(0f, 0f, maxW, totalH, 4 * density, 4 * density, bgPaint)
    // Border
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1 * density
    }
    canvas.drawRoundRect(0f, 0f, maxW, totalH, 4 * density, 4 * density, borderPaint)
    // Text
    lines.forEachIndexed { i, line ->
        val p = if (i == 0) textPaint else smallPaint
        val x = (maxW - p.measureText(line)) / 2
        val y = lineHeight * (i + 1)
        canvas.drawText(line, x, y, p)
    }

    return android.graphics.drawable.BitmapDrawable(resources, bmp)
}

internal fun MainActivity.windDirToArrow(deg: Int): String = when ((deg + 22) / 45 % 8) {
    0 -> "↓"; 1 -> "↙"; 2 -> "←"; 3 -> "↖"
    4 -> "↑"; 5 -> "↗"; 6 -> "→"; 7 -> "↘"
    else -> ""
}

internal fun MainActivity.buildPlaceSnippet(p: com.example.locationmapapp.data.model.PlaceResult) =
    listOfNotNull(p.address, p.phone, p.openingHours).joinToString("\n").ifBlank { p.category }

internal fun MainActivity.buildMetarSnippet(s: com.example.locationmapapp.data.model.MetarStation): String {
    val lines = mutableListOf<String>()

    // Temperature & dewpoint
    val tempF = s.tempC?.let { "%.0f°F".format(it * 9.0 / 5.0 + 32) }
    val dewF  = s.dewpointC?.let { "%.0f°F".format(it * 9.0 / 5.0 + 32) }
    if (tempF != null) {
        val dewPart = if (dewF != null) ", Dewpoint $dewF" else ""
        lines += "Temperature: $tempF$dewPart"
    }

    // Wind
    val spd2 = s.windSpeedKt
    val wind = if (spd2 != null && spd2 > 0) {
        val dirName = s.windDirDeg?.let { degreesToCompass(it) } ?: "Variable"
        val gust = s.windGustKt?.let { ", gusting to $it kt" } ?: ""
        "Wind: $dirName at ${spd2} kt$gust"
    } else "Wind: Calm"
    lines += wind

    // Visibility
    s.visibilityMiles?.let {
        val visStr = if (it >= 10.0) "10+ miles" else "${"%.0f".format(it)} miles"
        lines += "Visibility: $visStr"
    }

    // Sky condition
    s.skyCover?.let { cover ->
        val decoded = when (cover.uppercase()) {
            "CLR", "SKC" -> "Clear"
            "FEW"        -> "Few clouds"
            "SCT"        -> "Scattered clouds"
            "BKN"        -> "Broken clouds"
            "OVC"        -> "Overcast"
            else         -> cover
        }
        lines += "Sky: $decoded"
    }

    // Weather phenomena
    s.wxString?.let { lines += "Weather: ${decodeWx(it)}" }

    // Altimeter
    s.altimeterInHg?.let { lines += "Altimeter: ${"%.2f".format(it)} inHg" }

    // Sea level pressure
    s.slpMb?.let { lines += "Sea Level Pressure: ${"%.1f".format(it)} mb" }

    // Flight category
    s.flightCategory?.let { cat ->
        val decoded = when (cat.uppercase()) {
            "VFR"  -> "VFR (Visual Flight Rules)"
            "MVFR" -> "MVFR (Marginal VFR)"
            "IFR"  -> "IFR (Instrument Flight Rules)"
            "LIFR" -> "LIFR (Low IFR)"
            else   -> cat
        }
        lines += "Flight Category: $decoded"
    }

    // Observation time
    s.observationTime?.let {
        try {
            val instant = java.time.Instant.parse(it)
            val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            lines += "Observed: ${local.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}"
        } catch (_: Exception) {}
    }

    // Raw METAR at the bottom
    lines += "\n${s.rawMetar}"

    return lines.joinToString("\n")
}

internal fun MainActivity.degreesToCompass(deg: Int): String = when ((deg + 22) / 45 % 8) {
    0 -> "North"; 1 -> "Northeast"; 2 -> "East"; 3 -> "Southeast"
    4 -> "South"; 5 -> "Southwest"; 6 -> "West"; 7 -> "Northwest"
    else -> "${deg}°"
}

internal fun MainActivity.decodeWx(wx: String): String {
    val map = mapOf(
        "RA" to "Rain", "SN" to "Snow", "DZ" to "Drizzle",
        "FG" to "Fog", "BR" to "Mist", "HZ" to "Haze",
        "TS" to "Thunderstorm", "SH" to "Showers", "FZ" to "Freezing",
        "GR" to "Hail", "GS" to "Small hail", "PL" to "Ice pellets",
        "SG" to "Snow grains", "IC" to "Ice crystals",
        "UP" to "Unknown precip", "FU" to "Smoke",
        "VA" to "Volcanic ash", "DU" to "Dust", "SA" to "Sand",
        "SQ" to "Squall", "FC" to "Funnel cloud",
        "SS" to "Sandstorm", "DS" to "Duststorm"
    )
    val intensityMap = mapOf("-" to "Light ", "+" to "Heavy ", "VC" to "Vicinity ")
    var result = wx
    for ((code, name) in map) result = result.replace(code, name + " ")
    for ((code, prefix) in intensityMap) result = result.replace(code, prefix)
    return result.trim().replace("\\s+".toRegex(), " ")
}

fun MainActivity.clearMetarMarkers() {
    metarMarkers.forEach { binding.mapView.overlays.remove(it) }
    metarMarkers.clear(); binding.mapView.invalidate()
}

/** Rebuild METAR marker icons when crossing the zoom-10 label threshold. */
internal fun MainActivity.refreshMetarMarkerIcons() {
    val showLabels = binding.mapView.zoomLevelDouble >= METAR_LABEL_ZOOM
    for (marker in metarMarkers) {
        val station = marker.relatedObject as? com.example.locationmapapp.data.model.MetarStation ?: continue
        val fltCatColor = when (station.flightCategory?.uppercase()) {
            "VFR"  -> Color.parseColor("#2E7D32")
            "MVFR" -> Color.parseColor("#1565C0")
            "IFR"  -> Color.parseColor("#C62828")
            "LIFR" -> Color.parseColor("#AD1457")
            else   -> Color.parseColor("#546E7A")
        }
        marker.icon = if (showLabels) buildMetarStationIcon(station, fltCatColor)
                      else MarkerIconHelper.dot(this, fltCatColor)
    }
    binding.mapView.invalidate()
}

