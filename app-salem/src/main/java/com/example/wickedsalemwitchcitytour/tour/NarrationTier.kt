/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Phase 9T+ (S112) — narration queue tiered priority + 50m discard.
 * S135: Removed ATTRACTION tier — only PAID, HISTORIC, REST remain.
 *
 * Operator-specified ordering:
 *   PAID       → adPriority > 0 — paying merchant customers, top of the list
 *   HISTORIC   → HISTORICAL_BUILDINGS, CIVIC categories
 *   REST       → everything else (entertainment, food, transit, retail, etc.)
 *
 * Within each tier, the closer POI wins. Anything farther than 50m from the
 * current user position is dropped at dequeue time before tier evaluation.
 *
 * The classifier is fed by an explicit type→tier table that covers BOTH the
 * NarrationPoint.type values stored in user_data.db (e.g. "witch_museum",
 * "ghost_tour", "historic_site") AND the OSM bare tag values returned in
 * PlaceResult.category (e.g. "museum", "courthouse", "townhall"). Some
 * values overlap; some are Salem-specific.
 *
 * If a new POI type appears that isn't in the table, it falls through to
 * REST (the lowest priority). Add it explicitly here when surfaced.
 */

package com.example.wickedsalemwitchcitytour.tour

import com.example.wickedsalemwitchcitytour.content.model.SalemPoi

/**
 * Narration priority tier for the dequeue selection logic.
 * Lower ordinal = higher priority (PAID first).
 */
enum class NarrationTier {
    PAID,        // adPriority > 0 — paying merchants override category
    HISTORIC,    // HISTORICAL_BUILDINGS, CIVIC
    REST         // everything else (entertainment, shops, food, etc.)
}

object NarrationTierClassifier {

    /**
     * SalemPoi.category (uppercase) → tier table.
     * Used by classify(SalemPoi) for the narration queue priority system.
     */
    private val categoryToTier: Map<String, NarrationTier> = mapOf(
        // ── HISTORIC tier ─────────────────────────────────────────────────
        "HISTORICAL_BUILDINGS" to NarrationTier.HISTORIC,
        "HISTORICAL_LANDMARKS" to NarrationTier.HISTORIC,
        "CIVIC"              to NarrationTier.HISTORIC,
        "EDUCATION"          to NarrationTier.HISTORIC,

    )

    /**
     * Legacy lowercase type/tag → tier table.
     * Used by classifyOsmCategory() for OSM bare tag values (PlaceResult.category).
     */
    private val typeToTier: Map<String, NarrationTier> = buildMap {

        // ── HISTORIC tier ─────────────────────────────────────────────────
        put("museum", NarrationTier.HISTORIC)
        put("witch_museum", NarrationTier.HISTORIC)
        put("monument", NarrationTier.HISTORIC)
        put("memorial", NarrationTier.HISTORIC)
        put("historic", NarrationTier.HISTORIC)
        put("historic_site", NarrationTier.HISTORIC)
        put("historic_district", NarrationTier.HISTORIC)
        put("archaeological_site", NarrationTier.HISTORIC)
        put("ruins", NarrationTier.HISTORIC)
        put("public_art", NarrationTier.HISTORIC)
        put("artwork", NarrationTier.HISTORIC)
        put("heritage", NarrationTier.HISTORIC)
        put("cemetery", NarrationTier.HISTORIC)
        put("grave_yard", NarrationTier.HISTORIC)
        put("information", NarrationTier.HISTORIC)
        put("visitor_info", NarrationTier.HISTORIC)
        put("historical_buildings", NarrationTier.HISTORIC)
        put("townhall", NarrationTier.HISTORIC)
        put("courthouse", NarrationTier.HISTORIC)
        put("post_office", NarrationTier.HISTORIC)
        put("government", NarrationTier.HISTORIC)
        put("community_centre", NarrationTier.HISTORIC)
        put("community_center", NarrationTier.HISTORIC)
        put("library", NarrationTier.HISTORIC)
        put("public_building", NarrationTier.HISTORIC)

        // All other OSM tags (entertainment, shops, etc.) fall through to REST
    }

    /**
     * Classify a SalemPoi by its (adPriority, category) pair.
     * adPriority > 0 always wins (PAID).
     */
    fun classify(poi: SalemPoi): NarrationTier {
        if (poi.adPriority > 0) return NarrationTier.PAID
        return categoryToTier[poi.category] ?: NarrationTier.REST
    }

    /**
     * Classify a generic OSM POI by its category tag value.
     * OSM places have no merchant/adPriority concept, so they can never be PAID.
     */
    fun classifyOsmCategory(category: String?): NarrationTier {
        if (category == null) return NarrationTier.REST
        return typeToTier[category] ?: NarrationTier.REST
    }

    /**
     * Returns true if the OSM category maps to the HISTORIC tier.
     */
    fun isHistoricTag(category: String?): Boolean {
        return classifyOsmCategory(category) == NarrationTier.HISTORIC
    }
}
