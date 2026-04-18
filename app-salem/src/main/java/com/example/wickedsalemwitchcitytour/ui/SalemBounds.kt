/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import org.osmdroid.util.GeoPoint

/**
 * Salem geographic bounds + canonical fallback location.
 *
 * Bbox matches the tile-download `BBOX_FULL_SALEM` in
 * `tools/download_salem_tiles.py` — the area our bundled z16/z17 tiles
 * cover. If the device GPS reports a fix outside this envelope, the app
 * treats the user as parked at the Bewitched / Samantha statue
 * (id=`bewitched_sculpture_samantha_statue`, POI coords from the bundled
 * Room DB). This keeps the map, narration, proximity queries, and the
 * low-zoom location ball all grounded in downtown Salem regardless of
 * where the operator / user physically is.
 *
 * The Willows peninsula tip (north of 42.545) is out-of-bundle — parking
 * lot item #39. Extend the northern edge here when that bbox grows.
 */
object SalemBounds {

    const val SAMANTHA_LAT = 42.5213319
    const val SAMANTHA_LON = -70.8958518
    val SAMANTHA_STATUE: GeoPoint = GeoPoint(SAMANTHA_LAT, SAMANTHA_LON)

    const val SALEM_BBOX_NORTH = 42.545
    const val SALEM_BBOX_SOUTH = 42.475
    const val SALEM_BBOX_WEST = -70.958
    const val SALEM_BBOX_EAST = -70.835

    fun isInSalemBbox(lat: Double, lng: Double): Boolean =
        lat in SALEM_BBOX_SOUTH..SALEM_BBOX_NORTH &&
            lng in SALEM_BBOX_WEST..SALEM_BBOX_EAST

    fun isInSalemBbox(point: GeoPoint): Boolean =
        isInSalemBbox(point.latitude, point.longitude)

    /** Returns the input if inside Salem, otherwise the Samantha statue. */
    fun clampToSalem(point: GeoPoint): GeoPoint =
        if (isInSalemBbox(point)) point else SAMANTHA_STATUE

    fun clampToSalem(lat: Double, lng: Double): GeoPoint =
        if (isInSalemBbox(lat, lng)) GeoPoint(lat, lng) else SAMANTHA_STATUE
}
