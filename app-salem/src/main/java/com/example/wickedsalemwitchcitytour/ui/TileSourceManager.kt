/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TileSourceManager.kt"

/**
 * TileSourceManager — single-basemap tile source for WickedSalemWitchCityTour.
 *
 * S167 (2026-04-24): the app now ships with one basemap only — the custom
 * "Witchy" vector-rendered tile set baked at `tools/tile-bake/`. The prior
 * Esri-WorldImagery (satellite) and Mapnik (street) and CartoDB-Dark online
 * sources were removed to shrink the bundled tile archive. All tiles resolve
 * through osmdroid's `MapTileFileArchiveProvider` reading
 * `salem_tiles.sqlite` from the app's external files dir.
 */
object TileSourceManager {

    private const val TAG = "TileSourceManager"

    /** Tile source identifiers — stored in preferences. */
    object Id {
        const val CUSTOM = "CUSTOM"
    }

    /** Default (and only) tile source. */
    const val DEFAULT_SOURCE = Id.CUSTOM

    /** Display order for the basemap picker. Single entry — picker auto-hides. */
    val ALL_IDS: List<String> = listOf(Id.CUSTOM)

    /** Human-readable label for PopupMenu display. */
    fun label(id: String): String = when (id) {
        Id.CUSTOM -> "Witchy"
        else -> id
    }

    /**
     * Build an osmdroid ITileSource. Always returns the Salem-Custom offline
     * archive source — legacy preference values (SATELLITE/STREET/DARK from
     * pre-S167 installs) are silently coerced so returning users don't see
     * a blank map after the upgrade.
     */
    fun buildSource(id: String): ITileSource {
        DebugLogger.i(TAG, "buildSource($id) → Salem-Custom")
        return buildOfflineArchiveSource("Salem-Custom", 16, 19)
    }

    /**
     * Offline-only tile source: returns an empty URL so osmdroid's network
     * downloader refuses to fetch anything. Tiles resolve through the file
     * archive provider (reading `salem_tiles.sqlite`) only. The source name
     * must match the archive's `provider` column.
     */
    private fun buildOfflineArchiveSource(
        providerName: String,
        minZoom: Int,
        maxZoom: Int
    ): OnlineTileSourceBase {
        return object : OnlineTileSourceBase(
            providerName,
            minZoom, maxZoom, 256, "",
            arrayOf("")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String = ""
        }
    }
}
