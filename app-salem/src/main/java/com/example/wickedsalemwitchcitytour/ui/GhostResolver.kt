/*
 * WickedSalemWitchCityTour v1.5 — S275 (Katrina's Collection Phase 2)
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.BuildConfig

/**
 * S275 — Loads paired ghost portraits + frame overlays for the Katrina's Collection
 * badge view. Asset paths come from `CollectionEntry.ghost_asset_a/b/ghost_frame`
 * (and `SalemPoi.ghost_asset_a/b/ghost_frame` for the canonical row).
 *
 * Conventions:
 *   - Path = full asset-relative with extension. Examples:
 *       ghosts/ghost_andrew_safford_house_a.webp
 *       frames/frame_wrought_iron_raven.webp
 *   - Only HIST_BLDG POIs (107 of 2,039) have ghost paths populated. Everything
 *     else returns null and the caller renders a category-default tile.
 *   - Decoded bitmaps are LRU-cached (8 MB). 107 portraits × ~20 KB = ~2 MB if
 *     fully filled; 8 frames × ~40 KB = ~0.3 MB. The cap is generous.
 *
 * Pair-swap rule (S275): every render of a ghost tile rolls 0-99; <2 (2%) shows
 * the B (smirk) portrait, else A. See [shouldShowAlt].
 */
object GhostResolver {

    /** ~8 MB cache — fits the full set with headroom. */
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Cached for repeated bytecode-stripped log calls. */
    private const val TAG = "GhostResolver"

    /**
     * S306 (PerfStabilityReview Tier 1) — decode ghost portraits/frames
     * downsampled to this px ceiling instead of full 384². Ghosts render as
     * map-marker badges (≤40 dp) and Collection tiles (64 dp); 192 px covers
     * the largest (64 dp at density 3.0) with no upscale, while a 384² source
     * decodes at inSampleSize=2 (→192²) = a 4× ARGB cut (576 KB → 144 KB).
     * This kills the full-res re-decode storm through the 8 MB LRU that the
     * S299/S301 redo introduced on the tilt hot path. Markers are NOT map
     * tiles, so this cannot affect 3D wedge fill.
     */
    private const val TARGET_PX = 192

    /** Probability that a single ghost-view shows the B (smirk) portrait. */
    const val ALT_PROBABILITY: Double = 0.02

    /**
     * Roll for whether THIS render should use the B (smirk) image.
     * Caller rolls per-render (per-tile-bind), not per-session.
     */
    fun shouldShowAlt(): Boolean = Math.random() < ALT_PROBABILITY

    /**
     * Load a portrait (A or B) or frame overlay from the bundled assets.
     * Returns null if the asset is missing or the path is null/blank.
     */
    fun load(context: Context, assetPath: String?): Bitmap? {
        if (assetPath.isNullOrBlank()) return null
        cache.get(assetPath)?.let { return it }
        // S306 — two-pass sampled decode (≤TARGET_PX) instead of full-res.
        val bmp = SampledAssetDecoder.decode(context, assetPath, TARGET_PX, TARGET_PX)
        if (bmp != null) {
            cache.put(assetPath, bmp)
            if (BuildConfig.DEBUG) DebugLogger.d(TAG, "loaded $assetPath -> ${bmp.width}x${bmp.height} (sampled ≤${TARGET_PX}px)")
        } else if (BuildConfig.DEBUG) {
            DebugLogger.d(TAG, "missing/undecodable asset: $assetPath")
        }
        return bmp
    }

    /**
     * Convenience: pick the right portrait (A or B) for a single render using
     * the 2% B-swap rule. Returns the A bitmap if either A or the roll says so.
     */
    fun pickPortrait(context: Context, ghostAssetA: String?, ghostAssetB: String?): Bitmap? {
        val showAlt = ghostAssetB != null && shouldShowAlt()
        val path = if (showAlt) ghostAssetB else ghostAssetA
        return load(context, path)
    }

    /** Test/debug hook — wipe the cache (e.g. on configuration change). */
    fun clearCache() {
        cache.evictAll()
    }
}
