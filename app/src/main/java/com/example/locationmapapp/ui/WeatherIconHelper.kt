/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import com.example.locationmapapp.R

/**
 * WeatherIconHelper — maps NWS icon codes to vector drawable resources.
 *
 * NWS icon URLs contain a code like "sct", "rain_showers", "tsra", etc.
 * The proxy extracts just the code portion. This helper maps it to
 * the appropriate drawable, accounting for day/night variants.
 */
object WeatherIconHelper {

    /**
     * Returns the drawable resource ID for a given NWS weather icon code.
     * @param code The extracted NWS icon code (e.g., "skc", "sct", "rain")
     * @param isDaytime true for day icons, false for night icons
     */
    fun drawableForCode(code: String, isDaytime: Boolean): Int {
        return when {
            // Clear sky
            code == "skc" -> if (isDaytime) R.drawable.ic_wx_clear_day else R.drawable.ic_wx_clear_night
            // Few clouds
            code == "few" -> if (isDaytime) R.drawable.ic_wx_few_clouds_day else R.drawable.ic_wx_few_clouds_night
            // Scattered clouds / Partly cloudy
            code == "sct" -> if (isDaytime) R.drawable.ic_wx_partly_cloudy_day else R.drawable.ic_wx_partly_cloudy_night
            // Broken clouds / Mostly cloudy
            code == "bkn" -> R.drawable.ic_wx_mostly_cloudy
            // Overcast
            code == "ovc" -> R.drawable.ic_wx_overcast
            // Rain
            code == "rain" -> R.drawable.ic_wx_rain
            // Rain showers
            code.startsWith("rain_showers") -> R.drawable.ic_wx_showers
            // Thunderstorms
            code.startsWith("tsra") -> R.drawable.ic_wx_thunderstorm
            // Snow
            code.startsWith("snow") -> R.drawable.ic_wx_snow
            // Sleet / rain+snow mix
            code == "sleet" || code == "rain_snow" -> R.drawable.ic_wx_sleet
            // Freezing rain
            code.startsWith("fzra") -> R.drawable.ic_wx_freezing_rain
            // Fog
            code == "fog" -> R.drawable.ic_wx_fog
            // Haze / smoke / dust
            code == "haze" || code == "smoke" || code == "dust" -> R.drawable.ic_wx_haze
            // Wind
            code.startsWith("wind") -> R.drawable.ic_wx_wind
            // Hot
            code == "hot" -> R.drawable.ic_wx_hot
            // Cold
            code == "cold" -> R.drawable.ic_wx_cold
            // Tornado
            code == "tornado" -> R.drawable.ic_wx_tornado
            // Hurricane
            code == "hurricane" -> R.drawable.ic_wx_hurricane
            // Blizzard
            code == "blizzard" -> R.drawable.ic_wx_snow
            // Default fallback
            else -> R.drawable.ic_wx_default
        }
    }
}
