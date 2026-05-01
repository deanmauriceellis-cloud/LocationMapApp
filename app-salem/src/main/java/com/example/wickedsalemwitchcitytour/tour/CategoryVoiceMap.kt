/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

/**
 * Maps POI categories to default TTS voice IDs.
 *
 * Voice assignment strategy:
 * - Salem spooky categories → UK voices (old-world gothic atmosphere)
 * - Historic/cultural → US narrator voices (authoritative, clear)
 * - Food & drink → warm, inviting voices
 * - Services/commercial → clear, professional voices
 * - Parks/outdoor → AU voices (fresh, distinctive)
 *
 * Merchants with paid tiers can override via:
 * - narration_points.voice_override (different TTS voice ID)
 * - narration_points.audio_asset (custom pre-recorded audio file, highest priority)
 */
object CategoryVoiceMap {

    /** Voice ID constants matching Google TTS engine names */
    const val US_1 = "en-us-x-iob-local"  // Female (warm)
    const val US_2 = "en-us-x-iog-local"  // Male (deep)
    const val US_3 = "en-us-x-iol-local"  // Female (bright)
    const val US_4 = "en-us-x-iom-local"  // Male (clear)
    const val US_5 = "en-us-x-sfg-local"  // Female (smooth)
    const val US_6 = "en-us-x-tpc-local"  // Female (narrator)
    const val US_7 = "en-us-x-tpd-local"  // Male (narrator)
    const val US_8 = "en-us-x-tpf-local"  // Female (crisp)
    const val UK_1 = "en-gb-x-gba-local"  // Female
    const val UK_2 = "en-gb-x-gbb-local"  // Male
    const val UK_3 = "en-gb-x-gbc-local"  // Female (soft)
    const val UK_4 = "en-gb-x-gbd-local"  // Male (formal)
    const val UK_5 = "en-gb-x-gbg-local"  // Female (bright)
    const val UK_6 = "en-gb-x-rjs-local"  // Male (warm)
    const val AU_1 = "en-au-x-aub-local"  // Female
    const val AU_2 = "en-au-x-aud-local"  // Male

    /**
     * Category → default voice mapping.
     *
     * Covers both SalemPoi.category values (uppercase, e.g. "WITCH_SHOP") and
     * legacy NarrationPoint.type / OSM tag values (lowercase, e.g. "witch_shop").
     * Categories without an explicit mapping fall back to [defaultVoice].
     */
    private val categoryVoice: Map<String, String> = mapOf(
        // ── SalemPoi.category (uppercase — Phase 9U unified table) ──
        "WITCH_SHOP"          to UK_1,  // Female — mysterious shopkeeper
        "TOUR_COMPANIES"      to UK_2,  // Male — dramatic storyteller
        "PSYCHIC"             to UK_3,  // Female soft — ethereal, mystical
        "HISTORICAL_BUILDINGS" to US_7, // Male narrator — documentary voice
        "HISTORICAL_LANDMARKS" to US_7, // S216 split — reuse buildings voice
        "FOOD_DRINK"          to US_1,  // Female warm — welcoming
        "LODGING"             to US_8,  // Female crisp — professional hospitality
        "SHOPPING"            to US_3,  // Female bright — retail energy
        "ENTERTAINMENT"       to US_6,  // Female narrator — engaging guide
        "PARKS_REC"           to AU_1,  // AU Female — fresh, outdoorsy
        "CIVIC"               to US_4,  // Male clear — civic authority
        "EDUCATION"           to UK_3,  // UK Female soft — quiet, bookish
        "WORSHIP"             to UK_5,  // UK Female bright — reverent but warm
        "HEALTHCARE"          to US_4,  // Male clear — clinical trust
        "OFFICES"             to US_4,  // Male clear — business professional
        "FINANCE"             to US_4,  // Male clear — professional
        "AUTO_SERVICES"       to US_4,  // Male clear — professional

        // ── Legacy lowercase types / OSM tags (backward compatibility) ──
        "witch_shop"          to UK_1,
        "witch_museum"        to UK_4,
        "tour_companies"      to UK_2,
        "psychic"             to UK_3,
        "cemetery"            to UK_6,
        "historic_site"       to US_7,
        "museum"              to US_5,
        "public_art"          to US_5,
        "place_of_worship"    to UK_5,
        "restaurant"          to US_1,
        "cafe"                to US_1,
        "bar"                 to AU_2,
        "brewery"             to AU_2,
        "hotel"               to US_8,
        "lodging"             to US_8,
        "shop"                to US_3,
        "services"            to US_4,
        "medical"             to US_4,
        "government"          to US_4,
        "tour"                to US_6,
        "attraction"          to US_6,
        "visitor_info"        to US_7,
        "park"                to AU_1,
        "community_center"    to UK_6,
        "venue"               to US_2,
        "public"              to US_3,
        "library"             to UK_3,
        "other"               to US_7,
    )

    /** Default voice when category is unknown */
    const val defaultVoice = US_7  // Male narrator

    /** Get the voice ID for a POI category */
    fun voiceForCategory(category: String): String {
        return categoryVoice[category] ?: defaultVoice
    }

    /** Get a human-readable label for a voice ID */
    fun voiceLabel(voiceId: String): String = when (voiceId) {
        US_1 -> "US 1 — Female (warm)"
        US_2 -> "US 2 — Male (deep)"
        US_3 -> "US 3 — Female (bright)"
        US_4 -> "US 4 — Male (clear)"
        US_5 -> "US 5 — Female (smooth)"
        US_6 -> "US 6 — Female (narrator)"
        US_7 -> "US 7 — Male (narrator)"
        US_8 -> "US 8 — Female (crisp)"
        UK_1 -> "UK 1 — Female"
        UK_2 -> "UK 2 — Male"
        UK_3 -> "UK 3 — Female (soft)"
        UK_4 -> "UK 4 — Male (formal)"
        UK_5 -> "UK 5 — Female (bright)"
        UK_6 -> "UK 6 — Male (warm)"
        AU_1 -> "AU 1 — Female"
        AU_2 -> "AU 2 — Male"
        else -> voiceId
    }

    /** All voice IDs used in the mapping (for preloading) */
    val allUsedVoices: Set<String> = categoryVoice.values.toSet()
}
