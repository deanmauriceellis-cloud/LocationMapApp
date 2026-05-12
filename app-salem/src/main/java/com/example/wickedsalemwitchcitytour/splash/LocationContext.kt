/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * S231 — splash decision-tree LocationContext.
 *
 * Mirrors cache-proxy/lib/admin-splash-tree.js buildContext() so the editor
 * preview pane and the on-device splash engine see the same shape. Authored
 * variants reference these slot names (e.g. {miles}, {place}, {compass}); the
 * SplashEngine fills them at speak-time.
 */
package com.example.wickedsalemwitchcitytour.splash

import android.location.Location
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal const val SPLASH_MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module LocationContext.kt"

/** Salem common — anchor for distance + bearing. */
const val SALEM_LAT = 42.5224
const val SALEM_LON = -70.8961

enum class Movement { STATIONARY, APPROACHING, DEPARTING, LATERAL, UNKNOWN }
enum class PlaceKind { IN_CITY, NEAR_CITY, IN_TOWN_ADJACENT_TO_SALEM, IN_COUNTY, OFFGRID }

/**
 * The full set of slot values used by the splash engine. `null` means the
 * slot has no meaningful value for this resolution (e.g. `town` is null when
 * the user isn't in a Salem-adjacent town); the slot-filler renders null as
 * empty string.
 */
data class LocationContext(
    val lat: Double,
    val lon: Double,
    val miles: Double,
    val milesInt: Int,
    val bearingUserToSalem: Int,
    val compass: String,           // "north" / "northeast" / etc
    val compassShort: String,      // "N" / "NE" / etc
    val movement: Movement,
    val placeKind: PlaceKind,
    val placeName: String?,        // raw resolver name (no county suffix)
    val placeAdmin: String?,       // state postal code
    val city: String?,
    val nearCity: String?,
    val town: String?,
    val county: String?,           // includes " County" suffix when set
    val state: String?,
    val stateLong: String?,
    val place: String?,            // best-resolved combined display label
)

object LocationContextBuilder {

    /**
     * Build a context for the given Location. `place` may be null if the
     * resolver hasn't completed (caller-managed); set after [PlaceResolver]
     * returns by calling [withPlace].
     */
    fun build(loc: Location, place: PlaceHit?): LocationContext {
        val miles = greatCircleMiles(loc.latitude, loc.longitude, SALEM_LAT, SALEM_LON)
        val userToSalem = bearingDeg(loc.latitude, loc.longitude, SALEM_LAT, SALEM_LON)
        val salemToUser = bearingDeg(SALEM_LAT, SALEM_LON, loc.latitude, loc.longitude)
        val compassShort = compassFromBearing(salemToUser)
        val movement = classifyMovement(
            userToSalemBearing = userToSalem,
            headingDeg = if (loc.hasBearing()) loc.bearing.toDouble() else null,
            speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
        )

        val placeKind  = place?.kind ?: PlaceKind.OFFGRID
        val placeName  = place?.name
        val placeAdmin = place?.admin

        val city      = if (placeKind == PlaceKind.IN_CITY)                  placeName else null
        val nearCity  = if (placeKind == PlaceKind.NEAR_CITY)                placeName else null
        val townSlot  = if (placeKind == PlaceKind.IN_TOWN_ADJACENT_TO_SALEM) placeName else null
        val county    = if (placeKind == PlaceKind.IN_COUNTY)                placeName?.let { "$it County" } else null

        val placeDisplay = when {
            placeName == null -> null
            placeKind == PlaceKind.IN_COUNTY -> placeAdmin?.let { "$placeName County, $it" } ?: "$placeName County"
            else -> placeAdmin?.let { "$placeName, $it" } ?: placeName
        }

        return LocationContext(
            lat = loc.latitude,
            lon = loc.longitude,
            miles = "%.1f".format(miles).toDouble(),
            milesInt = miles.roundToInt(),
            bearingUserToSalem = userToSalem.roundToInt(),
            compass = COMPASS_LONG[compassShort] ?: compassShort.lowercase(),
            compassShort = compassShort,
            movement = movement,
            placeKind = placeKind,
            placeName = placeName,
            placeAdmin = placeAdmin,
            city = city,
            nearCity = nearCity,
            town = townSlot,
            county = county,
            state = placeAdmin,
            stateLong = placeAdmin?.let { STATE_LONG[it] },
            place = placeDisplay,
        )
    }
}

/** Polygon-resolver output. See PlaceResolver. */
data class PlaceHit(val kind: PlaceKind, val name: String, val admin: String?)

// ──────────────────────────────────────────────────────────────────────────
// Geo math
// ──────────────────────────────────────────────────────────────────────────

private const val EARTH_RADIUS_MI = 3958.7613

internal fun greatCircleMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).let { it * it }
    return 2.0 * EARTH_RADIUS_MI * asin(sqrt(a))
}

internal fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val φ1 = Math.toRadians(lat1)
    val φ2 = Math.toRadians(lat2)
    val λ1 = Math.toRadians(lon1)
    val λ2 = Math.toRadians(lon2)
    val y = sin(λ2 - λ1) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(λ2 - λ1)
    val θ = atan2(y, x)
    val deg = Math.toDegrees(θ)
    return ((deg % 360) + 360) % 360
}

private fun compassFromBearing(bearingFromSalem: Double): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((bearingFromSalem / 45.0).roundToInt() % 8 + 8) % 8
    return dirs[idx]
}

private fun classifyMovement(
    userToSalemBearing: Double,
    headingDeg: Double?,
    speedMps: Double?,
): Movement {
    if (speedMps == null || speedMps < 0.5) return Movement.STATIONARY
    if (headingDeg == null) return Movement.UNKNOWN
    val delta = abs(((headingDeg - userToSalemBearing + 540.0) % 360.0) - 180.0)
    val towardness = 180.0 - delta
    return when {
        towardness >= 120 -> Movement.APPROACHING   // within ±60° of toward
        towardness <= 60  -> Movement.DEPARTING     // within ±60° of away
        else              -> Movement.LATERAL
    }
}

private val COMPASS_LONG = mapOf(
    "N" to "north", "NE" to "northeast", "E" to "east", "SE" to "southeast",
    "S" to "south", "SW" to "southwest", "W" to "west", "NW" to "northwest",
)

// State postal → long name. Used for the {state_long} slot.
private val STATE_LONG = mapOf(
    "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas",
    "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
    "DC" to "the District of Columbia", "FL" to "Florida", "GA" to "Georgia",
    "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana",
    "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana",
    "ME" to "Maine", "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan",
    "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana",
    "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey",
    "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina",
    "ND" to "North Dakota", "OH" to "Ohio", "OK" to "Oklahoma", "OR" to "Oregon",
    "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
    "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah",
    "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia",
    "WI" to "Wisconsin", "WY" to "Wyoming",
)
