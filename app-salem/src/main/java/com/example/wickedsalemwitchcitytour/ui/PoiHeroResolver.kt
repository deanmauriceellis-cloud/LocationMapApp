/*
 * WickedSalemWitchCityTour v1.0
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
import com.example.wickedsalemwitchcitytour.content.model.NarrationPoint
import java.io.IOException
import kotlin.math.abs

/**
 * Resolves the hero image for a POI displayed in [PoiDetailSheet] (Session 113).
 *
 * Resolution order (stopgap — merchant / admin overrides land in a future
 * session once the admin-tool image assignment chain is wired):
 *
 *   1. **Future merchant paid asset** — NOT yet implemented (placeholder).
 *   2. **Future admin-assigned asset** — NOT yet implemented (placeholder).
 *   3. **Hash-pinned fallback** — deterministic pick from
 *      `assets/poi-icons/{folder}/` based on POI type → folder mapping, keyed
 *      on `abs(poi.id.hashCode()) % pool.size`. Same POI → same image across
 *      process restarts and rebuilds as long as the asset pool is unchanged.
 *   4. **Red placeholder** — [R.drawable.poi_hero_placeholder_red] shown when
 *      the POI's category does not map to any poi-icons folder or the folder
 *      is empty. This is a visible worklist flag — operator will replace
 *      these via the admin tool.
 */
object PoiHeroResolver {

    /**
     * POI type → poi-icons folder mapping. Mirrors ProximityDock.typeToFolder
     * but is maintained independently so evolving the dock does not break
     * detail-sheet imagery.
     */
    private val typeToFolder = mapOf(
        "witch_museum" to "witch_shop",
        "witch_shop" to "witch_shop",
        "psychic" to "psychic",
        "ghost_tour" to "ghost_tour",
        "haunted_attraction" to "haunted_attraction",
        "historic_site" to "historic_house",
        "historic_house" to "historic_house",
        "museum" to "tourism_history",
        "public_art" to "tourism_history",
        "cemetery" to "tourism_history",
        "tour" to "ghost_tour",
        "attraction" to "entertainment",
        "park" to "parks_rec",
        "place_of_worship" to "worship",
        "visitor_info" to "civic",
        "lodging" to "lodging",
        "hotel" to "lodging",
        "brewery" to "food_drink",
        "bar" to "food_drink",
        "restaurant" to "food_drink",
        "cafe" to "food_drink",
        "community_center" to "civic",
        "government" to "civic",
        "library" to "education",
        "shopping" to "shopping"
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
    fun resolve(context: Context, poi: NarrationPoint): HeroResult {
        val folder = typeToFolder[poi.type] ?: return HeroResult.RedPlaceholder
        val files = listingFor(context, folder)
        if (files.isEmpty()) return HeroResult.RedPlaceholder

        val idx = abs(poi.id.hashCode()) % files.size
        return HeroResult.AssetImage("poi-icons/$folder/${files[idx]}")
    }

    /** Load the resolved hero into an ImageView. Safe on any IO error. */
    fun applyTo(context: Context, poi: NarrationPoint, imageView: ImageView): HeroResult {
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
                    ?.sorted()  // deterministic ordering across devices
                    ?: emptyList()
            } catch (_: IOException) {
                emptyList()
            }
        }
    }
}
