/*
 * WickedSalemWitchCityTour v1.5 — Phase 9U (Session 120)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import java.io.IOException
import kotlin.math.abs

/**
 * Resolves the hero image for a POI displayed in [PoiDetailSheet].
 *
 * Resolution order:
 *   1. **poi.imageAsset** — S119 hero image in `assets/hero/{entity_id}.webp`
 *      (1,013 POIs have this populated as of S119).
 *   2. **Category-based fallback** — hash-pinned pick from
 *      `assets/poi-icons/{category.lowercase()}/` based on POI category → folder
 *      mapping, keyed on `abs(poi.id.hashCode()) % pool.size`.
 *   3. **Red placeholder** — [R.drawable.poi_hero_placeholder_red] shown when
 *      no hero image and no category icons exist.
 */
object PoiHeroResolver {

    /**
     * SalemPoi.category (uppercase) → poi-icons folder mapping.
     * Used when the POI has no imageAsset (hero image).
     */
    private val categoryToFolder = mapOf(
        "WITCH_SHOP"          to "witch_shop",
        "PSYCHIC"             to "psychic",
        "GHOST_TOUR"          to "ghost_tour",
        "HAUNTED_ATTRACTION"  to "haunted_attraction",
        "TOURISM_HISTORY"     to "tourism_history",
        "FOOD_DRINK"          to "food_drink",
        "LODGING"             to "lodging",
        "ENTERTAINMENT"       to "entertainment",
        "PARKS_REC"           to "parks_rec",
        "CIVIC"               to "civic",
        "EDUCATION"           to "education",
        "SHOPPING"            to "shopping",
        "WORSHIP"             to "worship",
        "HEALTHCARE"          to "healthcare",
        "OFFICES"             to "offices",
    )

    /** Cached file listings per folder (filled lazily on first access). */
    private val folderListingCache = mutableMapOf<String, List<String>>()

    sealed class HeroResult {
        /** Path relative to `assets/` — ready for `context.assets.open()`. */
        data class AssetImage(val assetPath: String) : HeroResult()
        /** Category has no curated imagery; show the red "ASSIGN HERO" flag. */
        data object RedPlaceholder : HeroResult()
    }

    /** Resolve the hero for this POI. Never throws. */
    fun resolve(context: Context, poi: SalemPoi): HeroResult {
        // Priority 1: dedicated hero image from S119 generation pipeline
        poi.imageAsset?.takeIf { it.isNotBlank() }?.let { asset ->
            return HeroResult.AssetImage(asset)
        }

        // Priority 2: category-based fallback from poi-icons
        val folder = categoryToFolder[poi.category]
            ?: return HeroResult.RedPlaceholder
        val files = listingFor(context, folder)
        if (files.isEmpty()) return HeroResult.RedPlaceholder

        val idx = abs(poi.id.hashCode()) % files.size
        return HeroResult.AssetImage("poi-icons/$folder/${files[idx]}")
    }

    /** Load the resolved hero into an ImageView. Safe on any IO error. */
    fun applyTo(context: Context, poi: SalemPoi, imageView: ImageView): HeroResult {
        val result = resolve(context, poi)
        when (result) {
            is HeroResult.AssetImage -> {
                try {
                    context.assets.open(result.assetPath).use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        imageView.setImageBitmap(bitmap)
                    }
                } catch (_: IOException) {
                    imageView.setImageResource(R.drawable.poi_hero_placeholder_red)
                    return HeroResult.RedPlaceholder
                }
            }
            HeroResult.RedPlaceholder -> {
                imageView.setImageResource(R.drawable.poi_hero_placeholder_red)
            }
        }
        return result
    }

    private fun listingFor(context: Context, folder: String): List<String> {
        return folderListingCache.getOrPut(folder) {
            try {
                context.assets.list("poi-icons/$folder")
                    ?.filter { it.endsWith(".png", ignoreCase = true) }
                    ?.sorted()
                    ?: emptyList()
            } catch (_: IOException) {
                emptyList()
            }
        }
    }
}
