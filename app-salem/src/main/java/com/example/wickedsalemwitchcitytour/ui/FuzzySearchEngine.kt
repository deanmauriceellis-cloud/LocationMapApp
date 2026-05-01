/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import kotlin.math.abs
import kotlin.math.sqrt

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module FuzzySearchEngine.kt"

/**
 * Multi-tier fuzzy scorer for the Find dialog.
 *
 * Scoring tiers (descending priority):
 *   1. Exact full-name match             → 10 000 - tiny distance penalty
 *   2. Name starts with full query       → 5 000
 *   3. All tokens start a name word      → 3 000
 *   Per expanded-token (name, exclusive best level):
 *   4. Exact word in name                →   600
 *   5. Substring in name                 →   400
 *   6. Prefix of a name word             →   250
 *   7. Levenshtein ≤1/2                  →   120
 *   Per expanded-token (non-name, always accumulated):
 *   8. Subcategory / category            →    60
 *   9. Cuisine / specialties             →    55
 *  10. Description                       →    40
 *  11. Historical note                   →    30
 *  12. Address / district                →    20
 *  Distance bonus (secondary tiebreak)  →  0–300
 *
 * Token expansion: each input token is expanded with TYPE_SYNONYMS before
 * scoring, so "dentist" also scores against "dental", "gym" against "fitness",
 * etc. Expansion is applied before ALL scoring tiers.
 *
 * Results with score = 0 are dropped. Sorted descending by score.
 */
internal object FuzzySearchEngine {

    private val WORD_SPLIT = Regex("[\\s,&.'\"\\-/()]+")

    /**
     * Maps common user search terms to the strings that actually appear in
     * POI names, subcategories, and descriptions. Keys and values are lowercase.
     * Handles plural forms and stem mismatches explicitly.
     */
    private val TYPE_SYNONYMS: Map<String, List<String>> = mapOf(
        // Healthcare — stem mismatches
        "dentist"         to listOf("dental"),
        "dentists"        to listOf("dental"),
        "chiropractor"    to listOf("chiropractic"),
        "chiropractors"   to listOf("chiropractic"),
        "therapist"       to listOf("therapy", "counseling", "behavioral"),
        "therapists"      to listOf("therapy", "counseling", "behavioral"),
        "optometrist"     to listOf("optical", "vision", "eye"),
        "optometrists"    to listOf("optical", "vision", "eye"),
        "ophthalmologist" to listOf("optical", "vision", "eye"),
        "veterinarian"    to listOf("veterinary", "animal"),
        "veterinarians"   to listOf("veterinary", "animal"),
        "vet"             to listOf("veterinary", "animal"),
        "vets"            to listOf("veterinary", "animal"),
        "doctor"          to listOf("medical", "physician", "clinic", "health"),
        "doctors"         to listOf("medical", "physician", "clinic", "health"),
        // Legal / Finance
        "lawyer"          to listOf("law", "attorney", "legal"),
        "lawyers"         to listOf("law", "attorney", "legal"),
        "attorney"        to listOf("law", "legal"),
        "attorneys"       to listOf("law", "legal"),
        "accountant"      to listOf("accounting", "cpa", "tax"),
        "accountants"     to listOf("accounting", "cpa", "tax"),
        // Food / Drink
        "coffee"          to listOf("cafe"),
        "coffeehouse"     to listOf("cafe"),
        "pub"             to listOf("bar", "tavern"),
        "pubs"            to listOf("bar", "tavern"),
        "brewery"         to listOf("brew", "beer"),
        "breweries"       to listOf("brew", "beer"),
        "bakery"          to listOf("bake", "bakery", "pastry"),
        "bakeries"        to listOf("bake", "bakery", "pastry"),
        // Home Services
        "plumber"         to listOf("plumbing"),
        "plumbers"        to listOf("plumbing"),
        "electrician"     to listOf("electric", "electrical"),
        "electricians"    to listOf("electric", "electrical"),
        "handyman"        to listOf("handyman", "repair", "services"),
        // Fitness / Wellness
        "gym"             to listOf("fitness"),
        "gyms"            to listOf("fitness"),
        "workout"         to listOf("fitness", "gym"),
        "spa"             to listOf("spa", "beauty", "wellness"),
        "spas"            to listOf("spa", "beauty", "wellness"),
        // Lodging
        "hotel"           to listOf("inn", "suites", "motel"),
        "hotels"          to listOf("inn", "suites", "motel"),
        "motel"           to listOf("hotel", "inn"),
        "motels"          to listOf("hotel", "inn"),
        "bnb"             to listOf("bed", "breakfast", "inn"),
        // Entertainment / Culture
        "theater"         to listOf("theatre"),
        "theatre"         to listOf("theater"),
        "cinema"          to listOf("theater", "theatre", "movie"),
        "movie"           to listOf("theater", "theatre", "cinema"),
        // Salem-specific
        "witches"         to listOf("witch", "witchcraft"),
        "museum"          to listOf("museums"),
        "museums"         to listOf("museum"),
        "tours"           to listOf("tour"),
    )

