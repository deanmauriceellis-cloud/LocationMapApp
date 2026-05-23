/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.BuildConfig
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module TileSourceManager.kt"

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
        // S289: was (16, 19) — caused basemap to silently vanish at z14/z15 even though
        // those tiles exist in salem_tiles.sqlite. Widened to z11-z19 so panning out
        // doesn't drop the basemap. NOTE: DB currently only has z14+ tiles; z11-z13
        // are blank until baked. Anything outside the DB returns null and falls
        // through to osmdroid's grid background, which is the existing fallback.
        DebugLogger.i(TAG, "buildSource($id) → Salem-Custom (z11-z19)")
        return buildOfflineArchiveSource("Salem-Custom", 11, 19)
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
            // S289 verbose: log every basemap tile request osmdroid asks for. Debug-only
            // (R8 strips). Lets us see z/x/y for each requested tile when diagnosing
            // "basemap is gone at this zoom" issues. Volume is bounded by visible tiles
            // (~20-30 per frame); osmdroid coalesces repeats via its own tile cache.
            private val seenKeys = java.util.concurrent.ConcurrentHashMap<Long, Int>()
            override fun getTileURLString(pMapTileIndex: Long): String {
                if (BuildConfig.DEBUG) {
                    val key = pMapTileIndex
                    val n = (seenKeys[key] ?: 0) + 1
                    seenKeys[key] = n
                    // Log only first occurrence per tile key per session to avoid flood.
                    if (n == 1) {
                        val z = MapTileIndex.getZoom(key)
                        val x = MapTileIndex.getX(key)
                        val y = MapTileIndex.getY(key)
                        DebugLogger.d("BasemapTile", "REQ $providerName z=$z x=$x y=$y (uniqueSoFar=${seenKeys.size})")
                    }
                }
                return ""
            }
        }
    }
}
