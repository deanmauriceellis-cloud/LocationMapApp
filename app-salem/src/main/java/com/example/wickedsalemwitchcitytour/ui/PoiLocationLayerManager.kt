/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module PoiLocationLayerManager.kt"

/**
 * S162 — POI location layer picker.
 *
 * Mirrors [TileSourceManager]'s shape so the layers popup can render the
 * basemap and POI-source selectors with identical patterns. Picks which
 * coordinate set the map renders for each POI:
 *   - CURRENT  : live `lat`/`lng` (the coords the app has always used)
 *   - PROPOSED : `lat_proposed`/`lng_proposed` produced by the offline
 *                TigerLine/MassGIS verifier (cache-proxy/scripts/poi-verify-*)
 *   - BOTH     : both sets, drawn together for visual diff (admin-only mode)
 *
 * Until the verifier populates proposed coords, only CURRENT will have data
 * to render — PROPOSED/BOTH are scaffolding for the review workflow.
 */
object PoiLocationLayerManager {

    /** Layer identifiers — stored in [com.example.locationmapapp.ui.menu.MenuPrefs.PREF_POI_LOCATION_LAYER]. */
    object Id {
        const val CURRENT = "current"
        const val PROPOSED = "proposed"
        const val BOTH = "both"
    }

    /** Default layer (live coords). */
    const val DEFAULT_LAYER = Id.CURRENT

    /** All layer IDs in display order. */
    val ALL_IDS: List<String> = listOf(Id.CURRENT, Id.PROPOSED, Id.BOTH)

    /** Human-readable labels for the popup menu. */
    fun label(id: String): String = when (id) {
        Id.CURRENT -> "POIs: Current"
        Id.PROPOSED -> "POIs: Proposed"
        Id.BOTH -> "POIs: Both (compare)"
        else -> id
    }
}
