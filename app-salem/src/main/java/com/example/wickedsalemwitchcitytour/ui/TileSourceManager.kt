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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TileSourceManager.kt"

/**
 * TileSourceManager — Centralized tile source management for WickedSalemWitchCityTour.
 *
 * Three tile sources:
 * - SATELLITE: USGS National Map Imagery (free, public domain, 15cm resolution over Salem)
 * - STREET: OpenStreetMap Mapnik (standard street map)
 * - DARK: CartoDB Dark Matter (dark mode)
 */
object TileSourceManager {

    private const val TAG = "TileSourceManager"

    /** Tile source identifiers — stored in preferences. */
    object Id {
        const val SATELLITE = "SATELLITE"
        const val STREET = "STREET"
        const val DARK = "DARK"
    }

    /** Default tile source for the app. */
    const val DEFAULT_SOURCE = Id.SATELLITE

    /** All available tile source IDs in display order. */
    val ALL_IDS = listOf(Id.SATELLITE, Id.STREET, Id.DARK)

    /** Human-readable labels for PopupMenu display. */
    fun label(id: String): String = when (id) {
        Id.SATELLITE -> "Satellite"
        Id.STREET -> "Street"
        Id.DARK -> "Dark"
        else -> id
    }

    /** Build an osmdroid ITileSource for the given ID. */
    fun buildSource(id: String): ITileSource {
        DebugLogger.i(TAG, "buildSource($id)")
        return when (id) {
            Id.SATELLITE -> buildUsgsSource()
            Id.STREET -> TileSourceFactory.MAPNIK
            Id.DARK -> buildDarkSource()
            else -> {
                DebugLogger.w(TAG, "Unknown tile source ID '$id', falling back to SATELLITE")
                buildUsgsSource()
            }
        }
    }

    /**
     * Esri World Imagery — high-resolution satellite imagery.
     * Zoom 0-19 (20 in some metro areas). No API key required.
     * Attribution: "Sources: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
     *
     * ArcGIS tile servers use z/y/x ordering (not osmdroid's default z/x/y),
     * so we override getTileURLString to swap x and y.
     */
    /** Max zoom for satellite imagery. */
    const val USGS_MAX_ZOOM = 19

    private fun buildUsgsSource(): OnlineTileSourceBase {
        return object : OnlineTileSourceBase(
            "Esri-WorldImagery",
            0, USGS_MAX_ZOOM, 256, "",
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                // ArcGIS uses z/y/x (row/column), not z/x/y
                return "${baseUrl}$z/$y/$x"
            }
        }
    }

    /** CartoDB Dark Matter — dark mode tiles. */
    private fun buildDarkSource(): XYTileSource {
        return XYTileSource(
            "CartoDB-DarkMatter",
            0, 19, 256, ".png",
            arrayOf(
                "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/",
                "https://cartodb-basemaps-b.global.ssl.fastly.net/dark_all/",
                "https://cartodb-basemaps-c.global.ssl.fastly.net/dark_all/",
                "https://cartodb-basemaps-d.global.ssl.fastly.net/dark_all/"
            )
        )
    }
}
