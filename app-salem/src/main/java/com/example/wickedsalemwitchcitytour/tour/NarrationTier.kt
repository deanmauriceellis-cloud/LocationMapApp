/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Phase 9T+ (S112) — narration queue tiered priority + 50m discard.
 *
 * Operator-specified ordering (S112 narration redesign):
 *   PAID       → adPriority > 0 — paying merchant customers, top of the list
 *   HISTORIC   → TOURISM_HISTORY, HISTORIC_HOUSE, CIVIC categories
 *   ATTRACTION → GHOST_TOUR, HAUNTED_ATTRACTION, WITCH_SHOP, PSYCHIC, ENTERTAINMENT
 *   REST       → everything else (food, transit, retail, lodging, etc.)
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
    HISTORIC,    // TOURISM_HISTORY, HISTORIC_HOUSE, CIVIC
    ATTRACTION,  // GHOST_TOUR, HAUNTED_ATTRACTION, WITCH_SHOP, PSYCHIC, ENTERTAINMENT
    REST         // everything else
}

object NarrationTierClassifier {

    /**
     * SalemPoi.category (uppercase) → tier table.
     * Used by classify(SalemPoi) for the narration queue priority system.
     */
    private val categoryToTier: Map<String, NarrationTier> = mapOf(
        // ── HISTORIC tier ─────────────────────────────────────────────────
        "TOURISM_HISTORY"    to NarrationTier.HISTORIC,
        "CIVIC"              to NarrationTier.HISTORIC,
        "EDUCATION"          to NarrationTier.HISTORIC,

        // ── ATTRACTION tier ───────────────────────────────────────────────
        "GHOST_TOUR"          to NarrationTier.ATTRACTION,
        "HAUNTED_ATTRACTION"  to NarrationTier.ATTRACTION,
        "WITCH_SHOP"          to NarrationTier.ATTRACTION,
        "PSYCHIC"             to NarrationTier.ATTRACTION,
        "ENTERTAINMENT"       to NarrationTier.ATTRACTION,
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
        put("historic_house", NarrationTier.HISTORIC)
        put("townhall", NarrationTier.HISTORIC)
        put("courthouse", NarrationTier.HISTORIC)
        put("post_office", NarrationTier.HISTORIC)
        put("government", NarrationTier.HISTORIC)
        put("community_centre", NarrationTier.HISTORIC)
        put("community_center", NarrationTier.HISTORIC)
        put("library", NarrationTier.HISTORIC)
        put("public_building", NarrationTier.HISTORIC)

        // ── ATTRACTION tier ───────────────────────────────────────────────
        put("ghost_tour", NarrationTier.ATTRACTION)
        put("tour", NarrationTier.ATTRACTION)
        put("walking_tour", NarrationTier.ATTRACTION)
        put("haunted_attraction", NarrationTier.ATTRACTION)
        put("haunted_house", NarrationTier.ATTRACTION)
        put("witch_shop", NarrationTier.ATTRACTION)
        put("witchcraft", NarrationTier.ATTRACTION)
        put("occult", NarrationTier.ATTRACTION)
        put("psychic", NarrationTier.ATTRACTION)
        put("tarot", NarrationTier.ATTRACTION)
        put("fortune_teller", NarrationTier.ATTRACTION)
        put("attraction", NarrationTier.ATTRACTION)
        put("theme_park", NarrationTier.ATTRACTION)
        put("theatre", NarrationTier.ATTRACTION)
        put("cinema", NarrationTier.ATTRACTION)
        put("arts_centre", NarrationTier.ATTRACTION)
        put("gallery", NarrationTier.ATTRACTION)
        put("nightclub", NarrationTier.ATTRACTION)
        put("comedy_club", NarrationTier.ATTRACTION)
        put("zoo", NarrationTier.ATTRACTION)
        put("aquarium", NarrationTier.ATTRACTION)
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
     * Returns true if the OSM category is HISTORIC or ATTRACTION (the two
     * tiers visible in the POI button OFF state, alongside any future
     * paid customers — which OSM places cannot be).
     */
    fun isHistoricOrAttractionTag(category: String?): Boolean {
        val tier = classifyOsmCategory(category)
        return tier == NarrationTier.HISTORIC || tier == NarrationTier.ATTRACTION
    }
}
