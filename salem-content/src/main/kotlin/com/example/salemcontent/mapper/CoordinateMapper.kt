/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.mapper

/**
 * Maps Salem simulation grid coordinates (2000x2000) to real-world GPS lat/lng.
 *
 * The grid represents 1692 Salem Village (modern Danvers, MA).
 * Grid scale: 1 unit ≈ 26 feet ≈ 7.9248 meters.
 *
 * Anchor points (from master plan):
 *   Meetinghouse  (1000, 1200) → GPS 42.5630, -70.9510
 *   Nurse Homestead (1100, 1260) → GPS 42.5630, -70.9380
 *
 * Note: These map 1692 VILLAGE locations (modern Danvers).
 * Modern Salem tourist sites have direct GPS coordinates curated separately.
 */
object CoordinateMapper {

    // Anchor: Meetinghouse
    private const val ANCHOR_X = 1000
    private const val ANCHOR_Y = 1200
    private const val ANCHOR_LAT = 42.5630
    private const val ANCHOR_LNG = -70.9510

    // Derived from Meetinghouse → Nurse Homestead offset:
    // dx=100, dy=60 → dLng=0.0130, dLat=0.0
    // So: 1 grid X unit = 0.0130/100 = 0.000130 degrees longitude
    //     1 grid Y unit = need second reference point for latitude

    // Using grid scale: 1 unit ≈ 7.9248m
    // At latitude 42.56°:
    //   1° lat ≈ 111,132m → 1 grid unit = 7.9248/111132 ≈ 0.0000713° lat
    //   1° lng ≈ 111,132 * cos(42.56°) ≈ 81,860m → 1 grid unit = 7.9248/81860 ≈ 0.0000968° lng
    private const val GRID_TO_LAT = 0.0000713   // degrees per grid unit (Y increases → lat increases)
    private const val GRID_TO_LNG = 0.0000968   // degrees per grid unit (X increases → lng increases/east)

    data class GpsCoord(val lat: Double, val lng: Double)

    /**
     * Convert grid (x, y) to GPS (lat, lng).
     * Y increases northward (higher Y = higher latitude).
     * X increases eastward (higher X = less negative longitude).
     */
    fun gridToGps(x: Int, y: Int): GpsCoord {
        val lat = ANCHOR_LAT + (y - ANCHOR_Y) * GRID_TO_LAT
        val lng = ANCHOR_LNG + (x - ANCHOR_X) * GRID_TO_LNG
        return GpsCoord(lat, lng)
    }
}