    fun search(
        query: String,
        pois: List<SalemPoi>,
        fromLat: Double,
        fromLon: Double,
        limit: Int = 80
    ): List<ScoredPoi> {
        val rawQuery = query.trim().lowercase()
        if (rawQuery.length < 2) return emptyList()
        val tokens = tokenize(rawQuery)

        return pois.asSequence()
            .map { poi -> ScoredPoi(poi, score(rawQuery, tokens, poi, fromLat, fromLon)) }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    private fun score(
        rawQuery: String,
        tokens: List<String>,
        poi: SalemPoi,
        lat: Double,
        lon: Double
    ): Float {
        val nameLower = poi.name.lowercase()
        val nameWords = nameLower.split(WORD_SPLIT).filter { it.isNotEmpty() }
        val descLower = ((poi.description ?: "") + " " + (poi.shortDescription ?: "")).lowercase()
        val subcatLower = (poi.subcategory ?: "").lowercase()
        val catLower = poi.category.lowercase()
        val addrLower = (poi.address ?: "").lowercase()
        val districtLower = (poi.district ?: "").lowercase()
        val specialtiesLower = (poi.specialties ?: "").lowercase()
        val cuisineLower = (poi.cuisineType ?: "").lowercase()

        var s = 0f

        // Tier 1 — exact full name (short-circuit)
        if (nameLower == rawQuery) return 10_000f - distanceM(lat, lon, poi.lat, poi.lng) * 0.001f

        // Tier 2 — name starts with full query string
        if (nameLower.startsWith(rawQuery)) s += 5_000f

        // Tier 3 — all original tokens each begin a word in the name
        if (tokens.size >= 2 && tokens.all { t -> nameWords.any { w -> w.startsWith(t) } }) s += 3_000f

        // Expand each original token with type synonyms; deduplicate
        val allTokens = tokens
            .flatMap { t -> listOf(t) + (TYPE_SYNONYMS[t] ?: emptyList()) }
            .distinct()

        // Per-token scoring
        for (token in allTokens) {
            // Name: exclusive tiers — only the best name-match level fires per token
            s += when {
                nameWords.contains(token)               -> 600f
                nameLower.contains(token)               -> 400f
                nameWords.any { it.startsWith(token) }  -> 250f
                nameWords.any { fuzzyMatch(it, token) } -> 120f
                else                                    -> 0f
            }

            // Non-name fields: always accumulated independently of name score
            if (subcatLower.contains(token) || catLower.contains(token)) s += 60f
            if (cuisineLower.contains(token) || specialtiesLower.contains(token)) s += 55f
            if (descLower.contains(token)) s += 40f
            if (addrLower.contains(token) || districtLower.contains(token)) s += 20f
        }

        if (s <= 0f) return 0f

        // Distance bonus — secondary tiebreaker, closer ranks higher
        val distM = distanceM(lat, lon, poi.lat, poi.lng)
        s += maxOf(0f, 300f - distM * 0.03f)

        return s
    }

    // 1 edit for words ≥4 chars; 2 edits for words ≥7 chars; 0 for short words
    private fun fuzzyMatch(word: String, token: String): Boolean {
        if (abs(word.length - token.length) > 2) return false
        val minLen = minOf(word.length, token.length)
        val tolerance = when {
            minLen >= 7 -> 2
            minLen >= 4 -> 1
            else -> 0
        }
        return tolerance > 0 && levenshtein(word, token) <= tolerance
    }

    // Space-efficient O(n) Levenshtein
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length; val n = b.length
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1]
                           else minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + 1)
            }
            prev.indices.forEach { k -> prev[k] = curr[k] }
        }
        return curr[n]
    }

    private fun tokenize(query: String): List<String> =
        query.split(WORD_SPLIT)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(8)

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLat = Math.sin(dLat / 2)
        val sinDLon = Math.sin(dLon / 2)
        val a = sinDLat * sinDLat +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                sinDLon * sinDLon
        return (R * 2 * Math.atan2(sqrt(a), sqrt(1 - a))).toInt()
    }
}

internal data class ScoredPoi(val poi: SalemPoi, val score: Float)
